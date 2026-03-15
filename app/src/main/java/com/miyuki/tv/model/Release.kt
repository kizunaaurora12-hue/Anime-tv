package com.miyuki.tv.model

import com.google.gson.annotations.SerializedName

class Release {
    @SerializedName("version_name")
    var versionName: String = ""
    @SerializedName("version_code")
    var versionCode: Int = 0
    @SerializedName("download_url")
    var downloadUrl: String = ""
    var changelog: ArrayList<String> = ArrayList()
}
