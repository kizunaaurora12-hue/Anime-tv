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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.AspectRatioFrameLayout
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
import okhttp3.OkHttpClient
import java.net.URLDecoder
import java.util.*

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var doubleBackToExitPressedOnce = false
    private val isTelevision = UiMode().isTelevision()
    private val preferences  = Preferences()
    private var category: Category? = null
    private var current: Channel?   = null
    private var player: ExoPlayer?  = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var bindingRoot: ActivityPlayerBinding
    private lateinit var bindingControl: CustomControlBinding
    private var handlerInfo: Handler? = null
    private var isLocked = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.getStringExtra(PLAYER_CALLBACK)) {
                RETRY_PLAYBACK -> playChannel()
                CLOSE_PLAYER   -> finish()
            }
        }
    }

    companion object {
        var isFirst   = true
        var isPipMode = false
        const val PLAYER_CALLBACK = "PLAYER_CALLBACK"
        const val RETRY_PLAYBACK  = "RETRY_PLAYBACK"
        const val CLOSE_PLAYER    = "CLOSE_PLAYER"
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
        bindingControl = CustomControlBinding.bind(bindingRoot.root.findViewById(R.id.custom_control))
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

    private fun bindingListener() {
        bindingRoot.playerView.apply {
            setOnTouchListener(object : OnSwipeTouchListener() {
                override fun onSwipeDown()  { switchChannel(CATEGORY_UP) }
                override fun onSwipeUp()    { switchChannel(CATEGORY_DOWN) }
                override fun onSwipeLeft()  { switchChannel(CHANNEL_NEXT) }
                override fun onSwipeRight() { switchChannel(CHANNEL_PREVIOUS) }
            })
            setControllerVisibilityListener(
                androidx.media3.ui.PlayerView.ControllerVisibilityListener { vis ->
                    setChannelInformation(vis == View.VISIBLE)
                }
            )
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
                if (!isLocked) { (it as ImageButton).setImageResource(R.drawable.ic_lock); lockControl(true) }
            }
            setOnLongClickListener {
                val resId = if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock
                (it as ImageButton).setImageResource(resId)
                lockControl(!isLocked); true
            }
        }
    }

    private fun setChannelInformation(visible: Boolean) {
        if (isLocked) return
        bindingRoot.layoutInfo.visibility = if (visible && !isPipMode) View.VISIBLE else View.INVISIBLE
        if (isPipMode) return
        if (handlerInfo == null) handlerInfo = Handler(Looper.getMainLooper())
        handlerInfo?.removeCallbacksAndMessages(null)
        if (visible) {
            handlerInfo?.postDelayed({
                bindingRoot.layoutInfo.visibility = View.INVISIBLE
            }, bindingRoot.playerView.controllerShowTimeoutMs.toLong())
        }
    }

    private fun lockControl(locked: Boolean) {
        isLocked = locked
        val vis = if (locked) View.INVISIBLE else View.VISIBLE
        bindingRoot.layoutInfo.visibility        = vis
        bindingControl.buttonExit.visibility     = vis
        bindingControl.layoutControl.visibility  = vis
        bindingControl.screenMode.visibility     = vis
        bindingControl.trackSelection.visibility = vis
        updateSeekVisibility()
    }

    private fun updateSeekVisibility() {
        val isLive = player?.isCurrentMediaItemLive == true
        val vis = when {
            isLocked -> View.INVISIBLE
            isLive   -> View.GONE
            else     -> View.VISIBLE
        }
        bindingControl.layoutSeekbar.visibility = vis
        bindingControl.spacerControl.visibility = vis
        val seekVis = if (!isLive && player?.isCurrentMediaItemSeekable == true && !isLocked)
            View.VISIBLE else View.GONE
        bindingControl.buttonRewind.visibility  = seekVis
        bindingControl.buttonForward.visibility = seekVis
    }

    private fun switchChannel(direction: Int): Boolean {
        val cats   = Playlist.cached.categories
        val catIdx = cats.indexOf(category)
        when (direction) {
            CHANNEL_NEXT -> {
                val chIdx = category?.channels?.indexOf(current) ?: return false
                if (chIdx + 1 < (category?.channels?.size ?: 0)) current = category?.channels?.get(chIdx + 1)
                else { val next = (catIdx + 1) % cats.size; category = cats[next]; current = category?.channels?.firstOrNull() }
            }
            CHANNEL_PREVIOUS -> {
                val chIdx = category?.channels?.indexOf(current) ?: return false
                if (chIdx - 1 >= 0) current = category?.channels?.get(chIdx - 1)
                else { val prev = if (catIdx - 1 < 0) cats.size - 1 else catIdx - 1; category = cats[prev]; current = category?.channels?.lastOrNull() }
            }
            CATEGORY_UP   -> { val prev = if (catIdx - 1 < 0) cats.size - 1 else catIdx - 1; category = cats[prev]; current = category?.channels?.firstOrNull() }
            CATEGORY_DOWN -> { category = cats[(catIdx + 1) % cats.size]; current = category?.channels?.firstOrNull() }
        }
        val realCatId = cats.indexOf(category)
        val realChId  = category?.channels?.indexOf(current) ?: 0
        if (realCatId >= 0) preferences.watched = PlayData(realCatId, realChId)
        playChannel()
        return true
    }

    private fun hexToBytes(hex: String): ByteArray {
        val data = ByteArray(hex.length / 2)
        for (i in data.indices) data[i] = ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        return data
    }

    private fun playChannel() {
        bindingRoot.categoryName.text = category?.name?.trim()
        bindingRoot.channelName.text  = current?.name?.trim()

        var streamUrl = URLDecoder.decode(current?.streamUrl ?: "", "utf-8")
        var userAgent = streamUrl.findPattern(".*user-agent=(.+?)(\\|.*)?")
        val referer   = streamUrl.findPattern(".*referer=(.+?)(\\|.*)?")
        streamUrl     = streamUrl.findPattern("(.+?)(\\|.*)?") ?: streamUrl

        if (userAgent == null) {
            val agents = listOf(*resources.getStringArray(R.array.user_agent))
            userAgent  = agents[Random().nextInt(agents.size)]
        }

        val drmLicense = Playlist.cached.drmLicenses
            .firstOrNull { current?.drmName?.equals(it.name) == true }?.url

        val isClearKey = current?.drmName?.startsWith("clearkey_") == true
        val isWidevine = current?.drmName?.startsWith("widevine_") == true
        val hasDrm     = !current?.drmName.isNullOrBlank() && !drmLicense.isNullOrBlank()

        // HTTP factory
        val okClient = OkHttpClient.Builder().build()
        val httpFactory = OkHttpDataSource.Factory(okClient)
            .setUserAgent(userAgent)
        if (referer != null) httpFactory.setDefaultRequestProperties(mapOf("referer" to referer))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        // DRM
        var drmSessionManager: androidx.media3.exoplayer.drm.DrmSessionManager =
            androidx.media3.exoplayer.drm.DrmSessionManager.DRM_UNSUPPORTED

        if (hasDrm && isClearKey) {
            try {
                val pairs    = drmLicense!!.split(",")
                val keysJson = StringBuilder("{\"keys\":[")
                pairs.forEachIndexed { i, pair ->
                    val kv = pair.trim().split(":")
                    if (kv.size == 2) {
                        val kid = android.util.Base64.encodeToString(hexToBytes(kv[0].trim()),
                            android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        val key = android.util.Base64.encodeToString(hexToBytes(kv[1].trim()),
                            android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        if (i > 0) keysJson.append(",")
                        keysJson.append("{\"kty\":\"oct\",\"kid\":\"$kid\",\"k\":\"$key\"}")
                    }
                }
                keysJson.append("],\"type\":\"temporary\"}")
                drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(LocalMediaDrmCallback(keysJson.toString().toByteArray()))
                Log.d("DRM", "ClearKey OK")
            } catch (e: Exception) { Log.e("DRM", "ClearKey error: ${e.message}") }
        } else if (hasDrm && isWidevine) {
            if (!MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)) {
                Toast.makeText(this, R.string.device_not_support_widevine, Toast.LENGTH_LONG).show()
                switchChannel(CHANNEL_NEXT); return
            }
            val drmCallback = HttpMediaDrmCallback(drmLicense, DefaultHttpDataSource.Factory())
            drmSessionManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(true)
                .build(drmCallback)
            Log.d("DRM", "Widevine OK")
        }

        val mediaItem = MediaItem.Builder().setUri(Uri.parse(streamUrl)).build()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setDrmSessionManagerProvider { drmSessionManager }

        trackSelector = DefaultTrackSelector(this)

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16))
            .setBufferDurationsMs(32 * 1024, 64 * 1024, 1024, 1024)
            .build()

        player?.release()
        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .also { if (preferences.optimizePrebuffer) it.setLoadControl(loadControl) }
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) updateSeekVisibility()
            }
            override fun onPlayerError(error: PlaybackException) {
                showErrorDialog(error.message ?: "Unknown error")
            }
        })

        bindingRoot.playerView.player      = player
        bindingRoot.playerView.resizeMode  = preferences.resizeMode
        bindingRoot.playerView.useController = true

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }

    private fun showErrorDialog(message: String) {
        val waitSec = 30
        val dlg = AlertDialog.Builder(this).apply {
            setTitle(R.string.player_playback_error)
            setMessage(message)
            setCancelable(false)
            setNegativeButton(R.string.btn_next_channel) { d, _ -> switchChannel(CHANNEL_NEXT); d.dismiss() }
            setPositiveButton(String.format(getString(R.string.btn_retry_count), waitSec)) { d, _ -> playChannel(); d.dismiss() }
            setNeutralButton(R.string.btn_close) { d, _ -> d.dismiss(); finish() }
        }.show()
        AsyncSleep().task(object : AsyncSleep.Task {
            override fun onCountDown(count: Int) {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).text =
                    if (count <= 0) getString(R.string.btn_retry)
                    else String.format(getString(R.string.btn_retry_count), count)
            }
            override fun onFinish() { dlg.dismiss(); playChannel() }
        }).start(waitSec)
    }

    private fun showTrackSelector(): Boolean {
        TrackSelectionDialog.createForTrackSelector(trackSelector) {}
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
                    R.id.mode_fixed_width  -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    R.id.mode_fixed_height -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                    R.id.mode_fill         -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    R.id.mode_zoom         -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else                   -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                bindingRoot.playerView.resizeMode = mode
                preferences.resizeMode = mode
                true
            }
            setOnDismissListener { bindingRoot.playerView.controllerShowTimeoutMs = timeout }
            show()
        }
    }

    override fun onResume()  { super.onResume();  player?.playWhenReady = true }
    override fun onPause()   { super.onPause();   player?.playWhenReady = false }

    @Suppress("DEPRECATION")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == false) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            enterPictureInPictureMode()
        }
    }

    override fun onPictureInPictureModeChanged(pip: Boolean, config: Configuration) {
        super.onPictureInPictureModeChanged(pip, config)
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
        if (!bindingRoot.playerView.isControllerFullyVisible && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
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
                if (player?.isPlaying == false) player?.play() else player?.pause(); return true
            }
        }
        if (bindingRoot.playerView.isControllerFullyVisible) return super.onKeyUp(keyCode, event)
        val rev = preferences.reverseNavigation
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP    -> return switchChannel(if (!rev) CHANNEL_PREVIOUS else CHANNEL_NEXT)
            KeyEvent.KEYCODE_DPAD_DOWN  -> return switchChannel(if (!rev) CHANNEL_NEXT     else CHANNEL_PREVIOUS)
            KeyEvent.KEYCODE_DPAD_LEFT  -> return switchChannel(if (!rev) CATEGORY_UP      else CATEGORY_DOWN)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return switchChannel(if (!rev) CATEGORY_DOWN    else CATEGORY_UP)
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        if (isLocked) return
        if (isTelevision || doubleBackToExitPressedOnce) { super.onBackPressed(); finish(); return }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, R.string.press_back_twice_exit_player, Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() {
        player?.release()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }
}
