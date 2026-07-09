package com.fluttercandies.flutter_image_compress

import android.content.Context
import android.media.ExifInterface
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Modifier
import java.util.UUID

/// Decomposition of an EXIF orientation into a CW rotation (in degrees) and
/// a horizontal flip. Applied in that order this covers all 8 EXIF values,
/// including the compound transpose/transverse/flip-vertical variants.
internal data class Orientation(val degrees: Int, val flipHorizontal: Boolean) {
    companion object {
        val NONE = Orientation(0, false)
    }
}

/// Reads orientation from image bytes or a file.
internal object Exif {
    fun getOrientation(bytes: ByteArray): Orientation = runCatching {
        orientationOf(ExifInterface(ByteArrayInputStream(bytes)))
    }.getOrDefault(Orientation.NONE)

    fun getOrientation(file: File): Orientation = runCatching {
        orientationOf(ExifInterface(file.absolutePath))
    }.getOrDefault(Orientation.NONE)

    // Mapping (rotate CW first, then flip horizontally):
    //   2 FLIP_HORIZONTAL  = 0° + flip H
    //   4 FLIP_VERTICAL    = 180° + flip H  (equivalent to a vertical flip)
    //   5 TRANSPOSE        = 90° + flip H   (mirror along main diagonal)
    //   7 TRANSVERSE       = 270° + flip H  (mirror along anti-diagonal)
    private fun orientationOf(exif: ExifInterface): Orientation =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> Orientation(90, false)
            ExifInterface.ORIENTATION_ROTATE_180 -> Orientation(180, false)
            ExifInterface.ORIENTATION_ROTATE_270 -> Orientation(270, false)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Orientation(0, true)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> Orientation(180, true)
            ExifInterface.ORIENTATION_TRANSPOSE -> Orientation(90, true)
            ExifInterface.ORIENTATION_TRANSVERSE -> Orientation(270, true)
            else -> Orientation.NONE
        }
}

/// Copies every known EXIF attribute from an original image onto a
/// re-encoded image (matches iOS behavior). Only `TAG_ORIENTATION` is
/// skipped because pixels are already rotated during compression.
/// JPEG-only — the framework `ExifInterface.saveAttributes()` supports
/// no other output format.
internal class ExifKeeper private constructor(private val oldExif: ExifInterface) {
    constructor(filePath: String) : this(ExifInterface(filePath))
    constructor(buf: ByteArray) : this(ExifInterface(ByteArrayInputStream(buf)))

    fun writeToOutputStream(context: Context, encoded: ByteArrayOutputStream): ByteArrayOutputStream {
        val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
        return try {
            file.outputStream().use { it.write(encoded.toByteArray()) }
            ExifInterface(file.absolutePath).apply {
                for (name in ALL_TAG_NAMES) {
                    if (name == ExifInterface.TAG_ORIENTATION) continue
                    oldExif.getAttribute(name)?.let { setAttribute(name, it) }
                }
                saveAttributes()
            }
            ByteArrayOutputStream().also { dest ->
                file.inputStream().use { it.copyTo(dest) }
            }
        } catch (ex: Exception) {
            Log.e(LOG_TAG, "exif copy failed", ex)
            encoded
        } finally {
            file.delete()
        }
    }

    private companion object {
        // Enumerate every `ExifInterface.TAG_*` String constant reflectively so the copy
        // matches iOS's "pass the whole property dict through" behavior (see ExifKeeper.swift).
        // Framework `android.media.ExifInterface` is platform code and not obfuscated on
        // the consumer side; reflection over its declared fields is stable.
        private val ALL_TAG_NAMES: List<String> by lazy {
            ExifInterface::class.java.declaredFields
                .filter {
                    it.name.startsWith("TAG_") &&
                        it.type == String::class.java &&
                        Modifier.isStatic(it.modifiers)
                }
                .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
        }
    }
}
