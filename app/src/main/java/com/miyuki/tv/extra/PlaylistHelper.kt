package com.miyuki.tv.extra

import android.util.Log
import com.google.gson.Gson
import com.miyuki.tv.App
import com.miyuki.tv.model.Favorites
import com.miyuki.tv.model.Playlist
import java.io.File

class PlaylistHelper {
    private val context  = App.context
    private val cache    = File(context.cacheDir,  "MiyukiTV.json")
    private val favorite = File(context.filesDir,  "Favorite.json")

    companion object { private const val TAG = "PlaylistHelper" }

    fun writeCache(playlist: Playlist) {
        try { cache.writeText(Gson().toJson(playlist)) }
        catch (e: Exception) { Log.e(TAG, "Could not write cache", e) }
    }

    fun readCache(): Playlist? = readFile(cache)

    fun readFile(file: File): Playlist? {
        return try { file.readText(Charsets.UTF_8).toPlaylist() }
        catch (e: Exception) { null }
    }

    fun writeFavorites(fav: Favorites) {
        try {
            Playlist.favorites = fav
            favorite.writeText(Gson().toJson(fav))
        } catch (e: Exception) { Log.e(TAG, "Could not write favorites", e) }
    }

    fun readFavorites(): Favorites {
        val newFav = Favorites()
        return try {
            if (favorite.exists()) {
                val fav = Gson().fromJson(favorite.readText(Charsets.UTF_8), Favorites::class.java)
                Playlist.favorites = fav
                fav
            } else {
                writeFavorites(newFav)
                newFav
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read favorites", e)
            newFav
        }
    }

    private fun String.toPlaylist(): Playlist? {
        return com.miyuki.tv.extension.toPlaylist(this)
    }
}
