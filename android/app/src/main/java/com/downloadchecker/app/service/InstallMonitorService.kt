package com.downloadchecker.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.downloadchecker.app.util.InstallTracker
import kotlinx.coroutines.*

class InstallMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channelId = "monitor"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(channelId, "Install monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val n: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Download Checker")
            .setContentText("Monitoring installs")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        startForeground(42, n)

        scope.launch {
            while (isActive) {
                val current = InstallTracker.snapshotPackages(packageManager)
                val store = com.downloadchecker.app.App.instance.session
                val baseline = store.baseline()
                for (pkg in current) {
                    if (!baseline.contains(pkg)) {
                        InstallTracker.onNewPackage(this@InstallMonitorService, pkg)
                    }
                }
                delay(15_000)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
