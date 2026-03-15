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
import com.google.android.exoplayer2.drm.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.miyuki.tv.databinding.ActivityPlayerBinding
import com.miyuki.tv.databinding.CustomControlBinding
import com.miyuki.tv.dialog.TrackSelectionDialog
import com.miyuki.tv.extension.findPattern
import com.miyuki.tv.extension.setFullScreenFlags
import com.miyuki.tv.extra.*
import com.miyuki.tv.model.*
import java.net.URLDecoder
import java.util.*

class PlayerActivity : AppCompatActivity() {

    private var doubleBackToExitPressedOnce = false
    private val isTelevision = UiMode().isTelevision()
    private val preferences  = Preferences()
    private var category: Category? = null
    private var current: Channel?   = null
    private var player: SimpleExoPlayer? = null
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
        private const val CH_NEXT  = 0
        private const val CH_PREV  = 1
        private const val CAT_UP   = 2
        private const val CAT_DOWN = 3
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LocaleHelper.setLocale(base, LocaleHelper.getLanguageCode(base)))
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
            val p = intent.getParcelableExtra<PlayData>(PlayData.VALUE)!!
            category = Playlist.cached.categories[p.catId]
            current  = category?.channels?.get(p.chId)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.player_playdata_error, Toast.LENGTH_SHORT).show()
            finish(); return
        }
        if (category == null || current == null) {
            Toast.makeText(this, R.string.player_no_channel, Toast.LENGTH_SHORT).show()
            finish(); return
        }
        bindListeners()
        playChannel()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(PLAYER_CALLBACK))
    }

    private fun bindListeners() {
        bindingRoot.playerView.apply {
            setOnTouchListener(object : OnSwipeTouchListener() {
                override fun onSwipeDown()  { switchChannel(CAT_UP) }
                override fun onSwipeUp()    { switchChannel(CAT_DOWN) }
                override fun onSwipeLeft()  { switchChannel(CH_NEXT) }
                override fun onSwipeRight() { switchChannel(CH_PREV) }
            })
            setControllerVisibilityListener { setChannelInfo(it == View.VISIBLE) }
        }
        bindingControl.apply {
            trackSelection.setOnClickListener { showTrackSelector() }
            buttonExit.apply {
                visibility = if (isTelevision) View.GONE else View.VISIBLE
                setOnClickListener { finish() }
            }
            buttonPrevious.setOnClickListener { switchChannel(CH_PREV) }
            buttonRewind.setOnClickListener   { player?.seekBack() }
            buttonForward.setOnClickListener  { player?.seekForward() }
            buttonNext.setOnClickListener     { switchChannel(CH_NEXT) }
            screenMode.setOnClickListener     { showScreenMenu(it) }
            buttonLock.apply {
                visibility = if (isTelevision) View.GONE else View.VISIBLE
                setOnClickListener {
                    if (!isLocked) { (it as ImageButton).setImageResource(R.drawable.ic_lock); lockControl(true) }
                }
                setOnLongClickListener {
                    (it as ImageButton).setImageResource(if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock)
                    lockControl(!isLocked); true
                }
            }
        }
    }

    private fun setChannelInfo(visible: Boolean) {
        if (isLocked) return
        bindingRoot.layoutInfo.visibility = if (visible && !isPipMode) View.VISIBLE else View.INVISIBLE
        if (isPipMode || !visible) return
        if (handlerInfo == null) handlerInfo = Handler(Looper.getMainLooper())
        handlerInfo?.removeCallbacksAndMessages(null)
        handlerInfo?.postDelayed({
            bindingRoot.layoutInfo.visibility = View.INVISIBLE
        }, bindingRoot.playerView.controllerShowTimeoutMs.toLong())
    }

    private fun lockControl(locked: Boolean) {
        isLocked = locked
        val vis = if (locked) View.INVISIBLE else View.VISIBLE
        bindingRoot.layoutInfo.visibility        = vis
        bindingControl.buttonExit.visibility     = vis
        bindingControl.layoutControl.visibility  = vis
        bindingControl.screenMode.visibility     = vis
        bindingControl.trackSelection.visibility = vis
        updateSeekBar()
    }

    private fun updateSeekBar() {
        val isLive = player?.isCurrentWindowLive == true
        val vis = when { isLocked -> View.INVISIBLE; isLive -> View.GONE; else -> View.VISIBLE }
        bindingControl.layoutSeekbar.visibility = vis
        bindingControl.spacerControl.visibility = vis
        val seekVis = if (!isLive && !isLocked) View.VISIBLE else View.GONE
        bindingControl.buttonRewind.visibility  = seekVis
        bindingControl.buttonForward.visibility = seekVis
    }

    private fun switchChannel(dir: Int): Boolean {
        val cats = Playlist.cached.categories
        val ci   = cats.indexOf(category)
        when (dir) {
            CH_NEXT  -> { val chi = category?.channels?.indexOf(current) ?: return false
                if (chi + 1 < (category?.channels?.size ?: 0)) current = category?.channels?.get(chi + 1)
                else { category = cats[(ci + 1) % cats.size]; current = category?.channels?.firstOrNull() } }
            CH_PREV  -> { val chi = category?.channels?.indexOf(current) ?: return false
                if (chi - 1 >= 0) current = category?.channels?.get(chi - 1)
                else { category = cats[if (ci-1<0) cats.size-1 else ci-1]; current = category?.channels?.lastOrNull() } }
            CAT_UP   -> { category = cats[if (ci-1<0) cats.size-1 else ci-1]; current = category?.channels?.firstOrNull() }
            CAT_DOWN -> { category = cats[(ci+1) % cats.size]; current = category?.channels?.firstOrNull() }
        }
        val rci = cats.indexOf(category)
        val rchi = category?.channels?.indexOf(current) ?: 0
        if (rci >= 0) preferences.watched = PlayData(rci, rchi)
        playChannel(); return true
    }

    private fun hexToBytes(hex: String): ByteArray {
        val d = ByteArray(hex.length / 2)
        for (i in d.indices) d[i] = ((Character.digit(hex[i*2],16) shl 4) + Character.digit(hex[i*2+1],16)).toByte()
        return d
    }

    private fun playChannel() {
        bindingRoot.categoryName.text = category?.name?.trim()
        bindingRoot.channelName.text  = current?.name?.trim()

        var url  = URLDecoder.decode(current?.streamUrl ?: "", "utf-8")
        var ua   = url.findPattern(".*user-agent=(.+?)(\\|.*)?")
        val ref  = url.findPattern(".*referer=(.+?)(\\|.*)?")
        url      = url.findPattern("(.+?)(\\|.*)?") ?: url
        if (ua == null) {
            val agents = resources.getStringArray(R.array.user_agent).toList()
            ua = agents[Random().nextInt(agents.size)]
        }

        val drmLic = Playlist.cached.drmLicenses
            .firstOrNull { current?.drmName?.equals(it.name) == true }?.url
        val isCK   = current?.drmName?.startsWith("clearkey_") == true
        val isWV   = current?.drmName?.startsWith("widevine_") == true
        val hasDrm = !current?.drmName.isNullOrBlank() && !drmLic.isNullOrBlank()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(ua)
        if (ref != null) httpFactory.setDefaultRequestProperties(mapOf("referer" to ref))
        val dsFactory = DefaultDataSourceFactory(this, httpFactory)

        var dsm: DrmSessionManager = DrmSessionManager.DRM_UNSUPPORTED
        if (hasDrm && isCK) {
            try {
                val pairs = drmLic!!.split(",")
                val json  = StringBuilder("{\"keys\":[")
                pairs.forEachIndexed { i, p ->
                    val kv = p.trim().split(":")
                    if (kv.size == 2) {
                        val kid = android.util.Base64.encodeToString(hexToBytes(kv[0].trim()),
                            android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        val key = android.util.Base64.encodeToString(hexToBytes(kv[1].trim()),
                            android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        if (i > 0) json.append(",")
                        json.append("{\"kty\":\"oct\",\"kid\":\"$kid\",\"k\":\"$key\"}")
                    }
                }
                json.append("],\"type\":\"temporary\"}")
                dsm = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(LocalMediaDrmCallback(json.toString().toByteArray()))
                Log.d("DRM","ClearKey OK")
            } catch (e: Exception) { Log.e("DRM","ClearKey: ${e.message}") }
        } else if (hasDrm && isWV) {
            if (!MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)) {
                Toast.makeText(this, R.string.device_not_support_widevine, Toast.LENGTH_LONG).show()
                switchChannel(CH_NEXT); return
            }
            dsm = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(true)
                .build(HttpMediaDrmCallback(drmLic, httpFactory))
            Log.d("DRM","Widevine OK")
        }

        val mimeType = when {
            url.contains(".mpd",  true) || url.contains("/dash", true) -> MimeTypes.APPLICATION_MPD
            url.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
            else -> null
        }
        val mediaItem = MediaItem.Builder().setUri(Uri.parse(url))
            .also { if (mimeType != null) it.setMimeType(mimeType) }.build()

        val msFactory = DefaultMediaSourceFactory(dsFactory as com.google.android.exoplayer2.upstream.DataSource.Factory)
            .setDrmSessionManagerProvider { dsm }

        trackSelector = DefaultTrackSelector(this).apply {
            parameters = DefaultTrackSelector.ParametersBuilder(applicationContext).build()
        }

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16))
            .setBufferDurationsMs(32*1024, 64*1024, 1024, 1024)
            .build()

        player?.release()
        player = SimpleExoPlayer.Builder(this)
            .setMediaSourceFactory(msFactory)
            .setTrackSelector(trackSelector)
            .also { if (preferences.optimizePrebuffer) it.setLoadControl(loadControl) }
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) updateSeekBar()
            }
            override fun onPlayerError(error: PlaybackException) {
                showError(error.message ?: "Playback error")
            }
        })

        bindingRoot.playerView.player       = player
        bindingRoot.playerView.resizeMode   = preferences.resizeMode
        bindingRoot.playerView.useController = true
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }

    private fun showError(msg: String) {
        val secs = 30
        val dlg = AlertDialog.Builder(this).apply {
            setTitle(R.string.player_playback_error)
            setMessage(msg)
            setCancelable(false)
            setNegativeButton(R.string.btn_next_channel) { d,_ -> switchChannel(CH_NEXT); d.dismiss() }
            setPositiveButton(String.format(getString(R.string.btn_retry_count), secs)) { d,_ -> playChannel(); d.dismiss() }
            setNeutralButton(R.string.btn_close) { d,_ -> d.dismiss(); finish() }
        }.show()
        AsyncSleep().task(object : AsyncSleep.Task {
            override fun onCountDown(count: Int) {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).text =
                    if (count<=0) getString(R.string.btn_retry)
                    else String.format(getString(R.string.btn_retry_count), count)
            }
            override fun onFinish() { dlg.dismiss(); playChannel() }
        }).start(secs)
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
                    R.id.mode_fixed_width  -> 1
                    R.id.mode_fixed_height -> 2
                    R.id.mode_fill         -> 3
                    R.id.mode_zoom         -> 4
                    else -> 0
                }
                bindingRoot.playerView.resizeMode = mode
                preferences.resizeMode = mode; true
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            enterPictureInPictureMode()
    }

    override fun onPictureInPictureModeChanged(pip: Boolean, config: Configuration) {
        super.onPictureInPictureModeChanged(pip, config)
        isPipMode = pip
        setChannelInfo(!pip)
        bindingRoot.playerView.useController = !pip
        player?.playWhenReady = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.setFullScreenFlags()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!bindingRoot.playerView.isControllerVisible && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            bindingRoot.playerView.showController(); return true
        }
        if (isLocked) return true
        when (keyCode) {
            KeyEvent.KEYCODE_MENU           -> return showTrackSelector()
            KeyEvent.KEYCODE_PAGE_UP        -> return switchChannel(CAT_UP)
            KeyEvent.KEYCODE_PAGE_DOWN      -> return switchChannel(CAT_DOWN)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> return switchChannel(CH_PREV)
            KeyEvent.KEYCODE_MEDIA_NEXT     -> return switchChannel(CH_NEXT)
            KeyEvent.KEYCODE_MEDIA_PLAY     -> { player?.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE    -> { player?.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player?.isPlaying==false) player?.play() else player?.pause(); return true }
        }
        if (bindingRoot.playerView.isControllerVisible) return super.onKeyUp(keyCode, event)
        val rev = preferences.reverseNavigation
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP    -> return switchChannel(if (!rev) CH_PREV  else CH_NEXT)
            KeyEvent.KEYCODE_DPAD_DOWN  -> return switchChannel(if (!rev) CH_NEXT  else CH_PREV)
            KeyEvent.KEYCODE_DPAD_LEFT  -> return switchChannel(if (!rev) CAT_UP   else CAT_DOWN)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return switchChannel(if (!rev) CAT_DOWN else CAT_UP)
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
