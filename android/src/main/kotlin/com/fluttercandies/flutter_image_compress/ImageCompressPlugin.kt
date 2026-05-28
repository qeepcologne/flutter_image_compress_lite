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
import java.io.IOException
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

    // Method handlers

    private fun compressList(call: MethodCall, result: Result) {
        @Suppress("UNCHECKED_CAST") val args = call.arguments as List<Any>
        val bytes = args[0] as ByteArray
        val p = CompressArgs(args, rotateIndex = 4) ?: return result.postError("UNKNOWN_FORMAT", "unknown compress format")
        val exifRotate = if (p.autoCorrectionAngle) Exif.getRotationDegrees(bytes) else 0
        val (w, h) = rotatedTarget(p.minWidth, p.minHeight, exifRotate)

        replyCatching(result) {
            ByteArrayOutputStream().use { out ->
                Compressor.encodeBytes(
                    context, p.format, bytes, out,
                    w, h, p.quality, p.rotate + exifRotate,
                    p.keepExif, p.inSampleSize,
                )
                out.toByteArray()
            }
        }
    }

    private fun compressFileToBytes(call: MethodCall, result: Result) {
        @Suppress("UNCHECKED_CAST") val args = call.arguments as List<Any>
        val path = args[0] as String
        val p = CompressArgs(args, rotateIndex = 4) ?: return result.postError("UNKNOWN_FORMAT", "unknown compress format")
        val exifRotate = if (p.autoCorrectionAngle) Exif.getRotationDegrees(File(path)) else 0
        val (w, h) = rotatedTarget(p.minWidth, p.minHeight, exifRotate)

        replyCatching(result) {
            ByteArrayOutputStream().use { out ->
                Compressor.encodeFile(
                    context, p.format, path, out,
                    w, h, p.quality, p.rotate + exifRotate,
                    p.keepExif, p.inSampleSize, p.oomRetries,
                )
                out.toByteArray()
            }
        }
    }

    private fun compressFileToFile(call: MethodCall, result: Result) {
        @Suppress("UNCHECKED_CAST") val args = call.arguments as List<Any>
        val path = args[0] as String
        val targetPath = args[4] as String
        val p = CompressArgs(args, rotateIndex = 5) ?: return result.postError("UNKNOWN_FORMAT", "unknown compress format")
        val exifRotate = if (p.autoCorrectionAngle) Exif.getRotationDegrees(File(path)) else 0
        val (w, h) = rotatedTarget(p.minWidth, p.minHeight, exifRotate)

        replyCatching(result) {
            try {
                File(targetPath).outputStream().use { out ->
                    Compressor.encodeFile(
                        context, p.format, path, out,
                        w, h, p.quality, p.rotate + exifRotate,
                        p.keepExif, p.inSampleSize, p.oomRetries,
                    )
                }
            } catch (e: CompressException) {
                throw e   // BAD_IMAGE / FILE_NOT_FOUND from the source decode pass through
            } catch (e: IOException) {
                throw CompressException("WRITE_FAILED", "could not write $targetPath: ${e.message}")
            }
            targetPath
        }
    }

    /// 90°/270° EXIF rotation swaps the requested output width/height.
    private fun rotatedTarget(minWidth: Int, minHeight: Int, exifRotate: Int): Pair<Int, Int> =
        if (exifRotate == 90 || exifRotate == 270) minHeight to minWidth else minWidth to minHeight

    // Reply helpers (post back on the main thread)

    /// Runs the encode [block] and replies on the main thread, mapping failures to the same
    /// wire error codes the iOS side uses instead of silently returning null.
    private fun replyCatching(result: Result, block: () -> Any?) {
        try {
            result.postSuccess(block())
        } catch (e: CompressException) {
            if (showLog) e.printStackTrace()
            result.postError(e.code, e.message ?: e.code)
        } catch (e: Exception) {
            if (showLog) e.printStackTrace()
            result.postError("COMPRESS_ERROR", e.message ?: e.toString())
        }
    }

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

/// The shared channel arguments, parsed by position. All three calls share the same layout
/// from [rotateIndex] onward (rotate, autoCorrectionAngle, format, keepExif, inSampleSize,
/// [oomRetries]); only compressWithFileAndGetFile shifts it by one to insert `targetPath` at 4.
/// Returns null when the format index doesn't map to a known [CompressFormat]. Mirrors the
/// iOS `CompressParams(args:rotateIndex:)` so the two platforms parse the wire format the same way.
private class CompressArgs private constructor(
    val format: CompressFormat,
    val minWidth: Int,
    val minHeight: Int,
    val quality: Int,
    val rotate: Int,
    val autoCorrectionAngle: Boolean,
    val keepExif: Boolean,
    val inSampleSize: Int,
    val oomRetries: Int,
) {
    companion object {
        operator fun invoke(args: List<Any>, rotateIndex: Int): CompressArgs? {
            val format = CompressFormat.fromIndex(args[rotateIndex + 2] as Int) ?: return null
            return CompressArgs(
                format = format,
                minWidth = args[1] as Int,
                minHeight = args[2] as Int,
                quality = args[3] as Int,
                rotate = args[rotateIndex] as Int,
                autoCorrectionAngle = args[rotateIndex + 1] as Boolean,
                keepExif = args[rotateIndex + 3] as Boolean,
                inSampleSize = args[rotateIndex + 4] as Int,
                oomRetries = (args.getOrNull(rotateIndex + 5) as? Int) ?: 0,
            )
        }
    }
}
