package com.miyuki.tv.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.miyuki.tv.MainActivity
import com.miyuki.tv.R
import com.miyuki.tv.databinding.SettingDialogBinding
import com.miyuki.tv.extra.Preferences

class SettingDialog : DialogFragment() {

    val preferences = Preferences()
    private val tabFragment = arrayOf(
        SettingSourcesFragment(), SettingAppFragment(), SettingAboutFragment()
    )
    private val tabTitle = arrayOf(
        R.string.tab_sources, R.string.tab_app, R.string.tab_about
    )
    private var revertCountryId = ""
    private var isCancelled     = true

    companion object {
        var isSourcesChanged = false
    }

    @Suppress("DEPRECATION")
    inner class FragmentAdapter(fragmentManager: FragmentManager?) :
        FragmentPagerAdapter(fragmentManager!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int): Fragment = tabFragment[position]
        override fun getCount(): Int = tabFragment.size
        override fun getPageTitle(position: Int): CharSequence = getString(tabTitle[position])
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AppCompatDialog(activity, R.style.SettingsDialogThemeOverlay).apply {
            setTitle(R.string.settings)
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = SettingDialogBinding.inflate(inflater, container, false)

        isSourcesChanged = false
        SettingAppFragment.launchAtBoot     = preferences.launchAtBoot
        SettingAppFragment.playLastWatched  = preferences.playLastWatched
        SettingAppFragment.sortFavorite     = preferences.sortFavorite
        SettingAppFragment.sortCategory     = preferences.sortCategory
        SettingAppFragment.sortChannel      = preferences.sortChannel
        SettingAppFragment.optimizePrebuffer = preferences.optimizePrebuffer
        SettingAppFragment.reverseNavigation = preferences.reverseNavigation
        SettingSourcesFragment.sources      = preferences.sources
        revertCountryId                     = preferences.countryId

        binding.settingViewPager.adapter = FragmentAdapter(childFragmentManager)
        binding.settingTabLayout.setupWithViewPager(binding.settingViewPager)

        binding.settingCancelButton.setOnClickListener { dismiss() }
        binding.settingOkButton.setOnClickListener {
            isCancelled = false
            preferences.launchAtBoot      = SettingAppFragment.launchAtBoot
            preferences.playLastWatched   = SettingAppFragment.playLastWatched
            preferences.sortFavorite      = SettingAppFragment.sortFavorite
            preferences.sortCategory      = SettingAppFragment.sortCategory
            preferences.sortChannel       = SettingAppFragment.sortChannel
            preferences.optimizePrebuffer = SettingAppFragment.optimizePrebuffer
            preferences.reverseNavigation = SettingAppFragment.reverseNavigation
            val sources = SettingSourcesFragment.sources
            if (sources?.filter { s -> s.active }?.isEmpty() == true) {
                sources[0].active = true
                Toast.makeText(context, R.string.warning_none_source_active, Toast.LENGTH_SHORT).show()
            }
            preferences.sources = sources
            dismiss()
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isCancelled) preferences.countryId = revertCountryId
        else if (isSourcesChanged) sendUpdatePlaylist(requireContext())
    }

    private fun sendUpdatePlaylist(context: Context) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(
            Intent(MainActivity.MAIN_CALLBACK)
                .putExtra(MainActivity.MAIN_CALLBACK, MainActivity.UPDATE_PLAYLIST)
        )
    }
}
