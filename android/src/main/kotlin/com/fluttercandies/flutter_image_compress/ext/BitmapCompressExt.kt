package com.fluttercandies.flutter_image_compress.ext

import android.graphics.Bitmap
import android.graphics.Matrix
import com.fluttercandies.flutter_image_compress.logger.log
import kotlin.math.max
import kotlin.math.min

fun Bitmap.rotate(rotate: Int): Bitmap {
    return if (rotate % 360 != 0) {
        val matrix = Matrix()
        matrix.setRotate(rotate.toFloat())
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, false)
    } else {
        this
    }
}

fun Bitmap.calcScale(minWidth: Int, minHeight: Int): Float {
    val w = width.toFloat()
    val h = height.toFloat()
    val scaleW = w / minWidth.toFloat()
    val scaleH = h / minHeight.toFloat()
    log("width scale = $scaleW")
    log("height scale = $scaleH")
    return max(1f, min(scaleW, scaleH))
}
