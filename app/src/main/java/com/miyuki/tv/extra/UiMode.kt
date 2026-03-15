package com.miyuki.tv.extra

import android.content.Context
import android.content.pm.PackageManager
import com.miyuki.tv.App

class UiMode {
    fun isTelevision(): Boolean {
        return App.context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
