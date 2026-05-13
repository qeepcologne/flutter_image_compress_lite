package com.fluttercandies.flutter_image_compress

import android.content.Context
import android.media.ExifInterface
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/// Reads orientation from image bytes or a file.
internal object Exif {
    fun getRotationDegrees(bytes: ByteArray): Int = runCatching {
        rotationDegreesOf(ExifInterface(ByteArrayInputStream(bytes)))
    }.getOrDefault(0)

    fun getRotationDegrees(file: File): Int = runCatching {
        rotationDegreesOf(ExifInterface(file.absolutePath))
    }.getOrDefault(0)

    private fun rotationDegreesOf(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}

/// Copies a curated subset of EXIF attributes from an original image
/// onto a re-encoded image. JPEG-only (the framework `ExifInterface`
/// requires a file-path target to `saveAttributes()`).
internal class ExifKeeper {
    private val oldExif: ExifInterface

    constructor(filePath: String) {
        oldExif = ExifInterface(filePath)
    }

    constructor(buf: ByteArray) {
        oldExif = ExifInterface(ByteArrayInputStream(buf))
    }

    fun writeToOutputStream(context: Context, encoded: ByteArrayOutputStream): ByteArrayOutputStream {
        return try {
            val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { it.write(encoded.toByteArray()) }
            val newExif = ExifInterface(file.absolutePath)
            for (attribute in PRESERVED_ATTRIBUTES) {
                oldExif.getAttribute(attribute)?.let { newExif.setAttribute(attribute, it) }
            }
            newExif.saveAttributes()
            ByteArrayOutputStream().also { dest ->
                file.inputStream().use { it.copyTo(dest) }
            }
        } catch (ex: Exception) {
            Log.e("flutter_image_compress", "exif copy failed: $ex")
            encoded
        }
    }

    private companion object {
        private val PRESERVED_ATTRIBUTES = listOf(
            "FNumber",
            "ExposureTime",
            "ISOSpeedRatings",
            "GPSAltitude",
            "GPSAltitudeRef",
            "FocalLength",
            "GPSDateStamp",
            "WhiteBalance",
            "GPSProcessingMethod",
            "GPSTimeStamp",
            "DateTime",
            "Flash",
            "GPSLatitude",
            "GPSLatitudeRef",
            "GPSLongitude",
            "GPSLongitudeRef",
            "Make",
            "Model",
        )
    }
}
