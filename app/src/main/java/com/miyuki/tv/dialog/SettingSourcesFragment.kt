package com.miyuki.tv.dialog

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import com.miyuki.tv.R
import com.miyuki.tv.adapter.SourcesAdapter
import com.miyuki.tv.databinding.SettingSourcesFragmentBinding
import com.miyuki.tv.extension.isLinkUrl
import com.miyuki.tv.extra.SourceChecker
import com.miyuki.tv.model.Source

class SettingSourcesFragment : Fragment() {

    companion object {
        var sources: ArrayList<Source>? = null
    }

    private var filePicker: FilePickerDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = SettingSourcesFragmentBinding.inflate(inflater, container, false)

        val adapter = SourcesAdapter(sources)
        binding.sourcesAdapter = adapter
        binding.rvSources.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )

        // SAF file picker - tidak perlu library pihak ketiga
        filePicker = FilePickerDialog(this).apply {
            setDialogSelectionListener { paths ->
                for (path in paths) {
                    adapter.addItem(Source().apply {
                        this.path = path
                        active = true
                    })
                }
                sources = adapter.getItems()
            }
        }

        binding.btnPick.setOnClickListener { filePicker?.show() }

        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        var clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""

        binding.inputSource.apply {
            setText(if (clipText.isLinkUrl()) clipText.trim() else "")
            setOnEditorActionListener { _, i, k ->
                if (i == EditorInfo.IME_ACTION_DONE ||
                    k?.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    binding.btnAdd.performClick(); true
                } else false
            }
        }

        binding.btnAdd.setOnClickListener {
            var input = binding.inputSource.text.toString()
            if (input.isBlank()) {
                clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (clipText.isLinkUrl()) input = clipText else return@setOnClickListener
            } else if (!input.isLinkUrl()) return@setOnClickListener

            it.isEnabled = false
            binding.inputSource.isEnabled = false
            binding.inputSource.setText(R.string.checking_url)

            val source = Source().apply { path = input; active = true }
            SourceChecker().set(source, object : SourceChecker.Result {
                override fun onCheckResult(result: Boolean) {
                    it.isEnabled = true
                    binding.inputSource.text?.clear()
                    binding.inputSource.isEnabled = true
                    if (result) {
                        adapter.addItem(source)
                        sources = adapter.getItems()
                    } else {
                        binding.inputSource.setText(input)
                        Toast.makeText(context, R.string.link_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }).run()
        }

        return binding.root
    }
}
