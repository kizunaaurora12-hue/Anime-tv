package com.miyuki.tv.extension

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.miyuki.tv.extra.M3uTool
import com.miyuki.tv.extra.MiyukiJsonConverter
import com.miyuki.tv.model.*

// ── String extensions ─────────────────────────────────────────────────────────
fun String?.toPlaylist(): Playlist? {
    if (this.isNullOrBlank()) return null
    // 1. Try MiyukiTV channels.json format
    try {
        if (MiyukiJsonConverter.isMiyukiFormat(this)) {
            val r = MiyukiJsonConverter.convert(this)
            if (r != null && !r.isCategoriesEmpty()) return r
        }
    } catch (e: Exception) { e.printStackTrace() }
    // 2. Try standard Playlist JSON
    try { return Gson().fromJson(this, Playlist::class.java) }
    catch (e: JsonParseException) { e.printStackTrace() }
    // 3. Try M3U
    try { return M3uTool.parse(this).toPlaylist() }
    catch (e: Exception) { e.printStackTrace() }
    return null
}

// ── List<M3U> → Playlist ─────────────────────────────────────────────────────
fun List<M3U>?.toPlaylist(): Playlist? {
    if (this == null) return null
    val playlist  = Playlist()
    val linkedMap = LinkedHashMap<String, ArrayList<Channel>>()
    val hashSet   = HashSet<String>()
    val drms      = ArrayList<DrmLicense>()
    val cats      = ArrayList<Category>()

    for (item in this) {
        for (i in item.streamUrl!!.indices) {
            if (!item.licenseKey.isNullOrEmpty()) {
                if (hashSet.none { it == item.licenseName }) {
                    hashSet.add(item.licenseName ?: "")
                    drms.add(DrmLicense().apply {
                        name = item.licenseName
                        url  = item.licenseKey
                    })
                }
            }
            val map = linkedMap.getOrPut(item.groupName.toString()) { ArrayList() }
            map.add(Channel().apply {
                name      = if (i > 0) "${item.channelName} #$i" else item.channelName
                streamUrl = item.streamUrl!![i]
                drmName   = item.licenseName
            })
        }
    }

    for ((key, channels) in linkedMap) {
        cats.add(Category().apply {
            name           = key
            this.channels  = channels
        })
    }
    playlist.categories  = cats
    playlist.drmLicenses = drms
    return playlist
}

// ── Playlist extensions ───────────────────────────────────────────────────────
fun Playlist?.isCategoriesEmpty(): Boolean =
    this?.categories?.isEmpty() == true

fun Playlist?.sortCategories() {
    this?.categories?.sortBy { it.name?.lowercase() }
}

fun Playlist?.sortChannels() {
    if (this == null) return
    for (cat in this.categories) cat.channels?.sortBy { it.name?.lowercase() }
}

fun Playlist?.trimChannelWithEmptyStreamUrl() {
    if (this == null) return
    for (cat in this.categories) cat.channels?.removeAll { it.streamUrl.isNullOrBlank() }
}

fun Playlist?.mergeWith(other: Playlist?) {
    if (other == null) return
    for (incomingCat in other.categories) {
        val existing = this?.categories?.firstOrNull {
            it.name?.trim()?.lowercase() == incomingCat.name?.trim()?.lowercase()
        }
        if (existing != null) existing.channels?.addAll(incomingCat.channels ?: ArrayList())
        else this?.categories?.add(incomingCat)
    }
    for (incomingDrm in other.drmLicenses) {
        if (this?.drmLicenses?.none { it.name == incomingDrm.name } == true)
            this.drmLicenses.add(incomingDrm)
    }
}

fun Playlist?.insertFavorite(channels: ArrayList<Channel>) {
    if (this == null) return
    if (this.categories.isNotEmpty() && this.categories[0].isFavorite())
        this.categories[0].channels = channels
    else
        this.categories.addFavorite(channels)
}

fun Playlist?.removeFavorite() {
    if (this == null) return
    if (this.categories.isNotEmpty() && this.categories[0].isFavorite())
        this.categories.removeAt(0)
}

// ── Category extensions ───────────────────────────────────────────────────────
fun Category?.isFavorite(): Boolean =
    this?.name?.lowercase()?.contains("favorit") == true ||
    this?.name?.lowercase()?.contains("favorite") == true

fun ArrayList<Category>.addFavorite(channels: ArrayList<Channel>) {
    add(0, Category().apply {
        name           = "\u2605 Favorit"
        this.channels  = channels
    })
}

fun Favorites?.trimNotExistFrom(playlist: Playlist?): Favorites {
    val fav = Favorites()
    if (this == null || playlist == null) return fav
    val allChannels = playlist.categories.flatMap { it.channels ?: ArrayList() }
    for (ch in this.channels) {
        if (allChannels.any { it.name == ch.name && it.streamUrl == ch.streamUrl })
            fav.channels.add(ch)
    }
    return fav
}

