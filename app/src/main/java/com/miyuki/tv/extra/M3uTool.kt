package com.miyuki.tv.extra

import com.miyuki.tv.model.M3U

object M3uTool {
    fun parse(content: String?): List<M3U> {
        if (content.isNullOrBlank()) return emptyList()
        val result = ArrayList<M3U>()
        val lines  = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF")) {
                val m3u     = M3U()
                m3u.channelName = line.substringAfterLast(",").trim()
                m3u.groupName   = Regex("group-title=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "Other"
                val licKey      = Regex("license-key=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                val licName     = Regex("license-name=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                if (!licKey.isNullOrBlank()) {
                    m3u.licenseKey  = licKey
                    m3u.licenseName = licName ?: "drm_${licKey.hashCode()}"
                }
                // Collect stream URLs on following non-comment lines
                val urls = ArrayList<String>()
                var j = i + 1
                while (j < lines.size && !lines[j].trim().startsWith("#")) {
                    val u = lines[j].trim()
                    if (u.isNotBlank()) urls.add(u)
                    j++
                }
                if (urls.isNotEmpty()) {
                    m3u.streamUrl = urls.toTypedArray()
                    result.add(m3u)
                }
                i = j
            } else {
                i++
            }
        }
        return result
    }
}
