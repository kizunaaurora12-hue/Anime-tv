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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.drm.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.*
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
    private var isLocked     = false
    private var isPanelOpen  = false

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
        bindingControl = CustomControlBinding.bind(
            bindingRoot.root.findViewById(R.id.custom_control))
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
        // Gesture swipe — JANGAN override tap, biarkan ExoPlayer handle tap sendiri
        bindingRoot.playerView.setOnTouchListener(object : OnSwipeTouchListener() {
            override fun onSwipeDown()  { if (!isLocked && !isPanelOpen) switchChannel(CAT_UP) }
            override fun onSwipeUp()    { if (!isLocked && !isPanelOpen) switchChannel(CAT_DOWN) }
            override fun onSwipeLeft()  { if (!isLocked && !isPanelOpen) switchChannel(CH_NEXT) }
            override fun onSwipeRight() { if (!isLocked && !isPanelOpen) switchChannel(CH_PREV) }
        })

        // ExoPlayer visibility listener
        bindingRoot.playerView.setControllerVisibilityListener { vis ->
            if (vis == View.VISIBLE) setChannelInfo(true)
            else setChannelInfo(false)
        }

        bindingControl.apply {
            // CHANNEL LIST
            buttonChannelList.setOnClickListener {
                if (isPanelOpen) closeChannelPanel() else openChannelPanel()
            }

            // TRACK SELECTION
            trackSelection.setOnClickListener { showTrackSelector() }

            // SCREEN MODE
            screenMode.setOnClickListener { showScreenMenu(it) }

            // EXIT
            buttonExit.apply {
                visibility = if (isTelevision) View.GONE else View.VISIBLE
                setOnClickListener { finish() }
            }

            // PLAYBACK
            buttonPrevious.setOnClickListener { switchChannel(CH_PREV) }
            buttonRewind.setOnClickListener   { player?.seekBack() }
            buttonForward.setOnClickListener  { player?.seekForward() }
            buttonNext.setOnClickListener     { switchChannel(CH_NEXT) }

            // LOCK - klik=lock, long press=unlock
            buttonLock.apply {
                visibility = if (isTelevision) View.GONE else View.VISIBLE
                setOnClickListener {
                    if (!isLocked) {
                        (it as ImageButton).setImageResource(R.drawable.ic_lock)
                        lockControl(true)
                        Toast.makeText(
                            this@PlayerActivity,
                            "Layar terkunci. Tekan lama tombol kunci untuk membuka.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                setOnLongClickListener {
                    val resId = if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock
                    (it as ImageButton).setImageResource(resId)
                    lockControl(!isLocked)
                    Toast.makeText(
                        this@PlayerActivity,
                        if (!isLocked) "Layar dibuka" else "Layar terkunci",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }

            // RESOLUSI
            resBtnAuto.setOnClickListener  { applyResolution(null,  resBtnAuto) }
            resBtn360.setOnClickListener   { applyResolution(360,   resBtn360) }
            resBtn480.setOnClickListener   { applyResolution(480,   resBtn480) }
            resBtn720.setOnClickListener   { applyResolution(720,   resBtn720) }
            resBtn1080.setOnClickListener  { applyResolution(1080,  resBtn1080) }
        }

        // Panel close
        bindingRoot.root.findViewById<View>(R.id.panel_close)
            .setOnClickListener { closeChannelPanel() }
    }

    // ── RESOLUSI — FIX: pakai ParametersBuilder ExoPlayer 2.15.0 ─────────────
    private fun applyResolution(maxHeight: Int?, activeBtn: TextView) {
        listOf(
            bindingControl.resBtnAuto,
            bindingControl.resBtn360,
            bindingControl.resBtn480,
            bindingControl.resBtn720,
            bindingControl.resBtn1080
        ).forEach {
            it.setTextColor(getColor(R.color.color_text_secondary))
            it.setBackgroundResource(R.drawable.res_btn_bg)
        }
        activeBtn.setTextColor(getColor(R.color.color_primary))
        activeBtn.setBackgroundResource(R.drawable.res_btn_active_bg)

        // ExoPlayer 2.15.0 cara set max resolution
        val builder = DefaultTrackSelector.ParametersBuilder(this)
        if (maxHeight != null) {
            builder.setMaxVideoSize(Int.MAX_VALUE, maxHeight)
        } else {
            builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        trackSelector.setParameters(builder.build())
        Toast.makeText(this, if (maxHeight == null) "Auto" else "${maxHeight}p", Toast.LENGTH_SHORT).show()
    }

    // ── SCREEN MODE — FIX: force update resizeMode ────────────────────────────
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
                bindingRoot.playerView.resizeMode = mode
                preferences.resizeMode = mode
                val label = when (mode) {
                    1 -> "Fixed Width"; 2 -> "Fixed Height"
                    3 -> "Fill";        4 -> "Zoom"; else -> "Fit"
                }
                Toast.makeText(this@PlayerActivity, label, Toast.LENGTH_SHORT).show()
                true
            }
            setOnDismissListener { bindingRoot.playerView.controllerShowTimeoutMs = timeout }
            show()
        }
    }

    // ── CHANNEL PANEL ─────────────────────────────────────────────────────────
    private fun openChannelPanel() {
        isPanelOpen = true
        val panel = bindingRoot.root.findViewById<View>(R.id.panel_channel_list)
        panel.visibility = View.VISIBLE
        bindingRoot.root.findViewById<TextView>(R.id.panel_cat_name)
            .text = category?.name?.uppercase()

        val channels   = category?.channels ?: return
        val catIdx     = Playlist.cached.categories.indexOf(category)
        val currentIdx = channels.indexOf(current)
        val rv         = bindingRoot.root.findViewById<RecyclerView>(R.id.panel_rv_channels)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = PanelChannelAdapter(channels, currentIdx) { ch, chIdx ->
            current = ch
            if (catIdx >= 0) preferences.watched = PlayData(catIdx, chIdx)
            playChannel(); closeChannelPanel()
        }
        if (currentIdx >= 0) rv.scrollToPosition(currentIdx)
        // Jaga controller tetap tampil
        bindingRoot.playerView.controllerShowTimeoutMs = 0
        bindingRoot.playerView.showController()
    }

    private fun closeChannelPanel() {
        isPanelOpen = false
        bindingRoot.root.findViewById<View>(R.id.panel_channel_list).visibility = View.GONE
        bindingRoot.playerView.controllerShowTimeoutMs = 4000
    }

    inner class PanelChannelAdapter(
        private val channels: ArrayList<Channel>,
        private val currentIdx: Int,
        private val onSelect: (Channel, Int) -> Unit
    ) : RecyclerView.Adapter<PanelChannelAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img:     android.widget.ImageView = v.findViewById(R.id.panel_ch_img)
            val initial: TextView                 = v.findViewById(R.id.panel_ch_initial)
            val name:    TextView                 = v.findViewById(R.id.panel_ch_name)
            val drm:     TextView                 = v.findViewById(R.id.panel_ch_drm)
            val root:    View                     = v.findViewById(R.id.panel_ch_root)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH =
            VH(layoutInflater.inflate(R.layout.item_panel_channel, p, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val ch = channels[pos]
            h.name.text = ch.name
            h.root.setBackgroundResource(
                if (pos == currentIdx) R.drawable.sidebar_item_selected_bg
                else android.R.color.transparent)
            h.name.setTextColor(
                if (pos == currentIdx) getColor(R.color.color_primary)
                else getColor(R.color.color_text))
            h.drm.visibility = if (ch.drmName != null) View.VISIBLE else View.GONE
            if (!ch.logo.isNullOrBlank()) {
                Glide.with(this@PlayerActivity).load(ch.logo)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.color.transparent)
                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: com.bumptech.glide.load.engine.GlideException?, m: Any?,
                            t: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?, f: Boolean): Boolean {
                            h.img.visibility = View.GONE; h.initial.visibility = View.VISIBLE
                            h.initial.text = ch.name?.take(1)?.uppercase(); return false }
                        override fun onResourceReady(r: android.graphics.drawable.Drawable?, m: Any?,
                            t: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            d: com.bumptech.glide.load.DataSource?, f: Boolean): Boolean {
                            h.img.visibility = View.VISIBLE; h.initial.visibility = View.GONE; return false }
                    }).into(h.img)
            } else {
                h.img.visibility = View.GONE; h.initial.visibility = View.VISIBLE
                h.initial.text = ch.name?.take(1)?.uppercase() ?: "?"
            }
            h.root.setOnClickListener { onSelect(ch, pos) }
        }
        override fun getItemCount() = channels.size
    }

    // ── CHANNEL INFO OVERLAY ──────────────────────────────────────────────────
    private fun setChannelInfo(visible: Boolean) {
        if (isLocked) return
        bindingRoot.layoutInfo.visibility =
            if (visible && !isPipMode) View.VISIBLE else View.INVISIBLE
        if (!visible || isPipMode) return
        if (handlerInfo == null) handlerInfo = Handler(Looper.getMainLooper())
        handlerInfo?.removeCallbacksAndMessages(null)
        handlerInfo?.postDelayed({
            bindingRoot.layoutInfo.visibility = View.INVISIBLE
        }, bindingRoot.playerView.controllerShowTimeoutMs.toLong().coerceAtLeast(1000L))
    }

    // ── LOCK — FIX: tombol lock TIDAK ikut hidden ─────────────────────────────
    private fun lockControl(locked: Boolean) {
        isLocked = locked
        // Saat locked: semua kontrol hilang KECUALI tombol lock itu sendiri
        val vis = if (locked) View.INVISIBLE else View.VISIBLE
        bindingRoot.layoutInfo.visibility        = vis
        bindingControl.buttonExit.visibility     = if (isTelevision) View.GONE else vis
        bindingControl.layoutControl.visibility  = vis
        bindingControl.screenMode.visibility     = vis
        bindingControl.trackSelection.visibility = vis
        bindingControl.buttonChannelList.visibility = vis
        bindingControl.layoutResolution.visibility  = vis
        // Tombol lock TETAP VISIBLE saat locked agar bisa long press
        bindingControl.buttonLock.visibility = if (isTelevision) View.GONE else View.VISIBLE
        updateSeekBar()
    }

    private fun updateSeekBar() {
        val isLive = player?.isCurrentWindowLive == true
        val vis = when { isLocked -> View.INVISIBLE; isLive -> View.GONE; else -> View.VISIBLE }
        bindingControl.layoutSeekbar.visibility = vis
        bindingControl.spacerControl.visibility = vis
        val sv = if (!isLive && !isLocked) View.VISIBLE else View.GONE
        bindingControl.buttonRewind.visibility  = sv
        bindingControl.buttonForward.visibility = sv
    }

    // ── SWITCH CHANNEL ────────────────────────────────────────────────────────
    private fun switchChannel(dir: Int): Boolean {
        if (isLocked) return true
        val cats = Playlist.cached.categories
        val ci   = cats.indexOf(category)
        when (dir) {
            CH_NEXT  -> { val chi = category?.channels?.indexOf(current) ?: return false
                if (chi+1 < (category?.channels?.size ?: 0)) current = category?.channels?.get(chi+1)
                else { category = cats[(ci+1) % cats.size]; current = category?.channels?.firstOrNull() } }
            CH_PREV  -> { val chi = category?.channels?.indexOf(current) ?: return false
                if (chi-1 >= 0) current = category?.channels?.get(chi-1)
                else { category = cats[if (ci-1<0) cats.size-1 else ci-1]; current = category?.channels?.lastOrNull() } }
            CAT_UP   -> { category = cats[if (ci-1<0) cats.size-1 else ci-1]; current = category?.channels?.firstOrNull() }
            CAT_DOWN -> { category = cats[(ci+1) % cats.size]; current = category?.channels?.firstOrNull() }
        }
        val rci = cats.indexOf(category); val rchi = category?.channels?.indexOf(current) ?: 0
        if (rci >= 0) preferences.watched = PlayData(rci, rchi)
        bindingRoot.playerView.hideController()
        playChannel(); return true
    }

    // ── PLAY CHANNEL ──────────────────────────────────────────────────────────
    private fun hexToBytes(hex: String): ByteArray {
        val d = ByteArray(hex.length/2)
        for (i in d.indices) d[i] = ((Character.digit(hex[i*2],16) shl 4) + Character.digit(hex[i*2+1],16)).toByte()
        return d
    }

    private fun playChannel() {
        bindingRoot.categoryName.text = category?.name?.trim()
        bindingRoot.channelName.text  = current?.name?.trim()

        var url = URLDecoder.decode(current?.streamUrl ?: "", "utf-8")
        var ua  = url.findPattern(".*user-agent=(.+?)(\\|.*)?")
        val ref = url.findPattern(".*referer=(.+?)(\\|.*)?")
        url     = url.findPattern("(.+?)(\\|.*)?") ?: url
        if (ua == null) { val ag = resources.getStringArray(R.array.user_agent).toList(); ua = ag[Random().nextInt(ag.size)] }

        val drmLic = Playlist.cached.drmLicenses.firstOrNull { current?.drmName?.equals(it.name) == true }?.url
        val isCK   = current?.drmName?.startsWith("clearkey_") == true
        val isWV   = current?.drmName?.startsWith("widevine_") == true
        val hasDrm = !current?.drmName.isNullOrBlank() && !drmLic.isNullOrBlank()

        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent(ua)
        if (ref != null) httpFactory.setDefaultRequestProperties(mapOf("referer" to ref))
        val dsFactory = DefaultDataSourceFactory(this, httpFactory)

        var dsm: DrmSessionManager = DrmSessionManager.DRM_UNSUPPORTED
        if (hasDrm && isCK) {
            try {
                val pairs = drmLic!!.split(","); val json = StringBuilder("{\"keys\":[")
                pairs.forEachIndexed { i, p ->
                    val kv = p.trim().split(":")
                    if (kv.size == 2) {
                        val kid = android.util.Base64.encodeToString(hexToBytes(kv[0].trim()), android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        val key = android.util.Base64.encodeToString(hexToBytes(kv[1].trim()), android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        if (i > 0) json.append(","); json.append("{\"kty\":\"oct\",\"kid\":\"$kid\",\"k\":\"$key\"}")
                    }
                }
                json.append("],\"type\":\"temporary\"}")
                dsm = DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER).setMultiSession(false).build(LocalMediaDrmCallback(json.toString().toByteArray()))
            } catch (e: Exception) { Log.e("DRM","CK: ${e.message}") }
        } else if (hasDrm && isWV) {
            if (!MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)) { Toast.makeText(this, R.string.device_not_support_widevine, Toast.LENGTH_LONG).show(); switchChannel(CH_NEXT); return }
            dsm = DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER).setMultiSession(true).build(HttpMediaDrmCallback(drmLic, httpFactory))
        }

        val mimeType = when { url.contains(".mpd",true)||url.contains("/dash",true) -> MimeTypes.APPLICATION_MPD; url.contains(".m3u8",true) -> MimeTypes.APPLICATION_M3U8; else -> null }
        val mediaItem = MediaItem.Builder().setUri(Uri.parse(url)).also { if (mimeType != null) it.setMimeType(mimeType) }.build()
        val msFactory = DefaultMediaSourceFactory(dsFactory as DataSource.Factory).setDrmSessionManagerProvider { dsm }

        trackSelector = DefaultTrackSelector(this).apply { parameters = DefaultTrackSelector.ParametersBuilder(applicationContext).build() }
        val lc = DefaultLoadControl.Builder().setAllocator(DefaultAllocator(true,16)).setBufferDurationsMs(32*1024,64*1024,1024,1024).build()

        player?.release()
        player = SimpleExoPlayer.Builder(this).setMediaSourceFactory(msFactory).setTrackSelector(trackSelector)
            .also { if (preferences.optimizePrebuffer) it.setLoadControl(lc) }.build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_READY) updateSeekBar() }
            override fun onPlayerError(e: PlaybackException) { showError(e.message ?: "Error") }
        })
        bindingRoot.playerView.player       = player
        bindingRoot.playerView.resizeMode   = preferences.resizeMode
        bindingRoot.playerView.useController = true
        player?.setMediaItem(mediaItem); player?.prepare(); player?.playWhenReady = true
        // Reset resolusi ke Auto
        applyResolution(null, bindingControl.resBtnAuto)
    }

    private fun showError(msg: String) {
        val secs = 30
        val dlg = AlertDialog.Builder(this).apply {
            setTitle(R.string.player_playback_error); setMessage(msg); setCancelable(false)
            setNegativeButton(R.string.btn_next_channel) { d,_ -> switchChannel(CH_NEXT); d.dismiss() }
            setPositiveButton(String.format(getString(R.string.btn_retry_count), secs)) { d,_ -> playChannel(); d.dismiss() }
            setNeutralButton(R.string.btn_close) { d,_ -> d.dismiss(); finish() }
        }.show()
        AsyncSleep().task(object : AsyncSleep.Task {
            override fun onCountDown(c: Int) { dlg.getButton(AlertDialog.BUTTON_POSITIVE).text = if (c<=0) getString(R.string.btn_retry) else String.format(getString(R.string.btn_retry_count), c) }
            override fun onFinish() { dlg.dismiss(); playChannel() }
        }).start(secs)
    }

    private fun showTrackSelector(): Boolean { TrackSelectionDialog.createForTrackSelector(trackSelector) {}.show(supportFragmentManager, null); return true }

    override fun onResume()  { super.onResume();  player?.playWhenReady = true }
    override fun onPause()   { super.onPause();   player?.playWhenReady = false }

    @Suppress("DEPRECATION")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint(); if (player?.isPlaying == false) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) enterPictureInPictureMode()
    }
    override fun onPictureInPictureModeChanged(pip: Boolean, config: Configuration) {
        super.onPictureInPictureModeChanged(pip, config); isPipMode = pip
        setChannelInfo(!pip); bindingRoot.playerView.useController = !pip; player?.playWhenReady = true
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) window.setFullScreenFlags() }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isPanelOpen) { closeChannelPanel(); return true }
        if (!bindingRoot.playerView.isControllerVisible && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) { bindingRoot.playerView.showController(); return true }
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
        if (isPanelOpen) { closeChannelPanel(); return }
        if (isLocked) return
        if (isTelevision || doubleBackToExitPressedOnce) { super.onBackPressed(); finish(); return }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, R.string.press_back_twice_exit_player, Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() { player?.release(); LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver); super.onDestroy() }
}
