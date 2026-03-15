package com.miyuki.tv.adapter

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.miyuki.tv.R
import com.miyuki.tv.dialog.SettingDialog
import com.miyuki.tv.model.Source

class SourcesAdapter(private var sources: ArrayList<Source>?) :
    RecyclerView.Adapter<SourcesAdapter.ViewHolder>() {

    lateinit var context: Context

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtPath:  TextView  = view.findViewById(R.id.source_path)
        val chkActive: CheckBox = view.findViewById(R.id.source_active)
        val btnRemove: ImageButton = view.findViewById(R.id.source_remove)
        val btnCopy: ImageButton   = view.findViewById(R.id.source_copy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.item_source, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val source = sources?.get(position) ?: return
        val isDefault = position == 0

        holder.txtPath.text  = source.path
        holder.chkActive.isChecked = source.active

        holder.chkActive.setOnCheckedChangeListener { _, isChecked ->
            source.active = isChecked
            SettingDialog.isSourcesChanged = true
        }

        holder.btnRemove.apply {
            visibility = if (isDefault) View.INVISIBLE else View.VISIBLE
            setOnClickListener {
                sources?.removeAt(holder.adapterPosition)
                notifyItemRemoved(holder.adapterPosition)
                SettingDialog.isSourcesChanged = true
            }
        }

        holder.btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("source", source.path))
            Toast.makeText(context, R.string.link_copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = sources?.size ?: 0

    fun addItem(source: Source) {
        if (sources == null) sources = ArrayList()
        sources?.add(source)
        notifyItemInserted(sources!!.size - 1)
        SettingDialog.isSourcesChanged = true
    }

    fun getItems(): ArrayList<Source> = sources ?: ArrayList()
}
