package com.miyuki.tv

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.multidex.MultiDexApplication
import com.miyuki.tv.extra.LocaleHelper

class App : MultiDexApplication() {
    companion object {
        private lateinit var current: Application

        val context: Context
            get() = current.applicationContext

        fun runOnUiThread(task: Runnable) {
            Handler(Looper.getMainLooper()).post(task)
        }
    }

    override fun onCreate() {
        super.onCreate()
        current = this
    }

    override fun attachBaseContext(base: Context) {
        val lang = LocaleHelper.getLanguageCode(base)
        super.attachBaseContext(LocaleHelper.setLocale(base, lang))
    }
}
