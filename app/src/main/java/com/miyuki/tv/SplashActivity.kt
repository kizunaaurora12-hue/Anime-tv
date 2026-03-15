package com.miyuki.tv

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.miyuki.tv.databinding.ActivitySplashBinding
import com.miyuki.tv.extension.toStringContributor
import com.miyuki.tv.extension.toRequest
import com.miyuki.tv.extension.mergeWith
import com.miyuki.tv.extra.HttpClient
import com.miyuki.tv.extra.LocaleHelper
import com.miyuki.tv.extra.Preferences
import com.miyuki.tv.extra.SourcesReader
import com.miyuki.tv.model.GithubUser
import com.miyuki.tv.model.Playlist
import com.miyuki.tv.model.Release
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val preferences = Preferences()

    override fun attachBaseContext(base: android.content.Context) {
        val lang = LocaleHelper.getLanguageCode(base)
        super.attachBaseContext(LocaleHelper.setLocale(base, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PlayerActivity.isPipMode) {
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
            finish()
            return
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textUsers.text = preferences.contributors
        animateLoadingBar()

        // Fetch contributors in background
        HttpClient(true)
            .create(getString(R.string.gh_contributors).toRequest())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val content = response.body()?.string()
                        if (!content.isNullOrBlank() && response.isSuccessful) {
                            val ghUsers = Gson().fromJson(content, Array<GithubUser>::class.java)
                            val users   = ghUsers.toStringContributor()
                            preferences.contributors = users
                            runOnUiThread { binding.textUsers.text = users }
                        }
                    } catch (e: Exception) {
                        Log.e("SplashActivity", "Contributors parse failed", e)
                    }
                }
            })

        if (preferences.isFirstTime) {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.app_name)
                setMessage(R.string.alert_first_time)
                setCancelable(false)
                setPositiveButton(android.R.string.ok) { di, _ ->
                    preferences.isFirstTime = false
                    prepareWhatIsNeeded()
                    di.dismiss()
                }
                setNeutralButton(R.string.button_website) { _, _ ->
                    preferences.isFirstTime = false
                    openWebsite(getString(R.string.website))
                    finish()
                }
                create().show()
            }
        } else {
            prepareWhatIsNeeded()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkNewRelease()
        if (requestCode != 260621) return
        if (!grantResults.contains(PackageManager.PERMISSION_DENIED)) return
        Toast.makeText(this, getString(R.string.must_allow_permissions), Toast.LENGTH_LONG).show()
    }

    private fun prepareWhatIsNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setStatus(R.string.status_checking_permission)
            val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            var passes = true
            for (perm in permissions) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(permissions, 260621)
                    passes = false
                    break
                }
            }
            if (!passes) return
        }
        checkNewRelease()
    }

    private fun checkNewRelease() {
        setStatus(R.string.status_checking_new_update)
        HttpClient(true)
            .create(getString(R.string.json_release).toRequest())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("SplashActivity", "Update check failed", e)
                    launchMainActivity()
                }
                override fun onResponse(call: Call, response: Response) {
                    val content = response.body()?.string()
                    if (!response.isSuccessful || content.isNullOrBlank()) return launchMainActivity()
                    try {
                        val release = Gson().fromJson(content, Release::class.java)
                        if (release.versionCode <= BuildConfig.VERSION_CODE ||
                            release.versionCode <= preferences.ignoredVersion) {
                            return launchMainActivity()
                        }
                        val msg         = buildUpdateMessage(release)
                        val downloadUrl = if (release.downloadUrl.isBlank())
                            String.format(
                                getString(R.string.apk_release),
                                release.versionName, release.versionName, release.versionCode
                            )
                        else release.downloadUrl

                        runOnUiThread {
                            AlertDialog.Builder(this@SplashActivity).apply {
                                setTitle(R.string.alert_new_update)
                                setMessage(msg)
                                setCancelable(false)
                                setPositiveButton(R.string.dialog_download) { _, _ ->
                                    downloadFile(downloadUrl); launchMainActivity()
                                }
                                setNegativeButton(R.string.dialog_ignore) { _, _ ->
                                    preferences.ignoredVersion = release.versionCode
                                    launchMainActivity()
                                }
                                setNeutralButton(R.string.button_website) { _, _ ->
                                    openWebsite(getString(R.string.website))
                                    launchMainActivity()
                                }
                                create().show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SplashActivity", "Release parse failed", e)
                        launchMainActivity()
                    }
                }
            })
    }

    private fun buildUpdateMessage(release: Release): String {
        val sb = StringBuilder(
            String.format(
                getString(R.string.message_update),
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                release.versionName, release.versionCode
            )
        )
        for (log in release.changelog)
            sb.append(String.format(getString(R.string.message_update_changelog), log))
        if (release.changelog.isEmpty())
            sb.append(getString(R.string.message_update_no_changelog))
        return sb.toString()
    }

    private fun setStatus(resId: Int) {
        runOnUiThread { binding.textStatus.setText(resId) }
    }

    private fun launchMainActivity() {
        val playlistSet = Playlist()
        setStatus(R.string.status_preparing_playlist)
        SourcesReader().set(preferences.sources, object : SourcesReader.Result {
            override fun onError(source: String, error: String) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "[$error] $source", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(playlist: Playlist?) {
                if (playlist != null) playlistSet.mergeWith(playlist)
            }
            override fun onFinish() {
                Playlist.cached = playlistSet
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(applicationContext, MainActivity::class.java))
                    finish()
                }, 900)
            }
        }).process(true)
    }

    private fun animateLoadingBar() {
        val handler  = Handler(Looper.getMainLooper())
        var progress = 0
        val runnable = object : Runnable {
            override fun run() {
                if (!isDestroyed && ::binding.isInitialized) {
                    progress = minOf(progress + 2, 100)
                    val parent      = binding.loadingBar.parent as? android.widget.FrameLayout
                    val parentWidth = parent?.width ?: 0
                    if (parentWidth > 0) {
                        val params = binding.loadingBar.layoutParams
                        params.width = parentWidth * progress / 100
                        binding.loadingBar.layoutParams = params
                    }
                    if (progress < 100) handler.postDelayed(this, 40)
                }
            }
        }
        handler.postDelayed(runnable, 200)
    }

    private fun openWebsite(link: String) {
        startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(link)))
    }

    private fun downloadFile(url: String) {
        try {
            val uri     = Uri.parse(url)
            val dm      = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(uri)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, uri.lastPathSegment
                )
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
            dm.enqueue(request)
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

}
