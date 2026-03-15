package com.miyuki.tv.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.miyuki.tv.PlayerActivity
import com.miyuki.tv.R
import com.miyuki.tv.model.Channel
import com.miyuki.tv.model.PlayData

class SearchAdapter(
    private val channels: ArrayList<Channel>,
    private val playDataList: ArrayList<PlayData>
) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

    private var filtered: ArrayList<Int> = ArrayList(channels.indices.toList())
    lateinit var context: Context

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgLogo: ImageView = view.findViewById(R.id.search_img_logo)
        val txtLogo: TextView  = view.findViewById(R.id.search_txt_logo)
        val txtName: TextView  = view.findViewById(R.id.search_txt_name)
        val root: View         = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.item_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val idx     = filtered[position]
        val channel = channels[idx]

        holder.txtName.text = channel.name

        val logoUrl = channel.logo
        if (!logoUrl.isNullOrBlank()) {
            Glide.with(context)
                .load(logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.transparent)
                .error(android.R.color.transparent)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(e: com.bumptech.glide.load.engine.GlideException?, model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean): Boolean {
                        holder.imgLogo.visibility = View.GONE
                        holder.txtLogo.visibility = View.VISIBLE
                        return false
                    }
                    override fun onResourceReady(resource: android.graphics.drawable.Drawable?, model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean): Boolean {
                        holder.imgLogo.visibility = View.VISIBLE
                        holder.txtLogo.visibility = View.GONE
                        return false
                    }
                })
                .into(holder.imgLogo)
        } else {
            holder.imgLogo.visibility = View.GONE
            holder.txtLogo.visibility = View.VISIBLE
            holder.txtLogo.text = channel.name?.take(1)?.uppercase() ?: "?"
        }

        holder.root.setOnClickListener {
            val pd = playDataList[idx]
            val intent = Intent(context, PlayerActivity::class.java)
            intent.putExtra(PlayData.VALUE, pd)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = filtered.size

    fun filter(query: String) {
        filtered = if (query.isBlank()) {
            ArrayList(channels.indices.toList())
        } else {
            val q = query.lowercase()
            ArrayList(channels.indices.filter {
                channels[it].name?.lowercase()?.contains(q) == true
            })
        }
        notifyDataSetChanged()
    }
}
