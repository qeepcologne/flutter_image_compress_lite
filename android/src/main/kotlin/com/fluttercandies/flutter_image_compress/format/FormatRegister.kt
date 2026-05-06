package com.fluttercandies.flutter_image_compress.format

import com.fluttercandies.flutter_image_compress.handle.FormatHandler
import java.util.EnumMap

object FormatRegister {
    private val formatMap = EnumMap<CompressFormat, FormatHandler>(CompressFormat::class.java)

    fun registerFormat(handler: FormatHandler) {
        formatMap[handler.type] = handler
    }

    fun findFormat(format: CompressFormat): FormatHandler? = formatMap[format]
}
