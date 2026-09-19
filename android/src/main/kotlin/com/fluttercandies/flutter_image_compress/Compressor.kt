package com.fluttercandies.flutter_image_compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.heifwriter.AvifWriter
import androidx.heifwriter.HeifWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/// Carries a wire error code (mirrors the iOS FlutterError codes) so the plugin can
/// surface decode/read/write failures to Dart as a PlatformException instead of null.
internal class CompressException(val code: String, message: String?) : Exception(message)

/// Order must match the Dart `CompressFormat` enum so `index` is the wire value.
///
/// `bitmapFormat` is null for HEIC and AVIF, which go through `androidx.heifwriter`
/// (`HeifWriter`/`AvifWriter`) instead of `Bitmap.compress()` — the platform
/// `Bitmap.CompressFormat` enum has no HEIC/AVIF entry at any API level.
///
/// `minExifApi` is the minimum `Build.VERSION.SDK_INT` at which framework
/// `ExifInterface.saveAttributes()` can write this format. `1` = supported on
/// every API level we ship on; `null` = never supported (HEIF-based containers).
enum class CompressFormat(val bitmapFormat: Bitmap.CompressFormat?, val minExifApi: Int?) {
    JPEG(Bitmap.CompressFormat.JPEG, 1),
    PNG(Bitmap.CompressFormat.PNG, Build.VERSION_CODES.R),
    HEIC(null, null),
    WEBP(Bitmap.CompressFormat.WEBP, Build.VERSION_CODES.S),
    AVIF(null, null);

    val canWriteExif: Boolean
        get() = minExifApi != null && Build.VERSION.SDK_INT >= minExifApi

    val unsupportedExifMessage: String
        get() = if (minExifApi == null) {
            "keepExif=true ignored for $name output: no ExifInterface writer supports this format"
        } else {
            "keepExif=true ignored for $name on API ${Build.VERSION.SDK_INT}: framework ExifInterface writer requires API $minExifApi+"
        }

    companion object {
        fun fromIndex(index: Int): CompressFormat? = entries.getOrNull(index)
    }
}

internal object Compressor {

    // How long HeifWriter/AvifWriter.stop() waits for the encoder to flush before giving up.
    // Documented as a per-encode ceiling, not a per-frame one — 5 s comfortably covers even
    // large HEIC/AVIF frames on slow devices; too short means legitimate encodes throw.
    private const val WRITER_STOP_TIMEOUT_MS = 5000L

    fun encodeBytes(
        context: Context,
        format: CompressFormat,
        bytes: ByteArray,
        output: OutputStream,
        minWidth: Int,
        minHeight: Int,
        quality: Int,
        rotate: Int,
        flipHorizontal: Boolean,
        keepExif: Boolean,
    ) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val plan = planDecode(bounds.outWidth, bounds.outHeight, minWidth, minHeight)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions(plan.sampleSize))
            ?: throw CompressException("BAD_IMAGE", "could not decode image bytes")
        val (destW, destH) = plan.target ?: targetSize(bitmap.width, bitmap.height, minWidth, minHeight)
        val encoded = compress(context, bitmap, format, destW, destH, quality, rotate, flipHorizontal)
        writeOutput(output, encoded, context, format, keepExif) { ExifKeeper(bytes) }
    }

    fun encodeFile(
        context: Context,
        format: CompressFormat,
        path: String,
        output: OutputStream,
        minWidth: Int,
        minHeight: Int,
        quality: Int,
        rotate: Int,
        flipHorizontal: Boolean,
        keepExif: Boolean,
    ) {
        if (!File(path).exists()) throw CompressException("FILE_NOT_FOUND", "could not read $path")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val plan = planDecode(bounds.outWidth, bounds.outHeight, minWidth, minHeight)
        val bitmap = BitmapFactory.decodeFile(path, decodeOptions(plan.sampleSize))
            ?: throw CompressException("BAD_IMAGE", "could not decode image at $path")
        val (destW, destH) = plan.target ?: targetSize(bitmap.width, bitmap.height, minWidth, minHeight)
        val encoded = compress(context, bitmap, format, destW, destH, quality, rotate, flipHorizontal)
        writeOutput(output, encoded, context, format, keepExif) { ExifKeeper(path) }
    }

    // [destW]/[destH] are already the final output size — normally computed by [targetSize] from
    // the *original*, pre-sampling image bounds (see [planDecode]/encodeFile/encodeBytes), or from
    // `bitmap`'s own dimensions when the bounds-only decode couldn't read them. We deliberately do
    // not recompute the scale from `bitmap` in the common case: BitmapFactory's inSampleSize decode
    // only guarantees the result is *approximately* width/sampleSize (codecs round to whatever
    // block size they decode natively), so re-deriving destW/destH from the sampled bitmap could
    // drift by a pixel or two from what this same source would have produced before the sampled
    // decode was introduced. Scaling straight to the pre-computed target keeps output dimensions
    // identical to the un-sampled path.
    private fun compress(
        context: Context,
        bitmap: Bitmap,
        format: CompressFormat,
        destW: Int,
        destH: Int,
        quality: Int,
        rotate: Int,
        flipHorizontal: Boolean,
    ): ByteArray {
        log("decoded width = ${bitmap.width}")
        log("decoded height = ${bitmap.height}")
        log("dst width = $destW")
        log("dst height = $destH")
        val scaled = Bitmap.createScaledBitmap(bitmap, destW, destH, true)
        if (scaled !== bitmap) bitmap.recycle()
        val transformed = scaled.rotate(rotate, flipHorizontal)
        if (transformed !== scaled) scaled.recycle()
        return try {
            when (format) {
                CompressFormat.HEIC -> encodeHeic(context, transformed, quality)
                CompressFormat.AVIF -> encodeAvif(context, transformed, quality)
                else -> ByteArrayOutputStream().also {
                    transformed.compress(format.bitmapFormat!!, quality, it)
                }.toByteArray()
            }
        } finally {
            transformed.recycle()
        }
    }

    // HeifWriter and AvifWriter both write to a file path (they wrap MediaMuxer) and implement
    // Closeable. Their public APIs are shape-identical but share no supertype, so we can't factor
    // beyond this: each encoder supplies its Builder; the tmp-file lifecycle and .use { } handle
    // the rest.
    private fun encodeHeic(context: Context, bitmap: Bitmap, quality: Int): ByteArray =
        encodeToTempFile(context, ".heic") { path ->
            HeifWriter.Builder(path, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
                .setQuality(quality).setMaxImages(1).build()
                .use { it.start(); it.addBitmap(bitmap); it.stop(WRITER_STOP_TIMEOUT_MS) }
        }

    private fun encodeAvif(context: Context, bitmap: Bitmap, quality: Int): ByteArray =
        encodeToTempFile(context, ".avif") { path ->
            AvifWriter.Builder(path, bitmap.width, bitmap.height, AvifWriter.INPUT_MODE_BITMAP)
                .setQuality(quality).setMaxImages(1).build()
                .use { it.start(); it.addBitmap(bitmap); it.stop(WRITER_STOP_TIMEOUT_MS) }
        }

    private inline fun encodeToTempFile(
        context: Context,
        suffix: String,
        write: (path: String) -> Unit,
    ): ByteArray {
        val tmp = File.createTempFile("img_", suffix, context.cacheDir)
        try {
            write(tmp.absolutePath)
            return tmp.readBytes()
        } finally {
            tmp.delete()
        }
    }

    private fun writeOutput(
        output: OutputStream,
        encoded: ByteArray,
        context: Context,
        format: CompressFormat,
        keepExif: Boolean,
        exifKeeper: () -> ExifKeeper,
    ) {
        if (keepExif && format.canWriteExif) {
            val tmp = ByteArrayOutputStream()
            tmp.write(encoded)
            output.write(exifKeeper().writeToOutputStream(context, tmp).toByteArray())
        } else {
            if (keepExif) Log.w(LOG_TAG, format.unsupportedExifMessage)
            output.write(encoded)
        }
    }

    // BitmapFactory decodes a wide-gamut source (Display P3, Adobe RGB) into that same color space, and neither
    // Bitmap.compress() nor the HeifWriter/AvifWriter path tags the output with a matching ICC profile reliably. A
    // non-color-managed viewer then reads those pixel values as sRGB and shows them oversaturated. Color-manage the
    // decode down to sRGB instead, so the output is self-describing whatever the encoder does. inPreferredColorSpace
    // is API 26+; below that the platform has no color management to begin with.
    //
    // [sampleSize] skips decoding pixel data this call would immediately throw away in `compress()`'s
    // createScaledBitmap step. A full-resolution photo (e.g. 12 MP) decoded straight to ARGB_8888 is ~48 MB before
    // any resize; calcInSampleSize keeps the sampled decode at least as large as the final output size.
    private fun decodeOptions(sampleSize: Int) = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        if (Build.VERSION.SDK_INT >= 26) inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        inSampleSize = sampleSize
    }

    // Same formula `Bitmap.calcScale` used before this file started decoding at a downsampled size — kept as a
    // free function so it can run against the *bounds-only* decode (BitmapFactory.Options.outWidth/outHeight)
    // before any pixel data is read, instead of against a fully decoded Bitmap.
    private fun calcScale(width: Int, height: Int, minWidth: Int, minHeight: Int): Float {
        val scaleW = width.toFloat() / minWidth.toFloat()
        val scaleH = height.toFloat() / minHeight.toFloat()
        log("width scale = $scaleW")
        log("height scale = $scaleH")
        return max(1f, min(scaleW, scaleH))
    }

    // The output size `compress()` will scale to, derived from the *original* (pre-sampling) image
    // dimensions so it is identical regardless of what inSampleSize calcInSampleSize later picks.
    private fun targetSize(width: Int, height: Int, minWidth: Int, minHeight: Int): Pair<Int, Int> {
        val scale = calcScale(width, height, minWidth, minHeight)
        log("scale = $scale")
        return (width / scale).toInt() to (height / scale).toInt()
    }

    // Largest power-of-two BitmapFactory.Options.inSampleSize that still decodes a bitmap at least as
    // large as [targetWidth]x[targetHeight] on both axes — i.e. the smallest decode that loses no detail
    // the final createScaledBitmap call in `compress()` wouldn't have discarded anyway. BitmapFactory only
    // honors powers of two (any other value is rounded down), hence the doubling loop instead of a division.
    // A zero/negative target (e.g. minWidth=minHeight=0) would otherwise leave the loop condition true
    // forever — origWidth/(sampleSize*2) can't drop below a target of 0 — until sampleSize overflows Int
    // and the next iteration divides by zero.
    private fun calcInSampleSize(origWidth: Int, origHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        if (origWidth <= 0 || origHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
        var sampleSize = 1
        while (origWidth / (sampleSize * 2) >= targetWidth && origHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

    // Bounds-only decodes (see encodeFile/encodeBytes) can fail to report dimensions —
    // BitmapFactory.Options.outWidth/outHeight come back <= 0 — for some malformed or unusual
    // sources even though the real decode right after still succeeds. There's nothing to compute a
    // sample size or target from in that case, so [target] is null and the caller falls back to
    // decoding at full resolution ([sampleSize] = 1) and deriving the target from the decoded
    // bitmap's own dimensions instead, matching this file's behavior before it started sampling.
    private class DecodePlan(val sampleSize: Int, val target: Pair<Int, Int>?)

    private fun planDecode(boundsWidth: Int, boundsHeight: Int, minWidth: Int, minHeight: Int): DecodePlan {
        if (boundsWidth <= 0 || boundsHeight <= 0) return DecodePlan(1, null)
        val target = targetSize(boundsWidth, boundsHeight, minWidth, minHeight)
        return DecodePlan(calcInSampleSize(boundsWidth, boundsHeight, target.first, target.second), target)
    }
}

private fun Bitmap.rotate(degrees: Int, flipHorizontal: Boolean): Bitmap {
    if (degrees % 360 == 0 && !flipHorizontal) return this
    val matrix = Matrix().apply {
        if (degrees % 360 != 0) postRotate(degrees.toFloat())
        if (flipHorizontal) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, false)
}
