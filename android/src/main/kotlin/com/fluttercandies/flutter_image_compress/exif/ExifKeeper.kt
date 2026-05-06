package com.fluttercandies.flutter_image_compress.exif

import android.content.Context
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

class ExifKeeper {
    private val oldExif: ExifInterface

    constructor(filePath: String) {
        oldExif = ExifInterface(filePath)
    }

    constructor(buf: ByteArray) {
        oldExif = ExifInterface(ByteArrayInputStream(buf))
    }

    fun writeToOutputStream(context: Context, outputStream: ByteArrayOutputStream): ByteArrayOutputStream {
        return try {
            val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { it.write(outputStream.toByteArray()) }
            val newExif = ExifInterface(file.absolutePath)
            copyExif(oldExif, newExif)
            newExif.saveAttributes()
            ByteArrayOutputStream().also { dest ->
                file.inputStream().use { it.copyTo(dest) }
            }
        } catch (ex: Exception) {
            Log.e("ExifDataCopier", "Error preserving Exif data on selected image: $ex")
            outputStream
        }
    }

    companion object {
        private val attributes = listOf(
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

        private fun copyExif(oldExif: ExifInterface, newExif: ExifInterface) {
            for (attribute in attributes) {
                oldExif.getAttribute(attribute)?.let { newExif.setAttribute(attribute, it) }
            }
            try {
                newExif.saveAttributes()
            } catch (_: IOException) {
            }
        }
    }
}
