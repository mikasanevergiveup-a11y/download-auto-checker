package com.downloadchecker.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.downloadchecker.app.util.InstallTracker

class PackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (replacing && action == Intent.ACTION_PACKAGE_ADDED) return
        InstallTracker.onNewPackage(context, pkg)
        context.sendBroadcast(Intent("com.downloadchecker.app.PROGRESS").setPackage(context.packageName))
    }
}
