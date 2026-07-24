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
enum class CompressFormat(val bitmapFormat: Bitmap.CompressFormat?) {
    JPEG(Bitmap.CompressFormat.JPEG),
    PNG(Bitmap.CompressFormat.PNG),
    HEIC(null),
    WEBP(Bitmap.CompressFormat.WEBP),
    AVIF(null);

    companion object {
        fun fromIndex(index: Int): CompressFormat? = entries.getOrNull(index)
    }
}

internal object Compressor {

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

    // HeifWriter/AvifWriter only write to a file path, so we round-trip through cacheDir.
    private fun encodeHeic(context: Context, bitmap: Bitmap, quality: Int): ByteArray {
        val tmp = File.createTempFile("heic_", ".heic", context.cacheDir)
        try {
            val writer = HeifWriter.Builder(
                tmp.absolutePath,
                bitmap.width,
                bitmap.height,
                HeifWriter.INPUT_MODE_BITMAP,
            ).setQuality(quality).setMaxImages(1).build()
            try {
                writer.start()
                writer.addBitmap(bitmap)
                writer.stop(5000)
            } finally {
                writer.close()
            }
            return tmp.readBytes()
        } finally {
            tmp.delete()
        }
    }

    private fun encodeAvif(context: Context, bitmap: Bitmap, quality: Int): ByteArray {
        val tmp = File.createTempFile("avif_", ".avif", context.cacheDir)
        try {
            val writer = AvifWriter.Builder(
                tmp.absolutePath,
                bitmap.width,
                bitmap.height,
                AvifWriter.INPUT_MODE_BITMAP,
            ).setQuality(quality).setMaxImages(1).build()
            try {
                writer.start()
                writer.addBitmap(bitmap)
                writer.stop(5000)
            } finally {
                writer.close()
            }
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
        if (keepExif && canWriteExif(format)) {
            val tmp = ByteArrayOutputStream()
            tmp.write(encoded)
            output.write(exifKeeper().writeToOutputStream(context, tmp).toByteArray())
        } else {
            if (keepExif) Log.w(LOG_TAG, unsupportedExifMessage(format))
            output.write(encoded)
        }
    }

    // Framework `ExifInterface.saveAttributes()` support was extended over time:
    //   JPEG        always
    //   PNG         API 30 (Android 11)
    //   WebP        API 31 (Android 12)
    //   HEIC/AVIF   never (HEIF-based containers; not in the writer's format list)
    private fun canWriteExif(format: CompressFormat): Boolean = when (format) {
        CompressFormat.JPEG -> true
        CompressFormat.PNG -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        CompressFormat.WEBP -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        CompressFormat.HEIC, CompressFormat.AVIF -> false
    }

    private fun unsupportedExifMessage(format: CompressFormat): String = when (format) {
        CompressFormat.JPEG -> "keepExif=true ignored (JPEG should be supported — please report)"
        CompressFormat.PNG -> "keepExif=true ignored for PNG on API ${Build.VERSION.SDK_INT}: framework ExifInterface writer requires API 30+"
        CompressFormat.WEBP -> "keepExif=true ignored for WebP on API ${Build.VERSION.SDK_INT}: framework ExifInterface writer requires API 31+"
        CompressFormat.HEIC -> "keepExif=true ignored for HEIC output: no ExifInterface writer supports this format"
        CompressFormat.AVIF -> "keepExif=true ignored for AVIF output: no ExifInterface writer supports this format"
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
