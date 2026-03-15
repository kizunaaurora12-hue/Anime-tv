package com.miyuki.tv.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.miyuki.tv.R
import com.miyuki.tv.adapter.SearchAdapter
import com.miyuki.tv.databinding.SearchDialogBinding
import com.miyuki.tv.extension.isFavorite
import com.miyuki.tv.model.Channel
import com.miyuki.tv.model.PlayData
import com.miyuki.tv.model.Playlist

class SearchDialog : DialogFragment() {

    private var _binding: SearchDialogBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchAdapter: SearchAdapter

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AppCompatDialog(activity, R.style.SettingsDialogThemeOverlay).apply {
            setTitle(R.string.search_channel)
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SearchDialogBinding.inflate(inflater, container, false)

        val channels    = ArrayList<Channel>()
        val playDataList = ArrayList<PlayData>()
        val playlist    = Playlist.cached

        for (catId in playlist.categories.indices) {
            val cat = playlist.categories[catId]
            if (catId == 0 && cat.isFavorite()) continue
            val ch = cat.channels ?: continue
            for (chId in ch.indices) {
                channels.add(ch[chId])
                playDataList.add(PlayData(catId, chId))
            }
        }

        searchAdapter = SearchAdapter(channels, playDataList)
        binding.rvSearch.layoutManager = GridLayoutManager(context, 4)
        binding.rvSearch.adapter       = searchAdapter

        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchAdapter.filter(s?.toString() ?: "")
            }
        })

        binding.btnClose.setOnClickListener { dismiss() }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
