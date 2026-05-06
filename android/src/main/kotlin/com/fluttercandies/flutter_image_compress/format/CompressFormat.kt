package com.fluttercandies.flutter_image_compress.format

import android.graphics.Bitmap

/// Order must match the Dart `CompressFormat` enum so `index` is the wire value.
enum class CompressFormat(val typeName: String, val bitmapFormat: Bitmap.CompressFormat?) {
    JPEG("jpeg", Bitmap.CompressFormat.JPEG),
    PNG("png", Bitmap.CompressFormat.PNG),
    HEIC("heic", null),
    WEBP("webp", Bitmap.CompressFormat.WEBP);

    companion object {
        fun fromIndex(index: Int): CompressFormat? = entries.getOrNull(index)
    }
}
