package com.miyuki.tv.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.miyuki.tv.BR
import com.miyuki.tv.R
import com.miyuki.tv.databinding.ItemCategoryBinding
import com.miyuki.tv.extension.addFavorite
import com.miyuki.tv.extension.isFavorite
import com.miyuki.tv.extra.Preferences
import com.miyuki.tv.model.Category
import com.miyuki.tv.model.Channel
import com.miyuki.tv.model.Playlist

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    lateinit var context: Context
    private var selectedCatIndex: Int = 0

    private val displayList: ArrayList<Category>
        get() = if (selectedCatIndex >= 0 && selectedCatIndex < (categories?.size ?: 0))
            arrayListOf(categories!![selectedCatIndex])
        else
            categories ?: arrayListOf()

    class ViewHolder(val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(obj: Any?) {
            binding.setVariable(BR.catModel, obj)
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.item_category, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category      = displayList.getOrNull(position) ?: return
        val catIndexInFull = categories?.indexOf(category) ?: position
        val isFav         = category.isFavorite() && catIndexInFull == 0

        holder.binding.chAdapter = ChannelAdapter(category.channels, catIndexInFull, isFav)
        holder.binding.rvChannels.layoutManager = GridLayoutManager(context, 5)

        try { holder.binding.txtCount?.text = (category.channels?.size ?: 0).toString() }
        catch (e: Exception) { /* ignore */ }

        holder.bind(category)
    }

    override fun getItemCount() = displayList.size

    fun showCategory(index: Int) {
        selectedCatIndex = index
        notifyDataSetChanged()
    }

    fun clear() {
        val size = itemCount
        categories?.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun insertOrUpdateFavorite() {
        val fav = Playlist.favorites
        if (Preferences().sortFavorite) fav.sort()
        if (categories?.getOrNull(0)?.isFavorite() == false) {
            categories.addFavorite(fav.channels)
            notifyDataSetChanged()
        } else {
            categories?.getOrNull(0)?.channels = fav.channels
            notifyDataSetChanged()
        }
    }

    fun removeFavorite() {
        if (categories?.getOrNull(0)?.isFavorite() == true) {
            categories.removeAt(0)
            notifyDataSetChanged()
        }
    }
}
