package com.miyuki.tv.extra

import com.miyuki.tv.extension.isLinkUrl
import com.miyuki.tv.extension.toRequest
import com.miyuki.tv.model.Source
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import com.miyuki.tv.App.Companion.runOnUiThread

class SourceChecker {
    private var source: Source? = null
    private var result: Result? = null

    interface Result {
        fun onCheckResult(result: Boolean)
    }

    fun set(source: Source, result: Result): SourceChecker {
        this.source = source
        this.result = result
        return this
    }

    fun run() {
        val src = source ?: return
        if (!src.path.isLinkUrl()) { result?.onCheckResult(false); return }

        HttpClient(false)
            .create(src.path.toRequest())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { result?.onCheckResult(false) }
                }
                override fun onResponse(call: Call, response: Response) {
                    val ok = response.isSuccessful && !response.body()?.string().isNullOrBlank()
                    runOnUiThread { result?.onCheckResult(ok) }
                }
            })
    }
}
