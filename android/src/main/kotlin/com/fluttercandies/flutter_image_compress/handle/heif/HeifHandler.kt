package com.fluttercandies.flutter_image_compress.handle.heif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.heifwriter.HeifWriter
import com.fluttercandies.flutter_image_compress.ext.calcScale
import com.fluttercandies.flutter_image_compress.ext.rotate
import com.fluttercandies.flutter_image_compress.format.CompressFormat
import com.fluttercandies.flutter_image_compress.handle.FormatHandler
import com.fluttercandies.flutter_image_compress.logger.log
import com.fluttercandies.flutter_image_compress.util.TmpFileUtil
import java.io.OutputStream

class HeifHandler : FormatHandler {
    override val type: CompressFormat = CompressFormat.HEIC

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
        val tmpFile = TmpFileUtil.createTmpFile(context)
        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, makeOptions(inSampleSize))
        convertToHeif(bitmap, minWidth, minHeight, rotate, tmpFile.absolutePath, quality)
        outputStream.write(tmpFile.readBytes())
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
        val tmpFile = TmpFileUtil.createTmpFile(context)
        val bitmap = BitmapFactory.decodeFile(path, makeOptions(inSampleSize))
        convertToHeif(bitmap, minWidth, minHeight, rotate, tmpFile.absolutePath, quality)
        outputStream.write(tmpFile.readBytes())
    }

    private fun makeOptions(inSampleSize: Int): BitmapFactory.Options {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        options.inSampleSize = inSampleSize
        return options
    }

    private fun convertToHeif(
        bitmap: Bitmap,
        minWidth: Int,
        minHeight: Int,
        rotate: Int,
        targetPath: String,
        quality: Int,
    ) {
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
        val result = Bitmap.createScaledBitmap(bitmap, destW.toInt(), destH.toInt(), true).rotate(rotate)
        val heifWriter = HeifWriter.Builder(
            targetPath,
            result.width,
            result.height,
            HeifWriter.INPUT_MODE_BITMAP,
        ).setQuality(quality).setMaxImages(1).build()
        heifWriter.start()
        heifWriter.addBitmap(result)
        heifWriter.stop(5000)
        heifWriter.close()
    }
}
