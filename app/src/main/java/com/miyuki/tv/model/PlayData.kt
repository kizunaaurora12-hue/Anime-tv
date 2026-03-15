package com.miyuki.tv.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayData(
    var catId: Int = 0,
    var chId: Int = 0
) : Parcelable {
    companion object {
        const val VALUE = "PLAY_DATA"
    }
}
