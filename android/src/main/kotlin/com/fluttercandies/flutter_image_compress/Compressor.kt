package com.fluttercandies.flutter_image_compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions())
            ?: throw CompressException("BAD_IMAGE", "could not decode image bytes")
        val encoded = compress(context, bitmap, format, minWidth, minHeight, quality, rotate, flipHorizontal)
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
        val bitmap = BitmapFactory.decodeFile(path, decodeOptions())
            ?: throw CompressException("BAD_IMAGE", "could not decode image at $path")
        val encoded = compress(context, bitmap, format, minWidth, minHeight, quality, rotate, flipHorizontal)
        writeOutput(output, encoded, context, format, keepExif) { ExifKeeper(path) }
    }

    private fun compress(
        context: Context,
        bitmap: Bitmap,
        format: CompressFormat,
        minWidth: Int,
        minHeight: Int,
        quality: Int,
        rotate: Int,
        flipHorizontal: Boolean,
    ): ByteArray {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        log("src width = $w")
        log("src height = $h")
        val scale = bitmap.calcScale(minWidth, minHeight)
        log("scale = $scale")
        val destW = (w / scale).toInt()
        val destH = (h / scale).toInt()
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

    private fun decodeOptions() = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
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

private fun Bitmap.calcScale(minWidth: Int, minHeight: Int): Float {
    val scaleW = width.toFloat() / minWidth.toFloat()
    val scaleH = height.toFloat() / minHeight.toFloat()
    log("width scale = $scaleW")
    log("height scale = $scaleH")
    return max(1f, min(scaleW, scaleH))
}
