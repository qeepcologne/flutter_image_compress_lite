package com.fluttercandies.flutter_image_compress

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageCompressPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var context: Context
    private var channel: MethodChannel? = null
    private var executor: ExecutorService = newExecutor()

    companion object {
        var showLog = false

        private val mainHandler = Handler(Looper.getMainLooper())

        private fun newExecutor(): ExecutorService = Executors.newFixedThreadPool(8)
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        if (executor.isShutdown) executor = newExecutor()
        channel = MethodChannel(binding.binaryMessenger, "flutter_image_compress")
        channel?.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
        executor.shutdown()
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "showLog" -> {
                showLog = call.arguments<Boolean>() == true
                result.success(null)
            }
            "compressWithList" -> executor.execute { compressList(call, result) }
            "compressWithFile" -> executor.execute { compressFileToBytes(call, result) }
            "compressWithFileAndGetFile" -> executor.execute { compressFileToFile(call, result) }
            "getSystemVersion" -> result.success(Build.VERSION.SDK_INT)
            else -> result.notImplemented()
        }
    }

    // MARK: - Method handlers

    private fun compressList(call: MethodCall, result: Result) {
        @Suppress("UNCHECKED_CAST") val args = call.arguments as List<Any>
        val bytes = args[0] as ByteArray
        var minWidth = args[1] as Int
        var minHeight = args[2] as Int
        val quality = args[3] as Int
        val rotate = args[4] as Int
        val autoCorrectionAngle = args[5] as Boolean
        val formatIndex = args[6] as Int
        val keepExif = args[7] as Boolean
        val inSampleSize = args[8] as Int

        val format = CompressFormat.fromIndex(formatIndex)
            ?: return result.postError("UNKNOWN_FORMAT", "unknown format index $formatIndex")

        val exifRotate = if (autoCorrectionAngle) Exif.getRotationDegrees(bytes) else 0
        if (exifRotate == 270 || exifRotate == 90) {
            val tmp = minWidth; minWidth = minHeight; minHeight = tmp
        }

        ByteArrayOutputStream().use { out ->
            try {
                Compressor.encodeBytes(
                    context, format, bytes, out,
                    minWidth, minHeight, quality, rotate + exifRotate,
                    keepExif, inSampleSize,
                )
                result.postSuccess(out.toByteArray())
            } catch (e: Exception) {
                if (showLog) e.printStackTrace()
                result.postSuccess(null)
            }
        }
    }

    private fun compressFileToBytes(call: MethodCall, result: Result) {
        @Suppress("UNCHECKED_CAST") val args = call.arguments as List<Any>
        val path = args[0] as String
        var minWidth = args[1] as Int
        var minHeight = args[2] as Int
        val quality = args[3] as Int
        val rotate = args[4] as Int
        val autoCorrectionAngle = args[5] as Boolean
        val formatIndex = args[6] as Int
        val keepExif = args[7] as Boolean
        val inSampleSize = args[8] as Int
        val oomRetries = args[9] as Int

        val format = CompressFormat.fromIndex(formatIndex)
            ?: return result.postError("UNKNOWN_FORMAT", "unknown format index $formatIndex")

        val exifRotate = if (autoCorrectionAngle) Exif.getRotationDegrees(File(path)) else 0
        if (exifRotate == 270 || exifRotate == 90) {
            val tmp = minWidth; minWidth = minHeight; minHeight = tmp
        }

        ByteArrayOutputStream().use { out ->
            try {
                Compressor.encodeFile(
                    context, format, path, out,
                    minWidth, minHeight, quality, rotate + exifRotate,
                    keepExif, inSampleSize, oomRetries,
                )
                result.postSuccess(out.toByteArray())
            } catch (e: Exception) {
                if (showLog) e.printStackTrace()
                result.postSuccess(null)
            }
        }
    }

    private fun compressFileToFile(call: MethodCall, result: Result) {
        @Suppress("UNCHECKED_CAST") val args = call.arguments as List<Any>
        val path = args[0] as String
        var minWidth = args[1] as Int
        var minHeight = args[2] as Int
        val quality = args[3] as Int
        val targetPath = args[4] as String
        val rotate = args[5] as Int
        val autoCorrectionAngle = args[6] as Boolean
        val formatIndex = args[7] as Int
        val keepExif = args[8] as Boolean
        val inSampleSize = args[9] as Int
        val oomRetries = args[10] as Int

        val format = CompressFormat.fromIndex(formatIndex)
            ?: return result.postError("UNKNOWN_FORMAT", "unknown format index $formatIndex")

        val exifRotate = if (autoCorrectionAngle) Exif.getRotationDegrees(File(path)) else 0
        if (exifRotate == 270 || exifRotate == 90) {
            val tmp = minWidth; minWidth = minHeight; minHeight = tmp
        }

        var out: OutputStream? = null
        try {
            out = File(targetPath).outputStream()
            Compressor.encodeFile(
                context, format, path, out,
                minWidth, minHeight, quality, rotate + exifRotate,
                keepExif, inSampleSize, oomRetries,
            )
            result.postSuccess(targetPath)
        } catch (e: Exception) {
            if (showLog) e.printStackTrace()
            result.postSuccess(null)
        } finally {
            out?.close()
        }
    }

    // MARK: - Reply helpers (post back on the main thread)

    private fun Result.postSuccess(any: Any?) {
        mainHandler.post { success(any) }
    }

    private fun Result.postError(code: String, message: String) {
        mainHandler.post { error(code, message, null) }
    }
}

internal fun log(any: Any?) {
    if (ImageCompressPlugin.showLog) {
        Log.i("flutter_image_compress", any?.toString() ?: "null")
    }
}
