package com.fluttercandies.flutter_image_compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/// Order must match the Dart `CompressFormat` enum so `index` is the wire value.
enum class CompressFormat(val typeName: String) {
    JPEG("jpeg"),
    PNG("png"),
    HEIC("heic"),
    WEBP("webp");

    /// Null when this OS version cannot encode the format
    /// (currently only HEIC, which needs API 30+).
    val bitmapFormat: Bitmap.CompressFormat?
        get() = when (this) {
            JPEG -> Bitmap.CompressFormat.JPEG
            PNG -> Bitmap.CompressFormat.PNG
            WEBP -> Bitmap.CompressFormat.WEBP
            HEIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.HEIC
            } else {
                null
            }
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
        inSampleSize: Int,
    ) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions(format, inSampleSize))
        val encoded = compress(bitmap, format, minWidth, minHeight, quality, rotate)
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
        inSampleSize: Int,
        oomRetries: Int,
    ) {
        if (oomRetries <= 0) return
        try {
            val bitmap = BitmapFactory.decodeFile(path, decodeOptions(format, inSampleSize))
            val encoded = compress(bitmap, format, minWidth, minHeight, quality, rotate)
            writeOutput(output, encoded, context, format, keepExif) { ExifKeeper(path) }
        } catch (e: OutOfMemoryError) {
            encodeFile(
                context, format, path, output,
                minWidth, minHeight, quality, rotate,
                keepExif, inSampleSize * 2, oomRetries - 1,
            )
        }
    }

    private fun compress(
        bitmap: Bitmap,
        format: CompressFormat,
        minWidth: Int,
        minHeight: Int,
        quality: Int,
        rotate: Int,
    ): ByteArray {
        val bitmapFormat = requireNotNull(format.bitmapFormat) {
            "${format.typeName} encoding is not available on this OS version"
        }
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        log("src width = $w")
        log("src height = $h")
        val scale = bitmap.calcScale(minWidth, minHeight)
        log("scale = $scale")
        val destW = w / scale
        val destH = h / scale
        log("dst width = $destW")
        log("dst height = $destH")
        val out = ByteArrayOutputStream()
        Bitmap.createScaledBitmap(bitmap, destW.toInt(), destH.toInt(), true)
            .rotate(rotate)
            .compress(bitmapFormat, quality, out)
        return out.toByteArray()
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

    private fun decodeOptions(format: CompressFormat, inSampleSize: Int): BitmapFactory.Options {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = false
        // Only JPEG is opaque; PNG/WebP/HEIC may carry alpha and would silently
        // lose transparency under RGB_565.
        options.inPreferredConfig = if (format == CompressFormat.JPEG) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }
        options.inSampleSize = inSampleSize
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
