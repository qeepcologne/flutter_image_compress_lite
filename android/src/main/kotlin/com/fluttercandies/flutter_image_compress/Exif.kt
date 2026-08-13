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
    }.onFailure { logRead(it) }.getOrDefault(Orientation.NONE)

    fun getOrientation(file: File): Orientation = runCatching {
        orientationOf(ExifInterface(file.absolutePath))
    }.onFailure { logRead(it) }.getOrDefault(Orientation.NONE)

    private fun logRead(t: Throwable) {
        if (ImageCompressPlugin.showLog) Log.w(LOG_TAG, "exif read failed, ignoring orientation", t)
    }

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
/// re-encoded image (matches iOS behavior), minus the tags that describe the
/// *source* file rather than the picture — see [SKIPPED_TAGS].
///
/// Runtime format support depends on both the device's Android version
/// and the output format, per framework `ExifInterface.saveAttributes()`:
/// JPEG always; PNG on API 30+; WebP on API 31+. HEIC is never supported.
/// `Compressor.writeOutput` gates on this before invoking us.
internal class ExifKeeper private constructor(private val oldExif: ExifInterface) {
    constructor(filePath: String) : this(ExifInterface(filePath))
    constructor(buf: ByteArray) : this(ExifInterface(ByteArrayInputStream(buf)))

    fun writeToOutputStream(context: Context, encoded: ByteArrayOutputStream): ByteArrayOutputStream {
        // Extension is arbitrary — ExifInterface sniffs the container from magic bytes.
        val file = File(context.cacheDir, "${UUID.randomUUID()}.exif")
        return try {
            file.outputStream().use { it.write(encoded.toByteArray()) }
            ExifInterface(file.absolutePath).apply {
                for (name in ALL_TAG_NAMES) {
                    if (name in SKIPPED_TAGS) continue
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
        /// Tags that describe the source *file* — its pixel buffer, its encoding structure, or offsets into its
        /// bytes — none of which survive a scale and re-encode. Copying them writes claims that contradict the
        /// output: stale dimensions, a colour space the new bytes are not in, and thumbnail/strip offsets pointing
        /// into a file that no longer exists. `TAG_ORIENTATION` is skipped because the pixels were already rotated.
        /// Mirrors the source-only key list in `ExifKeeper.swift`.
        private val SKIPPED_TAGS: Set<String> = setOf(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_THUMBNAIL_ORIENTATION,
            // dimensions
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_PIXEL_X_DIMENSION,
            ExifInterface.TAG_PIXEL_Y_DIMENSION,
            ExifInterface.TAG_DEFAULT_CROP_SIZE,
            // pixel-buffer / colour description
            ExifInterface.TAG_BITS_PER_SAMPLE,
            ExifInterface.TAG_COLOR_SPACE,
            ExifInterface.TAG_COMPRESSION,
            ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
            ExifInterface.TAG_SAMPLES_PER_PIXEL,
            ExifInterface.TAG_PLANAR_CONFIGURATION,
            ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING,
            ExifInterface.TAG_Y_CB_CR_POSITIONING,
            // offsets into the source bytes
            ExifInterface.TAG_ROWS_PER_STRIP,
            ExifInterface.TAG_STRIP_OFFSETS,
            ExifInterface.TAG_STRIP_BYTE_COUNTS,
            ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
            ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
            ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH,
            ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH,
            ExifInterface.TAG_SUBFILE_TYPE,
            ExifInterface.TAG_NEW_SUBFILE_TYPE,
            // raw-container structure, meaningless in a JPEG/PNG/WebP output
            ExifInterface.TAG_DNG_VERSION,
            ExifInterface.TAG_ORF_ASPECT_FRAME,
            ExifInterface.TAG_ORF_PREVIEW_IMAGE_START,
            ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH,
            ExifInterface.TAG_ORF_THUMBNAIL_IMAGE,
            ExifInterface.TAG_RW2_JPG_FROM_RAW,
            ExifInterface.TAG_RW2_SENSOR_TOP_BORDER,
            ExifInterface.TAG_RW2_SENSOR_LEFT_BORDER,
            ExifInterface.TAG_RW2_SENSOR_BOTTOM_BORDER,
            ExifInterface.TAG_RW2_SENSOR_RIGHT_BORDER,
        )

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
