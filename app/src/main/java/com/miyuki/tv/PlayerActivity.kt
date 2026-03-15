package com.miyuki.tv

import android.app.AlertDialog
import android.content.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaDrm
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.MimeTypes
import com.miyuki.tv.databinding.ActivityPlayerBinding
import com.miyuki.tv.databinding.CustomControlBinding
import com.miyuki.tv.dialog.TrackSelectionDialog
import com.miyuki.tv.extension.findPattern
import com.miyuki.tv.extension.setFullScreenFlags
import com.miyuki.tv.extra.*
import com.miyuki.tv.model.Category
import com.miyuki.tv.model.Channel
import com.miyuki.tv.model.PlayData
import com.miyuki.tv.model.Playlist
import java.net.URLDecoder
import java.util.*

class PlayerActivity : AppCompatActivity() {

    private var doubleBackToExitPressedOnce = false
    private val isTelevision = UiMode().isTelevision()
    private val preferences  = Preferences()
    private var category: Category? = null
    private var current: Channel?   = null
    private var player: SimpleExoPlayer? = null
    private lateinit var mediaItem: MediaItem
    private lateinit var trackSelector: DefaultTrackSelector
    private var lastSeenTrackGroupArray: TrackGroupArray? = null
    private lateinit var bindingRoot: ActivityPlayerBinding
    private lateinit var bindingControl: CustomControlBinding
    private var handlerInfo: Handler? = null
    private var errorCounter = 0
    private var isLocked     = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.getStringExtra(PLAYER_CALLBACK)) {
                RETRY_PLAYBACK -> retryPlayback(true)
                CLOSE_PLAYER   -> finish()
            }
        }
    }

    companion object {
        var isFirst   = true
        var isPipMode = false
        const val PLAYER_CALLBACK  = "PLAYER_CALLBACK"
        const val RETRY_PLAYBACK   = "RETRY_PLAYBACK"
        const val CLOSE_PLAYER     = "CLOSE_PLAYER"
        private const val CHANNEL_NEXT     = 0
        private const val CHANNEL_PREVIOUS = 1
        private const val CATEGORY_UP      = 2
        private const val CATEGORY_DOWN    = 3
    }

    override fun attachBaseContext(base: android.content.Context) {
        val lang = LocaleHelper.getLanguageCode(base)
        super.attachBaseContext(LocaleHelper.setLocale(base, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        bindingRoot    = ActivityPlayerBinding.inflate(layoutInflater)
        bindingControl = CustomControlBinding.bind(
            bindingRoot.root.findViewById(R.id.custom_control)
        )
        setContentView(bindingRoot.root)
        isFirst = false

        if (Playlist.cached.categories.isEmpty()) {
            Toast.makeText(this, R.string.player_no_playlist, Toast.LENGTH_SHORT).show()
            finish(); return
        }

        try {
            val parcel: PlayData? = intent.getParcelableExtra(PlayData.VALUE)
            category = Playlist.cached.categories[parcel?.catId as Int]
            current  = category?.channels?.get(parcel.chId)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.player_playdata_error, Toast.LENGTH_SHORT).show()
            finish(); return
        }

        if (category == null || current == null) {
            Toast.makeText(this, R.string.player_no_channel, Toast.LENGTH_SHORT).show()
            finish(); return
        }

        bindingListener()
        playChannel()

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(PLAYER_CALLBACK))
    }

    // ── Binding listeners ─────────────────────────────────────────────────────
    private fun bindingListener() {
        bindingRoot.playerView.apply {
            setOnTouchListener(object : OnSwipeTouchListener() {
                override fun onSwipeDown()  { switchChannel(CATEGORY_UP) }
                override fun onSwipeUp()    { switchChannel(CATEGORY_DOWN) }
                override fun onSwipeLeft()  { switchChannel(CHANNEL_NEXT) }
                override fun onSwipeRight() { switchChannel(CHANNEL_PREVIOUS) }
            })
            setControllerVisibilityListener { setChannelInformation(it == View.VISIBLE) }
        }
        bindingControl.trackSelection.setOnClickListener { showTrackSelector() }
        bindingControl.buttonExit.apply {
            visibility = if (isTelevision) View.GONE else View.VISIBLE
            setOnClickListener { finish() }
        }
        bindingControl.buttonPrevious.setOnClickListener  { switchChannel(CHANNEL_PREVIOUS) }
        bindingControl.buttonRewind.setOnClickListener    { player?.seekBack() }
        bindingControl.buttonForward.setOnClickListener   { player?.seekForward() }
        bindingControl.buttonNext.setOnClickListener      { switchChannel(CHANNEL_NEXT) }
        bindingControl.screenMode.setOnClickListener      { showScreenMenu(it) }
        bindingControl.buttonLock.apply {
            visibility = if (isTelevision) View.GONE else View.VISIBLE
            setOnClickListener {
                if (!isLocked) {
                    (it as ImageButton).setImageResource(R.drawable.ic_lock)
                    lockControl(true)
                }
            }
            setOnLongClickListener {
                val resId = if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock
                (it as ImageButton).setImageResource(resId)
                lockControl(!isLocked); true
            }
        }
    }

    // ── Channel info overlay ──────────────────────────────────────────────────
    private fun setChannelInformation(visible: Boolean) {
        if (isLocked) return
        bindingRoot.layoutInfo.visibility =
            if (visible && !isPipMode) View.VISIBLE else View.INVISIBLE
        if (isPipMode) return
        if (visible == bindingRoot.playerView.isControllerVisible) return
        if (!visible) return

        if (handlerInfo == null) handlerInfo = Handler(Looper.getMainLooper())
        handlerInfo?.removeCallbacksAndMessages(null)
        handlerInfo?.postDelayed({
            if (bindingRoot.playerView.isControllerVisible) return@postDelayed
            bindingRoot.layoutInfo.visibility = View.INVISIBLE
        }, bindingRoot.playerView.controllerShowTimeoutMs.toLong())
    }

    private fun lockControl(setLocked: Boolean) {
        isLocked = setLocked
        val vis  = if (setLocked) View.INVISIBLE else View.VISIBLE
        bindingRoot.layoutInfo.visibility          = vis
        bindingControl.buttonExit.visibility       = vis
        bindingControl.layoutControl.visibility    = vis
        bindingControl.screenMode.visibility       = vis
        bindingControl.trackSelection.visibility   = vis
        switchLiveOrVideo()
    }

    private fun switchLiveOrVideo() = switchLiveOrVideo(false)
    private fun switchLiveOrVideo(reset: Boolean) {
        var vis = when {
            reset    -> View.GONE
            isLocked -> View.INVISIBLE
            player?.isCurrentWindowLive == true -> View.GONE
            else -> View.VISIBLE
        }
        bindingControl.layoutSeekbar.visibility = vis
        bindingControl.spacerControl.visibility = vis
        if (player?.isCurrentWindowSeekable == false) vis = View.GONE
        bindingControl.buttonRewind.visibility  = vis
        bindingControl.buttonForward.visibility = vis
    }

    // ── Channel switching ─────────────────────────────────────────────────────
    private fun switchChannel(direction: Int): Boolean {
        val cats = Playlist.cached.categories
        val catIdx = cats.indexOf(category)
        when (direction) {
            CHANNEL_NEXT -> {
                val chIdx = category?.channels?.indexOf(current) ?: return false
                if (chIdx + 1 < (category?.channels?.size ?: 0)) {
                    current = category?.channels?.get(chIdx + 1)
                } else {
                    val nextCat = (catIdx + 1) % cats.size
                    category = cats[nextCat]
                    current  = category?.channels?.firstOrNull()
                }
            }
            CHANNEL_PREVIOUS -> {
                val chIdx = category?.channels?.indexOf(current) ?: return false
                if (chIdx - 1 >= 0) {
                    current = category?.channels?.get(chIdx - 1)
                } else {
                    val prevCat = if (catIdx - 1 < 0) cats.size - 1 else catIdx - 1
                    category = cats[prevCat]
                    current  = category?.channels?.lastOrNull()
                }
            }
            CATEGORY_UP -> {
                val prevCat = if (catIdx - 1 < 0) cats.size - 1 else catIdx - 1
                category = cats[prevCat]
                current  = category?.channels?.firstOrNull()
            }
            CATEGORY_DOWN -> {
                val nextCat = (catIdx + 1) % cats.size
                category = cats[nextCat]
                current  = category?.channels?.firstOrNull()
            }
        }
        // Save last watched
        val realCatId = cats.indexOf(category)
        val realChId  = category?.channels?.indexOf(current) ?: 0
        if (realCatId >= 0) preferences.watched = PlayData(realCatId, realChId)
        playChannel()
        return true
    }

    // ── DRM helpers ───────────────────────────────────────────────────────────
    private fun hexToBytes(hex: String): ByteArray {
        val len  = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len / 2)
            data[i] = ((Character.digit(hex[i * 2], 16) shl 4) +
                        Character.digit(hex[i * 2 + 1], 16)).toByte()
        return data
    }

    private fun isDrmWidevineSupported(): Boolean {
        if (MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)) return true
        AlertDialog.Builder(this).apply {
            setTitle(R.string.player_playback_error)
            setMessage(R.string.device_not_support_widevine)
            setCancelable(false)
            setPositiveButton(getString(R.string.btn_next_channel)) { _, _ -> switchChannel(CHANNEL_NEXT) }
            setNegativeButton(R.string.btn_close) { _, _ -> finish() }
            create().show()
        }
        return false
    }

    // ── Main play logic ───────────────────────────────────────────────────────
    private fun playChannel() {
        switchLiveOrVideo(true)
        bindingRoot.categoryName.text = category?.name?.trim()
        bindingRoot.channelName.text  = current?.name?.trim()

        var streamUrl = URLDecoder.decode(current?.streamUrl, "utf-8")
        var userAgent = streamUrl.findPattern(".*user-agent=(.+?)(\\|.*)?")
        val referer   = streamUrl.findPattern(".*referer=(.+?)(\\|.*)?")
        streamUrl     = streamUrl.findPattern("(.+?)(\\|.*)?") ?: streamUrl

        if (userAgent == null) {
            val agents   = listOf(*resources.getStringArray(R.array.user_agent))
            userAgent    = agents.firstOrNull {
                current?.streamUrl?.contains(
                    it.substringBefore("/").lowercase()
                ) == true
            } ?: agents[Random().nextInt(agents.size)]
        }

        val drmLicense = Playlist.cached.drmLicenses
            .firstOrNull { current?.drmName?.equals(it.name) == true }?.url

        val mimeType = when {
            streamUrl.contains(".mpd",          ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            streamUrl.contains("/dash",         ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            streamUrl.contains(".m3u8",         ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            streamUrl.contains("playlist.m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            streamUrl.contains("master.m3u8",   ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            streamUrl.contains("index.m3u8",    ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            else -> null
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(userAgent)
        if (referer != null)
            httpFactory.setDefaultRequestProperties(mapOf("referer" to referer))
        val dataSourceFactory = DefaultDataSourceFactory(this, httpFactory)

        val isClearKey = current?.drmName?.startsWith("clearkey_") == true
        val isWidevine = current?.drmName?.startsWith("widevine_") == true
        val hasDrm     = !current?.drmName.isNullOrBlank() && !drmLicense.isNullOrBlank()

        var drmSessionManager: com.google.android.exoplayer2.drm.DrmSessionManager =
            com.google.android.exoplayer2.drm.DrmSessionManager.DRM_UNSUPPORTED

        if (hasDrm && isClearKey) {
            try {
                val pairs    = drmLicense!!.split(",")
                val keysJson = StringBuilder("{\"keys\":[")
                pairs.forEachIndexed { i, pair ->
                    val kv = pair.trim().split(":")
                    if (kv.size == 2) {
                        val kidB64 = android.util.Base64.encodeToString(
                            hexToBytes(kv[0].trim()),
                            android.util.Base64.NO_PADDING or
                            android.util.Base64.URL_SAFE  or
                            android.util.Base64.NO_WRAP
                        )
                        val keyB64 = android.util.Base64.encodeToString(
                            hexToBytes(kv[1].trim()),
                            android.util.Base64.NO_PADDING or
                            android.util.Base64.URL_SAFE  or
                            android.util.Base64.NO_WRAP
                        )
                        if (i > 0) keysJson.append(",")
                        keysJson.append("{\"kty\":\"oct\",\"kid\":\"$kidB64\",\"k\":\"$keyB64\"}")
                    }
                }
                keysJson.append("],\"type\":\"temporary\"}")
                val licenseBytes = keysJson.toString().toByteArray(Charsets.UTF_8)
                val drmCallback  =
                    com.google.android.exoplayer2.drm.LocalMediaDrmCallback(licenseBytes)
                drmSessionManager =
                    com.google.android.exoplayer2.drm.DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(
                            C.CLEARKEY_UUID,
                            com.google.android.exoplayer2.drm.FrameworkMediaDrm.DEFAULT_PROVIDER
                        )
                        .setMultiSession(false)
                        .build(drmCallback)
                Log.d("DRM", "ClearKey OK")
            } catch (e: Exception) {
                Log.e("DRM", "ClearKey build error: ${e.message}")
            }
        } else if (hasDrm && isWidevine) {
            if (!isDrmWidevineSupported()) return
            val drmCallback =
                com.google.android.exoplayer2.drm.HttpMediaDrmCallback(drmLicense, httpFactory)
            drmSessionManager =
                com.google.android.exoplayer2.drm.DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(
                        C.WIDEVINE_UUID,
                        com.google.android.exoplayer2.drm.FrameworkMediaDrm.DEFAULT_PROVIDER
                    )
                    .setMultiSession(true)
                    .build(drmCallback)
            Log.d("DRM", "Widevine OK licUrl=$drmLicense")
        } else if (!current?.drmName.isNullOrBlank() && drmLicense == null) {
            Log.e("DRM", "DRM channel but license NOT FOUND")
            Toast.makeText(applicationContext,
                "DRM license tidak ditemukan, coba refresh playlist",
                Toast.LENGTH_LONG).show()
        }

        mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .also { if (mimeType != null) it.setMimeType(mimeType) }
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setDrmSessionManagerProvider { drmSessionManager }

        trackSelector = DefaultTrackSelector(this).apply {
            parameters = ParametersBuilder(applicationContext).build()
        }

        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16))
            .setBufferDurationsMs(32 * 1024, 64 * 1024, 1024, 1024)
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val playerBuilder = SimpleExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
        if (preferences.optimizePrebuffer) playerBuilder.setLoadControl(loadControl)

        player?.release()
        player = playerBuilder.build()
        player?.addListener(PlayerListener())
        player?.resizeMode = preferences.resizeMode

        bindingRoot.playerView.player       = player
        bindingRoot.playerView.resizeMode   = preferences.resizeMode
        bindingRoot.playerView.useController = true

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true

        errorCounter = 0
    }

    private fun retryPlayback(resetCounter: Boolean) {
        if (resetCounter) errorCounter = 0
        playChannel()
    }

    // ── ExoPlayer listener ────────────────────────────────────────────────────
    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY   -> switchLiveOrVideo()
                Player.STATE_BUFFERING -> { /* show buffering indicator */ }
                else -> { /* idle / ended */ }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            errorCounter++
            if (errorCounter <= 3) {
                showMessage(
                    String.format(
                        getString(R.string.player_error_message),
                        error.errorCode, error.errorCodeName, error.message
                    ), true
                )
            } else {
                showMessage(
                    String.format(
                        getString(R.string.player_error_message),
                        error.errorCode, error.errorCodeName, error.message
                    ), true
                )
            }
        }

        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) {
            if (trackGroups == lastSeenTrackGroupArray) return
            lastSeenTrackGroupArray = trackGroups
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return
            val isVideoProblem  = mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO) ==
                MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS
            val isAudioProblem  = mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO) ==
                MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS
            val problem = when {
                isVideoProblem && isAudioProblem -> "video & audio"
                isVideoProblem -> "video"
                else -> "audio"
            }
            val message = String.format(getString(R.string.error_unsupported), problem)
            if (isVideoProblem) showMessage(message, false)
            else if (isAudioProblem)
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showMessage(message: String, autoRetry: Boolean) {
        val waitSec = 30
        val builder = AlertDialog.Builder(this).apply {
            setTitle(R.string.player_playback_error)
            setMessage(message)
            setCancelable(false)
            setNegativeButton(getString(R.string.btn_next_channel)) { di, _ ->
                switchChannel(CHANNEL_NEXT); di.dismiss()
            }
            setPositiveButton(
                if (autoRetry) String.format(getString(R.string.btn_retry_count), waitSec)
                else getString(R.string.btn_retry)
            ) { di, _ -> retryPlayback(true); di.dismiss() }
            setNeutralButton(R.string.btn_close) { di, _ -> di.dismiss(); finish() }
            create()
        }
        val dialog = builder.show()
        if (!autoRetry) return
        AsyncSleep().task(object : AsyncSleep.Task {
            override fun onCountDown(count: Int) {
                val text = if (count <= 0) getString(R.string.btn_retry)
                else String.format(getString(R.string.btn_retry_count), count)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = text
            }
            override fun onFinish() { dialog.dismiss(); retryPlayback(true) }
        }).start(waitSec)
    }

    private fun showTrackSelector(): Boolean {
        TrackSelectionDialog.createForTrackSelector(trackSelector) { }
            .show(supportFragmentManager, null)
        return true
    }

    private fun showScreenMenu(view: View) {
        val timeout = bindingRoot.playerView.controllerShowTimeoutMs
        bindingRoot.playerView.controllerShowTimeoutMs = 0
        PopupMenu(this, view).apply {
            inflate(R.menu.screen_resize_mode)
            setOnMenuItemClickListener { m ->
                val mode = when (m.itemId) {
                    R.id.mode_fixed_width  -> 1
                    R.id.mode_fixed_height -> 2
                    R.id.mode_fill         -> 3
                    R.id.mode_zoom         -> 4
                    else                   -> 0
                }
                if (bindingRoot.playerView.resizeMode != mode) {
                    bindingRoot.playerView.resizeMode = mode
                    preferences.resizeMode = mode
                }
                true
            }
            setOnDismissListener {
                bindingRoot.playerView.controllerShowTimeoutMs = timeout
            }
            show()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onResume()  { super.onResume();  player?.playWhenReady = true }
    override fun onPause()   { super.onPause();   player?.playWhenReady = false }

    @Suppress("DEPRECATION")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == false) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = android.app.PictureInPictureParams.Builder().build()
                enterPictureInPictureMode(params)
            } else {
                enterPictureInPictureMode()
            }
        }
    }

    override fun onPictureInPictureModeChanged(pip: Boolean, config: Configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            super.onPictureInPictureModeChanged(pip, config)
        }
        isPipMode = pip
        setChannelInformation(!pip)
        bindingRoot.playerView.useController = !pip
        player?.playWhenReady = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.setFullScreenFlags()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!bindingRoot.playerView.isControllerVisible &&
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            bindingRoot.playerView.showController(); return true
        }
        if (isLocked) return true
        when (keyCode) {
            KeyEvent.KEYCODE_MENU           -> return showTrackSelector()
            KeyEvent.KEYCODE_PAGE_UP        -> return switchChannel(CATEGORY_UP)
            KeyEvent.KEYCODE_PAGE_DOWN      -> return switchChannel(CATEGORY_DOWN)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> return switchChannel(CHANNEL_PREVIOUS)
            KeyEvent.KEYCODE_MEDIA_NEXT     -> return switchChannel(CHANNEL_NEXT)
            KeyEvent.KEYCODE_MEDIA_PLAY     -> { player?.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE    -> { player?.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player?.isPlaying == false) player?.play() else player?.pause()
                return true
            }
        }
        if (player?.isCurrentWindowLive == false) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_REWIND       -> { player?.seekBack(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player?.seekForward(); return true }
            }
        }
        if (bindingRoot.playerView.isControllerVisible) return super.onKeyUp(keyCode, event)
        val reverse = preferences.reverseNavigation
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP    -> return switchChannel(if (!reverse) CHANNEL_PREVIOUS else CHANNEL_NEXT)
            KeyEvent.KEYCODE_DPAD_DOWN  -> return switchChannel(if (!reverse) CHANNEL_NEXT     else CHANNEL_PREVIOUS)
            KeyEvent.KEYCODE_DPAD_LEFT  -> return switchChannel(if (!reverse) CATEGORY_UP      else CATEGORY_DOWN)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return switchChannel(if (!reverse) CATEGORY_DOWN    else CATEGORY_UP)
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        if (isLocked) return
        if (isTelevision || doubleBackToExitPressedOnce) {
            super.onBackPressed(); finish(); return
        }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_twice_exit_player), Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() {
        player?.release()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

}
