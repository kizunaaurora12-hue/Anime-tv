package com.miyuki.tv.extra

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.miyuki.tv.App
import com.miyuki.tv.R
import com.miyuki.tv.extension.isLinkUrl
import com.miyuki.tv.extension.isPathExist
import com.miyuki.tv.model.PlayData
import com.miyuki.tv.model.Source
import java.util.*
import kotlin.collections.ArrayList

class Preferences {
    private val context = App.context
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var editor: SharedPreferences.Editor

    companion object {
        private const val FIRST_TIME         = "FIRST_TIME"
        private const val IGNORED_VERSION    = "IGNORED_VERSION"
        private const val LAST_WATCHED       = "LAST_WATCHED"
        private const val OPEN_LAST_WATCHED  = "OPEN_LAST_WATCHED"
        private const val LAUNCH_AT_BOOT     = "LAUNCH_AT_BOOT"
        private const val SORT_FAVORITE      = "SORT_FAVORITE"
        private const val SORT_CATEGORY      = "SORT_CATEGORY"
        private const val SORT_CHANNEL       = "SORT_CHANNEL"
        private const val OPTIMIZE_PREBUFFER = "OPTIMIZE_PREBUFFER"
        private const val REVERSE_NAVIGATION = "REVERSE_NAVIGATION"
        private const val CONTRIBUTORS       = "CONTRIBUTORS"
        private const val RESIZE_MODE        = "RESIZE_MODE"
        private const val SOURCES_PLAYLIST   = "SOURCES_PLAYLIST"
        private const val COUNTRY_ID         = "COUNTRY_ID"
    }

    var isFirstTime: Boolean
        get() = preferences.getBoolean(FIRST_TIME, true)
        set(value) { edit { putBoolean(FIRST_TIME, value) } }

    var ignoredVersion: Int
        get() = preferences.getInt(IGNORED_VERSION, 0)
        set(value) { edit { putInt(IGNORED_VERSION, value) } }

    var launchAtBoot: Boolean
        get() = preferences.getBoolean(LAUNCH_AT_BOOT, false)
        set(value) { edit { putBoolean(LAUNCH_AT_BOOT, value) } }

    var playLastWatched: Boolean
        get() = preferences.getBoolean(OPEN_LAST_WATCHED, false)
        set(value) { edit { putBoolean(OPEN_LAST_WATCHED, value) } }

    var sortFavorite: Boolean
        get() = preferences.getBoolean(SORT_FAVORITE, false)
        set(value) { edit { putBoolean(SORT_FAVORITE, value) } }

    var sortCategory: Boolean
        get() = preferences.getBoolean(SORT_CATEGORY, false)
        set(value) { edit { putBoolean(SORT_CATEGORY, value) } }

    var sortChannel: Boolean
        get() = preferences.getBoolean(SORT_CHANNEL, true)
        set(value) { edit { putBoolean(SORT_CHANNEL, value) } }

    var watched: PlayData
        get() = Gson().fromJson(preferences.getString(LAST_WATCHED, "{}").toString(), PlayData::class.java)
        set(value) { edit { putString(LAST_WATCHED, Gson().toJson(value)) } }

    var optimizePrebuffer: Boolean
        get() = preferences.getBoolean(OPTIMIZE_PREBUFFER, true)
        set(value) { edit { putBoolean(OPTIMIZE_PREBUFFER, value) } }

    var reverseNavigation: Boolean
        get() = preferences.getBoolean(REVERSE_NAVIGATION, false)
        set(value) { edit { putBoolean(REVERSE_NAVIGATION, value) } }

    var countryId: String
        get() = preferences.getString(COUNTRY_ID, "id").toString()
        set(value) { edit { putString(COUNTRY_ID, value) } }

    var sources: ArrayList<Source>?
        get() {
            val result = ArrayList<Source>()
            val default = Source().apply {
                path   = context.getString(R.string.iptv_playlist)
                active = true
            }
            try {
                val json = preferences.getString(SOURCES_PLAYLIST, "").toString()
                if (json.isBlank()) throw Exception("no playlist sources in preference")
                val list = Gson().fromJson(json, Array<Source>::class.java)
                if (list == null || list.isEmpty()) throw Exception("cannot parse sources")
                list.first().path = default.path
                list.forEach {
                    if (it.path.isLinkUrl()) result.add(it)
                    else if (it.path.isPathExist()) result.add(it)
                }
            } catch (e: Exception) { e.printStackTrace() }

            if (result.isEmpty()) result.add(default)
            val active = result.filter { s -> s.active }
            if (active.isEmpty()) result.first().active = true
            return result
        }
        set(value) { edit { putString(SOURCES_PLAYLIST, Gson().toJson(value)) } }

    var contributors: String?
        get() = preferences.getString(CONTRIBUTORS, context.getString(R.string.main_contributors))
        set(value) { edit { putString(CONTRIBUTORS, value) } }

    var resizeMode: Int
        get() = preferences.getInt(RESIZE_MODE, 0)
        set(value) { edit { putInt(RESIZE_MODE, value) } }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        editor = preferences.edit()
        editor.block()
        editor.apply()
    }
}
