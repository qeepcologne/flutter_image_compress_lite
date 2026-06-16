package com.fluttercandies.flutter_image_compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
enum class CompressFormat(val typeName: String) {
    JPEG("jpeg"),
    PNG("png"),
    HEIC("heic"),
    WEBP("webp");

    /// Null for HEIC, which goes through androidx.heifwriter instead of
    /// `Bitmap.compress()`. The platform `Bitmap.CompressFormat` enum has no
    /// HEIC entry at any API level.
    val bitmapFormat: Bitmap.CompressFormat?
        get() = when (this) {
            JPEG -> Bitmap.CompressFormat.JPEG
            PNG -> Bitmap.CompressFormat.PNG
            WEBP -> Bitmap.CompressFormat.WEBP
            HEIC -> null
        }

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
        keepExif: Boolean,
    ) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions(format))
            ?: throw CompressException("BAD_IMAGE", "could not decode image bytes")
        val encoded = compress(context, bitmap, format, minWidth, minHeight, quality, rotate)
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
        keepExif: Boolean,
    ) {
        if (!File(path).exists()) throw CompressException("FILE_NOT_FOUND", "could not read $path")
        val bitmap = BitmapFactory.decodeFile(path, decodeOptions(format))
            ?: throw CompressException("BAD_IMAGE", "could not decode image at $path")
        val encoded = compress(context, bitmap, format, minWidth, minHeight, quality, rotate)
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
        val scaled = Bitmap.createScaledBitmap(bitmap, destW, destH, true).rotate(rotate)
        return if (format == CompressFormat.HEIC) {
            encodeHeic(context, scaled, quality)
        } else {
            val out = ByteArrayOutputStream()
            scaled.compress(format.bitmapFormat!!, quality, out)
            out.toByteArray()
        }
    }

    // HeifWriter only writes to a file path, so we round-trip through cacheDir.
    private fun encodeHeic(context: Context, bitmap: Bitmap, quality: Int): ByteArray {
        val tmp = File.createTempFile("heic_", ".heic", context.cacheDir)
        try {
            val writer = HeifWriter.Builder(
                tmp.absolutePath,
                bitmap.width,
                bitmap.height,
                HeifWriter.INPUT_MODE_BITMAP,
            ).setQuality(quality).setMaxImages(1).build()
            writer.start()
            writer.addBitmap(bitmap)
            writer.stop(5000)
            writer.close()
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
        if (keepExif && format == CompressFormat.JPEG) {
            val tmp = ByteArrayOutputStream()
            tmp.write(encoded)
            output.write(exifKeeper().writeToOutputStream(context, tmp).toByteArray())
        } else {
            output.write(encoded)
        }
    }

    private fun decodeOptions(format: CompressFormat): BitmapFactory.Options {
        val options = BitmapFactory.Options()
        // Only JPEG is opaque; PNG/WebP/HEIC may carry alpha and would silently
        // lose transparency under RGB_565.
        options.inPreferredConfig = if (format == CompressFormat.JPEG) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }
        return options
    }
}

private fun Bitmap.rotate(degrees: Int): Bitmap = if (degrees % 360 != 0) {
    val matrix = Matrix().apply { setRotate(degrees.toFloat()) }
    Bitmap.createBitmap(this, 0, 0, width, height, matrix, false)
} else {
    this
}

private fun Bitmap.calcScale(minWidth: Int, minHeight: Int): Float {
    val scaleW = width.toFloat() / minWidth.toFloat()
    val scaleH = height.toFloat() / minHeight.toFloat()
    log("width scale = $scaleW")
    log("height scale = $scaleH")
    return max(1f, min(scaleW, scaleH))
}
