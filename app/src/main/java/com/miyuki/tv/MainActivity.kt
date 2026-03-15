package com.miyuki.tv

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.miyuki.tv.adapter.CategoryAdapter
import com.miyuki.tv.adapter.SidebarAdapter
import com.miyuki.tv.databinding.ActivityMainBinding
import com.miyuki.tv.dialog.SearchDialog
import com.miyuki.tv.dialog.SettingDialog
import com.miyuki.tv.extension.isCategoriesEmpty
import com.miyuki.tv.extension.insertFavorite
import com.miyuki.tv.extension.removeFavorite
import com.miyuki.tv.extension.mergeWith
import com.miyuki.tv.extension.sortCategories
import com.miyuki.tv.extension.sortChannels
import com.miyuki.tv.extension.trimChannelWithEmptyStreamUrl
import com.miyuki.tv.extension.trimNotExistFrom
import com.miyuki.tv.extension.setFullScreenFlags
import com.miyuki.tv.extra.*
import com.miyuki.tv.model.*
import java.text.SimpleDateFormat
import java.util.*

open class MainActivity : AppCompatActivity() {

    private var doubleBackToExitPressedOnce = false
    private val isTelevision = UiMode().isTelevision()
    private val preferences  = Preferences()
    private val helper       = PlaylistHelper()
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CategoryAdapter
    private var sidebarAdapter: SidebarAdapter? = null

    private val clockHandler  = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

    // Localised display names for sidebar header
    private val catDisplayNames = mapOf(
        "nasional"      to "NASIONAL",
        "berita"        to "BERITA",
        "hiburan"       to "HIBURAN",
        "olahraga"      to "OLAHRAGA",
        "internasional" to "INTERNASIONAL",
        "jepang"        to "JEPANG",
        "vision"        to "VISION+ DRM",
        "indihome"      to "INDIHOME DRM",
        "custom"        to "CUSTOM",
        "favorit"       to "\u2605 FAVORIT",
        "favorite"      to "\u2605 FAVORIT"
    )

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.getStringExtra(MAIN_CALLBACK)) {
                UPDATE_PLAYLIST -> updatePlaylist(false)
                INSERT_FAVORITE -> adapter.insertOrUpdateFavorite()
                REMOVE_FAVORITE -> adapter.removeFavorite()
            }
        }
    }

    companion object {
        const val MAIN_CALLBACK  = "MAIN_CALLBACK"
        const val UPDATE_PLAYLIST = "UPDATE_PLAYLIST"
        const val INSERT_FAVORITE = "REFRESH_FAVORITE"
        const val REMOVE_FAVORITE = "REMOVE_FAVORITE"
    }

    override fun attachBaseContext(base: android.content.Context) {
        val lang = LocaleHelper.getLanguageCode(base)
        super.attachBaseContext(LocaleHelper.setLocale(base, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startClock()

        binding.buttonSearch.setOnClickListener   { openSearch() }
        binding.buttonRefresh.setOnClickListener  { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener { openSettings() }
        binding.buttonExit.setOnClickListener     { finish() }
        binding.searchHint?.setOnClickListener    { openSearch() }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))

        if (!Playlist.cached.isCategoriesEmpty())
            setPlaylistToAdapter(Playlist.cached)
        else
            showAlertPlaylistError(getString(R.string.null_playlist))
    }

    // ── Loading state ─────────────────────────────────────────────────────────
    private fun setLoadingPlaylist(show: Boolean) {
        if (show) {
            binding.loading.startShimmer()
            binding.loading.visibility = View.VISIBLE
        } else {
            binding.loading.stopShimmer()
            binding.loading.visibility = View.GONE
        }
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────
    private fun setupSidebar(playlistSet: Playlist) {
        sidebarAdapter = SidebarAdapter(playlistSet.categories) { cat, position ->
            val key          = cat.name?.lowercase()?.trim() ?: ""
            val matchedTitle = catDisplayNames.entries
                .firstOrNull { key.contains(it.key) }?.value
                ?: cat.name?.uppercase() ?: ""
            binding.textCurrentCat?.text = matchedTitle

            val catIndex = playlistSet.categories.indexOf(cat)
            adapter.showCategory(if (catIndex >= 0) catIndex else position)
            binding.rvCategory.scrollToPosition(0)
        }
        binding.rvSidebar.layoutManager = LinearLayoutManager(this)
        binding.rvSidebar.adapter       = sidebarAdapter
        adapter.showCategory(0)
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    @SuppressLint("SetTextI18n")
    private fun updateStats(playlistSet: Playlist) {
        try {
            val total       = playlistSet.categories.sumOf { it.channels?.size ?: 0 }
            val drmCount    = playlistSet.drmLicenses.size
            val customCount = playlistSet.categories
                .filter { it.name?.lowercase()?.contains("custom") == true }
                .sumOf { it.channels?.size ?: 0 }

            binding.statTotal?.text  = total.toString()
            binding.statDrm?.text    = drmCount.toString()
            binding.statCustom?.text = customCount.toString()

            playlistSet.categories.firstOrNull()?.let { firstCat ->
                val key   = firstCat.name?.lowercase()?.trim() ?: ""
                val title = catDisplayNames.entries
                    .firstOrNull { key.contains(it.key) }?.value
                    ?: firstCat.name?.uppercase() ?: ""
                binding.textCurrentCat?.text = title
            }
        } catch (e: Exception) { /* ignore */ }
    }

    // ── Set playlist ──────────────────────────────────────────────────────────
    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        if (preferences.sortCategory) playlistSet.sortCategories()
        if (preferences.sortChannel)  playlistSet.sortChannels()
        playlistSet.trimChannelWithEmptyStreamUrl()

        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (preferences.sortFavorite) fav.sort()
        if (fav.channels.isNotEmpty())
            playlistSet.insertFavorite(fav.channels)
        else
            playlistSet.removeFavorite()

        adapter             = CategoryAdapter(playlistSet.categories)
        binding.catAdapter  = adapter
        Playlist.cached     = playlistSet
        helper.writeCache(playlistSet)

        setupSidebar(playlistSet)
        updateStats(playlistSet)
        setLoadingPlaylist(false)
        Toast.makeText(applicationContext, R.string.playlist_updated, Toast.LENGTH_SHORT).show()

        if (preferences.playLastWatched && PlayerActivity.isFirst) {
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayData.VALUE, preferences.watched)
            )
        }
    }

    // ── Update playlist ───────────────────────────────────────────────────────
    private fun updatePlaylist(useCache: Boolean) {
        setLoadingPlaylist(true)
        binding.catAdapter?.clear()
        val playlistSet = Playlist()

        SourcesReader().set(preferences.sources, object : SourcesReader.Result {
            override fun onError(source: String, error: String) {
                val snackbar = Snackbar.make(
                    binding.root,
                    "[${error.uppercase()}] $source",
                    Snackbar.LENGTH_INDEFINITE
                )
                snackbar.setAction(android.R.string.ok) { snackbar.dismiss() }
                snackbar.show()
            }
            override fun onResponse(playlist: Playlist?) {
                if (playlist != null) playlistSet.mergeWith(playlist)
                else Toast.makeText(
                    applicationContext,
                    R.string.playlist_cant_be_parsed,
                    Toast.LENGTH_SHORT
                ).show()
            }
            override fun onFinish() {
                if (!playlistSet.isCategoriesEmpty()) setPlaylistToAdapter(playlistSet)
                else showAlertPlaylistError(getString(R.string.null_playlist))
            }
        }).process(useCache)
    }

    private fun showAlertPlaylistError(message: String?) {
        val alert = AlertDialog.Builder(this).apply {
            setTitle(R.string.alert_title_playlist_error)
            setMessage(message)
            setCancelable(false)
            setNeutralButton(R.string.settings)     { _, _ -> openSettings() }
            setPositiveButton(R.string.dialog_retry) { _, _ -> updatePlaylist(true) }
        }
        val cache = helper.readCache()
        if (cache != null) {
            alert.setNegativeButton(R.string.dialog_cached) { _, _ ->
                setPlaylistToAdapter(cache)
            }
        }
        alert.create().show()
    }

    // ── Clock ─────────────────────────────────────────────────────────────────
    private fun startClock() { clockHandler.post(clockRunnable) }

    @SuppressLint("DefaultLocale")
    private fun updateClock() {
        val now        = Calendar.getInstance()
        val timeFmt    = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt    = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
        binding.textClock?.text = timeFmt.format(now.time)
        binding.textDate?.text  = dateFmt.format(now.time)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun openSettings() = SettingDialog().show(supportFragmentManager.beginTransaction(), null)
    private fun openSearch()   = SearchDialog().show(supportFragmentManager.beginTransaction(), null)

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.setFullScreenFlags()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { openSettings(); return true }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        if (isTelevision || doubleBackToExitPressedOnce) {
            super.onBackPressed(); finish(); return
        }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_twice_exit_app), Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockRunnable)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }
}
