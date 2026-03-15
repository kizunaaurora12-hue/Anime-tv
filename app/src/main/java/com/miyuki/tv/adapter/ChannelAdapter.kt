package com.miyuki.tv.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.miyuki.tv.BR
import com.miyuki.tv.MainActivity
import com.miyuki.tv.PlayerActivity
import com.miyuki.tv.R
import com.miyuki.tv.databinding.ItemChannelBinding
import com.miyuki.tv.extension.startAnimation
import com.miyuki.tv.model.Channel
import com.miyuki.tv.model.PlayData
import com.miyuki.tv.model.Playlist

class ChannelAdapter(
    val channels: ArrayList<Channel>?,
    private val catId: Int,
    private val isFav: Boolean
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>(), ChannelClickListener {

    lateinit var context: Context

    class ViewHolder(val binding: ItemChannelBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(obj: Any?) {
            binding.setVariable(BR.modelChannel, obj)
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemChannelBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.item_channel, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels?.get(position) ?: return
        holder.bind(channel)
        holder.binding.catId = catId
        holder.binding.chId  = position
        holder.binding.clickListener = this

        holder.binding.btnPlay.setOnFocusChangeListener { v, hasFocus ->
            v.startAnimation(hasFocus)
        }

        val imgLogo = holder.binding.imgLogo
        val txtLogo = holder.binding.txtLogo
        val logoUrl = channel.logo

        if (!logoUrl.isNullOrBlank()) {
            Glide.with(context)
                .load(logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.transparent)
                .error(android.R.color.transparent)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        imgLogo.visibility = View.GONE
                        txtLogo.visibility = View.VISIBLE
                        return false
                    }
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        imgLogo.visibility = View.VISIBLE
                        txtLogo.visibility = View.GONE
                        return false
                    }
                })
                .into(imgLogo)
        } else {
            imgLogo.visibility = View.GONE
            txtLogo.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = channels?.size ?: 0

    override fun onClicked(ch: Channel, catId: Int, chId: Int) {
        // Resolve real catId/chId from cached playlist (object identity)
        val realCatId = Playlist.cached.categories.indexOfFirst { cat ->
            cat.channels?.any { it === ch } == true
        }.let { if (it == -1) catId else it }

        val realChId = Playlist.cached.categories.getOrNull(realCatId)
            ?.channels?.indexOfFirst { it === ch } ?: chId

        val intent = Intent(context, PlayerActivity::class.java)
        intent.putExtra(PlayData.VALUE, PlayData(realCatId, realChId))
        context.startActivity(intent)
    }

    override fun onLongClicked(ch: Channel, catId: Int, chId: Int): Boolean {
        val fav = Playlist.favorites
        if (isFav) {
            channels?.remove(ch)
            fav.remove(ch)
            if (itemCount != 0) {
                notifyItemRemoved(chId)
                notifyItemRangeChanged(0, itemCount)
            } else sendBroadcast(false)
            Toast.makeText(
                context,
                String.format(context.getString(R.string.removed_from_favorite), ch.name),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val result = fav.insert(ch)
            if (result) sendBroadcast(true)
            val message = if (result)
                String.format(context.getString(R.string.added_into_favorite), ch.name)
            else
                String.format(context.getString(R.string.already_in_favorite), ch.name)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        fav.save()
        return true
    }

    private fun sendBroadcast(isInserted: Boolean) {
        val callback = if (isInserted) MainActivity.INSERT_FAVORITE else MainActivity.REMOVE_FAVORITE
        LocalBroadcastManager.getInstance(context).sendBroadcast(
            Intent(MainActivity.MAIN_CALLBACK)
                .putExtra(MainActivity.MAIN_CALLBACK, callback)
        )
    }
}
