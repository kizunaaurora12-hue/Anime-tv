package com.miyuki.tv.extra

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.miyuki.tv.model.Category
import com.miyuki.tv.model.Channel
import com.miyuki.tv.model.DrmLicense
import com.miyuki.tv.model.Playlist

/**
 * MiyukiTV — channels.json converter
 *
 * channels.json format:
 * {
 *   "channels": [
 *     {
 *       "id": 1,
 *       "name": "Channel Name",
 *       "cat": "nasional",          // category key
 *       "url": "https://...",
 *       "logo": "https://...",      // optional
 *       "drm": false,
 *       "drmType": "ClearKey",      // "ClearKey" or "Widevine"
 *       "licUrl": "keyid:key",      // ClearKey: "keyid:key", Widevine: license URL
 *       "ua": "Mozilla/5.0 ..."    // optional user-agent override
 *     }
 *   ]
 * }
 */
object MiyukiJsonConverter {
    private const val TAG = "MiyukiConverter"

    private val CAT_NAMES = mapOf(
        "nasional"      to "Nasional",
        "berita"        to "Berita",
        "hiburan"       to "Hiburan",
        "olahraga"      to "Olahraga",
        "internasional" to "Internasional",
        "jepang"        to "Jepang",
        "vision"        to "Vision+ DRM",
        "indihome"      to "IndiHome DRM",
        "custom"        to "Custom"
    )

    private val CAT_ORDER = listOf(
        "nasional", "berita", "hiburan", "olahraga",
        "internasional", "jepang", "vision", "indihome", "custom"
    )

    fun convert(jsonString: String): Playlist? {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            if (!root.has("channels")) return null

            val channelsArray: JsonArray = root.getAsJsonArray("channels")
            val playlist    = Playlist()
            val categoryMap = LinkedHashMap<String, ArrayList<Channel>>()
            val drmMap      = LinkedHashMap<String, String>()

            for (element in channelsArray) {
                val obj     = element.asJsonObject
                val name    = obj.get("name")?.asString   ?: continue
                val url     = obj.get("url")?.asString    ?: continue
                val cat     = obj.get("cat")?.asString    ?: "nasional"
                val hasDrm  = obj.get("drm")?.asBoolean  ?: false
                val drmType = obj.get("drmType")?.asString ?: "ClearKey"
                val licUrl  = obj.get("licUrl")?.asString
                val ua      = obj.get("ua")?.asString
                val logo    = obj.get("logo")?.asString

                val channel = Channel().apply {
                    this.name      = name
                    this.streamUrl = if (!ua.isNullOrBlank()) "$url|user-agent=$ua" else url
                    this.logo      = logo
                }

                if (hasDrm && !licUrl.isNullOrBlank()) {
                    val isWidevine = drmType.equals("Widevine", ignoreCase = true)
                    val drmName = if (isWidevine) "widevine_${licUrl.hashCode()}"
                                  else             "clearkey_${licUrl.hashCode()}"
                    channel.drmName = drmName
                    if (!drmMap.containsKey(drmName)) drmMap[drmName] = licUrl
                }

                val catKey = cat.lowercase()
                if (!categoryMap.containsKey(catKey)) categoryMap[catKey] = ArrayList()
                categoryMap[catKey]?.add(channel)
            }

            // Build ordered categories
            val categories = ArrayList<Category>()
            for (key in CAT_ORDER) {
                if (categoryMap.containsKey(key)) {
                    categories.add(Category().apply {
                        this.name     = CAT_NAMES[key] ?: key.replaceFirstChar { it.uppercase() }
                        this.channels = categoryMap[key]
                    })
                }
            }
            // Any extra unknown categories
            for ((key, channels) in categoryMap) {
                if (!CAT_ORDER.contains(key)) {
                    categories.add(Category().apply {
                        this.name     = key.replaceFirstChar { it.uppercase() }
                        this.channels = channels
                    })
                }
            }

            playlist.categories = categories

            val drmLicenses = ArrayList<DrmLicense>()
            for ((name, lic) in drmMap) {
                drmLicenses.add(DrmLicense().apply {
                    this.name = name
                    this.url  = lic
                })
            }
            playlist.drmLicenses = drmLicenses

            Log.d(TAG, "Converted ${channelsArray.size()} channels, ${categories.size} cats, ${drmLicenses.size} DRM")
            playlist

        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert Miyuki JSON: ${e.message}")
            null
        }
    }

    fun isMiyukiFormat(jsonString: String): Boolean {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            root.has("channels") && !root.has("categories")
        } catch (e: Exception) { false }
    }
}
