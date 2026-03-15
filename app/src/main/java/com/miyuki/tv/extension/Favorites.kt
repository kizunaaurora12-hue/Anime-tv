package com.miyuki.tv.extension

import com.miyuki.tv.extra.PlaylistHelper
import com.miyuki.tv.model.Favorites

fun Favorites.save() {
    PlaylistHelper().writeFavorites(this)
}
