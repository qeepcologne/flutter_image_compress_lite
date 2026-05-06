package com.fluttercandies.flutter_image_compress.handle.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.fluttercandies.flutter_image_compress.exif.ExifKeeper
import com.fluttercandies.flutter_image_compress.ext.calcScale
import com.fluttercandies.flutter_image_compress.ext.rotate
import com.fluttercandies.flutter_image_compress.format.CompressFormat
import com.fluttercandies.flutter_image_compress.handle.FormatHandler
import com.fluttercandies.flutter_image_compress.logger.log
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class CommonHandler(override val type: CompressFormat) : FormatHandler {

    private val bitmapFormat: Bitmap.CompressFormat = requireNotNull(type.bitmapFormat) {
        "CommonHandler does not support ${type.typeName}"
    }

    override fun handleByteArray(
        context: Context,
        byteArray: ByteArray,
        outputStream: OutputStream,
        minWidth: Int,
        minHeight: Int,
        quality: Int,
        rotate: Int,
        keepExif: Boolean,
        inSampleSize: Int,
    ) {
        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, makeOptions(inSampleSize))
        val result = compressBitmap(bitmap, minWidth, minHeight, quality, rotate)
        if (keepExif && type == CompressFormat.JPEG) {
            val byteArrayOutputStream = ByteArrayOutputStream()
            byteArrayOutputStream.write(result)
            val resultStream = ExifKeeper(byteArray).writeToOutputStream(context, byteArrayOutputStream)
            outputStream.write(resultStream.toByteArray())
        } else {
            outputStream.write(result)
        }
    }

    override fun handleFile(
        context: Context,
        path: String,
        outputStream: OutputStream,
        minWidth: Int,
        minHeight: Int,
        quality: Int,
        rotate: Int,
        keepExif: Boolean,
        inSampleSize: Int,
        numberOfRetries: Int,
    ) {
        if (numberOfRetries <= 0) return
        try {
            val bitmap = BitmapFactory.decodeFile(path, makeOptions(inSampleSize))
            val result = compressBitmap(bitmap, minWidth, minHeight, quality, rotate)
            if (keepExif && type == CompressFormat.JPEG) {
                val byteArrayOutputStream = ByteArrayOutputStream()
                byteArrayOutputStream.write(result)
                val tmpOutputStream = ExifKeeper(path).writeToOutputStream(context, byteArrayOutputStream)
                outputStream.write(tmpOutputStream.toByteArray())
            } else {
                outputStream.write(result)
            }
        } catch (e: OutOfMemoryError) {
            handleFile(
                context,
                path,
                outputStream,
                minWidth,
                minHeight,
                quality,
                rotate,
                keepExif,
                inSampleSize * 2,
                numberOfRetries - 1,
            )
        }
    }

    private fun compressBitmap(
        bitmap: Bitmap,
        minWidth: Int,
        minHeight: Int,
        quality: Int,
        rotate: Int,
    ): ByteArray {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        log("src width = $w")
        log("src height = $h")
        val scale = bitmap.calcScale(minWidth, minHeight)
        log("scale = $scale")
        val destW = w / scale
        val destH = h / scale
        log("dst width = $destW")
        log("dst height = $destH")
        val outputStream = ByteArrayOutputStream()
        Bitmap.createScaledBitmap(bitmap, destW.toInt(), destH.toInt(), true)
            .rotate(rotate)
            .compress(bitmapFormat, quality, outputStream)
        return outputStream.toByteArray()
    }

    private fun makeOptions(inSampleSize: Int): BitmapFactory.Options {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        options.inSampleSize = inSampleSize
        return options
    }
}
