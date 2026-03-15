package com.miyuki.tv.extra

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.miyuki.tv.App
import com.miyuki.tv.model.Favorites
import com.miyuki.tv.model.Playlist
import java.io.File

class PlaylistHelper {
    private val context  = App.context
    private val cache    = File(context.cacheDir, "MiyukiTV.json")
    private val favorite = File(context.filesDir, "Favorite.json")

    companion object { private const val TAG = "PlaylistHelper" }

    fun writeCache(playlist: Playlist) {
        try { cache.writeText(Gson().toJson(playlist)) }
        catch (e: Exception) { Log.e(TAG, "Could not write cache", e) }
    }

    fun readCache(): Playlist? = readFile(cache)

    fun readFile(file: File): Playlist? {
        return try {
            val text = file.readText(Charsets.UTF_8)
            com.miyuki.tv.extension.toPlaylist(text)
        } catch (e: Exception) { null }
    }

    /** Baca dari content:// URI (SAF) atau path file biasa */
    fun readFromPath(path: String): Playlist? {
        return try {
            val text = if (path.startsWith("content://")) {
                readFromContentUri(path)
            } else {
                File(path).readText(Charsets.UTF_8)
            }
            com.miyuki.tv.extension.toPlaylist(text)
        } catch (e: Exception) {
            Log.e(TAG, "Could not read from path: $path", e)
            null
        }
    }

    private fun readFromContentUri(uriString: String): String {
        val uri = Uri.parse(uriString)
        val cr: ContentResolver = context.contentResolver
        return cr.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw Exception("Cannot open content URI: $uriString")
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
}
