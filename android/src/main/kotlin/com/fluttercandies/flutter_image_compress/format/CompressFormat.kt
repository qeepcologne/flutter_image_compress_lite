package com.fluttercandies.flutter_image_compress.format

import android.graphics.Bitmap
import android.os.Build

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
