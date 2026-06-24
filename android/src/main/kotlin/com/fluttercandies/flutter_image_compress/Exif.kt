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
            Log.e(LOG_TAG, "exif copy failed", ex)
            encoded
        }
    }

    private companion object {
        // TAG_PHOTOGRAPHIC_SENSITIVITY is the modern EXIF 2.3 name but lives only on AndroidX
        // ExifInterface; the framework class still exposes the deprecated TAG_ISO_SPEED_RATINGS,
        // which is what we have to use. Same tag-id (34855), same wire bytes.
        @Suppress("DEPRECATION")
        private val PRESERVED_ATTRIBUTES = listOf(
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
        )
    }
}
