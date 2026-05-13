package com.fluttercandies.flutter_image_compress.format

import com.fluttercandies.flutter_image_compress.handle.FormatHandler
import com.fluttercandies.flutter_image_compress.handle.common.CommonHandler
import java.util.EnumMap

object FormatRegister {
    private val formatMap: Map<CompressFormat, FormatHandler> =
        EnumMap<CompressFormat, FormatHandler>(CompressFormat::class.java).apply {
            put(CompressFormat.JPEG, CommonHandler(CompressFormat.JPEG))
            put(CompressFormat.PNG, CommonHandler(CompressFormat.PNG))
            put(CompressFormat.HEIC, CommonHandler(CompressFormat.HEIC))
            put(CompressFormat.WEBP, CommonHandler(CompressFormat.WEBP))
        }

    fun findFormat(format: CompressFormat): FormatHandler? = formatMap[format]
}
