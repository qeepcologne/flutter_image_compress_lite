package com.fluttercandies.flutter_image_compress

import android.content.Context
import android.os.Build
import com.fluttercandies.flutter_image_compress.core.CompressFileHandler
import com.fluttercandies.flutter_image_compress.core.CompressListHandler
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageCompressPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var context: Context
    private var channel: MethodChannel? = null
    private var executor: ExecutorService = newExecutor()

    companion object {
        var showLog = false

        private fun newExecutor(): ExecutorService = Executors.newFixedThreadPool(8)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "showLog" -> result.success(handleLog(call))
            "compressWithList" -> CompressListHandler(call, result, executor).handle(context)
            "compressWithFile" -> CompressFileHandler(call, result, executor).handle(context)
            "compressWithFileAndGetFile" -> CompressFileHandler(call, result, executor).handleGetFile(context)
            "getSystemVersion" -> result.success(Build.VERSION.SDK_INT)
            else -> result.notImplemented()
        }
    }

    private fun handleLog(call: MethodCall): Int {
        val arg = call.arguments<Boolean>()
        showLog = (arg == true)
        return 1
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
}
