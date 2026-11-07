package com.downloadchecker.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.downloadchecker.app.App

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!App.instance.session.isLoggedIn()) return
        context.startForegroundService(Intent(context, InstallMonitorService::class.java))
    }
}
