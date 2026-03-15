package com.miyuki.tv.extra

import com.miyuki.tv.extension.isLinkUrl
import com.miyuki.tv.extension.toFile
import com.miyuki.tv.extension.toRequest

import com.miyuki.tv.model.Playlist
import com.miyuki.tv.model.Source
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import com.miyuki.tv.App.Companion.runOnUiThread

class SourcesReader {
    private var sources: ArrayList<Source> = ArrayList()
    private var result: Result? = null

    interface Result {
        fun onProgress(source: String) {}
        fun onResponse(playlist: Playlist?) {}
        fun onError(source: String, error: String) {}
        fun onFinish() {}
    }

    fun set(sources: ArrayList<Source>?, result: Result): SourcesReader {
        sources?.let { this.sources.addAll(it) }
        this.result = result
        return this
    }

    fun process(useCache: Boolean) {
        if (sources.isEmpty()) { result?.onFinish(); return }

        val source = sources.first()
        sources.remove(source)

        if (!source.active) { process(useCache); return }

        result?.onProgress(source.path)

        if (!source.path.isLinkUrl()) {
            val playlist = PlaylistHelper().readFile(source.path.toFile())
            result?.onResponse(playlist)
            process(useCache)
            return
        }

        HttpClient(useCache)
            .create(source.path.toRequest())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        result?.onError(source.path, e.message.toString())
                        process(useCache)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val content = response.body()?.string()
                    runOnUiThread {
                        if (response.isSuccessful && !content.isNullOrBlank()) {
                            val playlist = com.miyuki.tv.extension.toPlaylist(content)
                            if (playlist != null && !playlist.categories.isEmpty())
                                result?.onResponse(playlist)
                            else
                                result?.onError(source.path, "parse error")
                        } else {
                            result?.onError(source.path, response.message())
                        }
                        process(useCache)
                    }
                }
            })
    }


}
