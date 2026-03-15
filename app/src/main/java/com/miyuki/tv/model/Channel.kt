package com.miyuki.tv.model

import com.google.gson.annotations.SerializedName

class Channel {
    var name: String? = null
    @SerializedName("stream_url")
    var streamUrl: String? = null
    @SerializedName("drm_name")
    var drmName: String? = null
    var logo: String? = null
}
