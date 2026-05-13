package com.fluttercandies.flutter_image_compress.core

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.ExecutorService

abstract class ResultHandler(
    private var result: MethodChannel.Result?,
    protected val executor: ExecutorService,
) {
    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
    }

    private var isReply = false

    fun reply(any: Any?) {
        if (isReply) return
        isReply = true
        val r = this.result
        this.result = null
        mainHandler.post { r?.success(any) }
    }

    fun replyError(code: String, message: String) {
        if (isReply) return
        isReply = true
        val r = this.result
        this.result = null
        mainHandler.post { r?.error(code, message, null) }
    }
}
