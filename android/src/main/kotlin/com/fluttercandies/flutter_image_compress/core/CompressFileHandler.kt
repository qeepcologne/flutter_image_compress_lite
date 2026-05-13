package com.fluttercandies.flutter_image_compress.core

import android.content.Context
import com.fluttercandies.flutter_image_compress.ImageCompressPlugin
import com.fluttercandies.flutter_image_compress.exif.Exif
import com.fluttercandies.flutter_image_compress.format.CompressFormat
import com.fluttercandies.flutter_image_compress.format.FormatRegister
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ExecutorService

class CompressFileHandler(
    private val call: MethodCall,
    result: MethodChannel.Result,
    executor: ExecutorService,
) : ResultHandler(result, executor) {
    fun handle(context: Context) {
        executor.execute {
            @Suppress("UNCHECKED_CAST")
            val args: List<Any> = call.arguments as List<Any>
            val filePath = args[0] as String
            var minWidth = args[1] as Int
            var minHeight = args[2] as Int
            val quality = args[3] as Int
            val rotate = args[4] as Int
            val autoCorrectionAngle = args[5] as Boolean
            val formatIndex = args[6] as Int
            val format = CompressFormat.fromIndex(formatIndex)
            val keepExif = args[7] as Boolean
            val inSampleSize = args[8] as Int
            val oomRetries = args[9] as Int
            if (format == null) {
                replyError("UNKNOWN_FORMAT", "unknown format index $formatIndex")
                return@execute
            }
            val formatHandler = FormatRegister.findFormat(format)
            if (formatHandler == null) {
                replyError("UNKNOWN_FORMAT", "no handler registered for ${format.typeName}")
                return@execute
            }
            val exifRotate = if (autoCorrectionAngle) {
                Exif.getRotationDegrees(File(filePath))
            } else {
                0
            }
            if (exifRotate == 270 || exifRotate == 90) {
                val tmp = minWidth
                minWidth = minHeight
                minHeight = tmp
            }
            val targetRotate = rotate + exifRotate
            val outputStream = ByteArrayOutputStream()
            try {
                formatHandler.handleFile(
                    context,
                    filePath,
                    outputStream,
                    minWidth,
                    minHeight,
                    quality,
                    targetRotate,
                    keepExif,
                    inSampleSize,
                    oomRetries
                )
                reply(outputStream.toByteArray())
            } catch (e: Exception) {
                if (ImageCompressPlugin.showLog) e.printStackTrace()
                reply(null)
            } finally {
                outputStream.close()
            }
        }
    }

    fun handleGetFile(context: Context) {
        executor.execute {
            @Suppress("UNCHECKED_CAST")
            val args: List<Any> = call.arguments as List<Any>
            val file = args[0] as String
            var minWidth = args[1] as Int
            var minHeight = args[2] as Int
            val quality = args[3] as Int
            val targetPath = args[4] as String
            val rotate = args[5] as Int
            val autoCorrectionAngle = args[6] as Boolean
            val formatIndex = args[7] as Int
            val format = CompressFormat.fromIndex(formatIndex)
            val keepExif = args[8] as Boolean
            val inSampleSize = args[9] as Int
            val oomRetries = args[10] as Int
            if (format == null) {
                replyError("UNKNOWN_FORMAT", "unknown format index $formatIndex")
                return@execute
            }
            val formatHandler = FormatRegister.findFormat(format)
            if (formatHandler == null) {
                replyError("UNKNOWN_FORMAT", "no handler registered for ${format.typeName}")
                return@execute
            }
            val exifRotate = if (autoCorrectionAngle) {
                Exif.getRotationDegrees(File(file))
            } else {
                0
            }
            if (exifRotate == 270 || exifRotate == 90) {
                val tmp = minWidth
                minWidth = minHeight
                minHeight = tmp
            }
            val targetRotate = rotate + exifRotate
            var outputStream: OutputStream? = null
            try {
                outputStream = File(targetPath).outputStream()
                formatHandler.handleFile(
                    context,
                    file,
                    outputStream,
                    minWidth,
                    minHeight,
                    quality,
                    targetRotate,
                    keepExif,
                    inSampleSize,
                    oomRetries
                )
                reply(targetPath)
            } catch (e: Exception) {
                if (ImageCompressPlugin.showLog) e.printStackTrace()
                reply(null)
            } finally {
                outputStream?.close()
            }
        }
    }
}
