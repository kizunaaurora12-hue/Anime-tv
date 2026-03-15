package com.miyuki.tv.extra

import android.content.Context
import android.net.ConnectivityManager
import com.miyuki.tv.App

class Network {
    @Suppress("DEPRECATION")
    fun isConnected(): Boolean {
        val cm = App.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = cm.activeNetworkInfo
        return info != null && info.isConnected
    }
}
