package com.miyuki.tv.extra

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class HttpClient(private val useCache: Boolean = false) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun create(request: Request): Call {
        val req = if (!useCache) {
            request.newBuilder()
                .header("Cache-Control", "no-cache")
                .build()
        } else request
        return client.newCall(req)
    }
}
