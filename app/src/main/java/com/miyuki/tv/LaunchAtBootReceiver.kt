package com.miyuki.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miyuki.tv.extra.Preferences

class LaunchAtBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Preferences().launchAtBoot) {
                val launch = Intent(context, SplashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launch)
            }
        }
    }
}
