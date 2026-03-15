package com.miyuki.tv.dialog

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.miyuki.tv.BuildConfig
import com.miyuki.tv.R
import com.miyuki.tv.databinding.SettingAboutFragmentBinding
import com.miyuki.tv.extra.Preferences

class SettingAboutFragment : Fragment() {

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = SettingAboutFragmentBinding.inflate(inflater, container, false)

        binding.textVersion.text = "MiyukiTV v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        binding.textUsers.text   = Preferences().contributors

        binding.buttonWebsite.setOnClickListener  { openLink(getString(R.string.website)) }
        binding.buttonTelegram.setOnClickListener { openLink(getString(R.string.telegram_group)) }

        return binding.root
    }

    private fun openLink(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(url)))
    }
}
