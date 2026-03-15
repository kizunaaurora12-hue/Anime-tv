package com.miyuki.tv.adapter

import com.miyuki.tv.model.Channel

interface ChannelClickListener {
    fun onClicked(ch: Channel, catId: Int, chId: Int)
    fun onLongClicked(ch: Channel, catId: Int, chId: Int): Boolean
}
