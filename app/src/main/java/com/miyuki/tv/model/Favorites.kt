package com.miyuki.tv.model

class Favorites {
    var channels: ArrayList<Channel> = ArrayList()

    fun insert(channel: Channel): Boolean {
        if (channels.any { it.name == channel.name && it.streamUrl == channel.streamUrl }) return false
        channels.add(channel)
        return true
    }

    fun remove(channel: Channel) {
        channels.removeAll { it.name == channel.name && it.streamUrl == channel.streamUrl }
    }

    fun sort() {
        channels.sortBy { it.name?.lowercase() }
    }

    fun save() {
        Playlist.favorites = this
    }
}
