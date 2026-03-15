package com.miyuki.tv.dialog

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.miyuki.tv.R
import com.miyuki.tv.databinding.SettingAppFragmentBinding
import com.miyuki.tv.extra.LocaleHelper

class SettingAppFragment : Fragment() {

    companion object {
        var launchAtBoot      = false
        var playLastWatched   = false
        var sortFavorite      = false
        var sortCategory      = false
        var sortChannel       = true
        var optimizePrebuffer = true
        var reverseNavigation = false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = SettingAppFragmentBinding.inflate(inflater, container, false)

        // Language
        val languageCodes = arrayOf("in", "en", "ja", "ms")
        val languageNames = arrayOf(
            getString(R.string.language_id),
            getString(R.string.language_en),
            getString(R.string.language_ja),
            getString(R.string.language_ms)
        )
        val currentCode  = LocaleHelper.getLanguageCode(requireContext())
        val currentIndex = languageCodes.indexOf(currentCode).takeIf { it >= 0 } ?: 0
        binding.textCurrentLanguage.text = languageNames[currentIndex]

        binding.languageSelector.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.select_language))
                .setSingleChoiceItems(languageNames, currentIndex) { dialog, which ->
                    val selectedCode = languageCodes[which]
                    if (selectedCode != currentCode) {
                        LocaleHelper.saveLanguageCode(requireContext(), selectedCode)
                        dialog.dismiss()
                        val intent = requireActivity().packageManager
                            .getLaunchIntentForPackage(requireActivity().packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        requireActivity().finishAffinity()
                        startActivity(intent)
                    } else dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.cancel_button_label), null)
                .show()
        }

        // Toggles
        binding.switchLaunchAtBoot.isChecked     = launchAtBoot
        binding.switchPlayLastWatched.isChecked  = playLastWatched
        binding.switchSortFavorite.isChecked     = sortFavorite
        binding.switchSortCategory.isChecked     = sortCategory
        binding.switchSortChannel.isChecked      = sortChannel
        binding.switchOptimizePrebuffer.isChecked = optimizePrebuffer
        binding.switchReverseNavigation.isChecked = reverseNavigation

        binding.switchLaunchAtBoot.setOnCheckedChangeListener     { _, v -> launchAtBoot      = v }
        binding.switchPlayLastWatched.setOnCheckedChangeListener  { _, v -> playLastWatched   = v }
        binding.switchSortFavorite.setOnCheckedChangeListener     { _, v -> sortFavorite      = v }
        binding.switchSortCategory.setOnCheckedChangeListener     { _, v -> sortCategory      = v }
        binding.switchSortChannel.setOnCheckedChangeListener      { _, v -> sortChannel       = v }
        binding.switchOptimizePrebuffer.setOnCheckedChangeListener { _, v -> optimizePrebuffer = v }
        binding.switchReverseNavigation.setOnCheckedChangeListener { _, v -> reverseNavigation = v }

        return binding.root
    }
}
