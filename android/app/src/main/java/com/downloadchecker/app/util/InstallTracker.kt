package com.downloadchecker.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.downloadchecker.app.data.SessionStore

object InstallTracker {
    fun snapshotPackages(pm: PackageManager): Set<String> {
        val out = LinkedHashSet<String>()
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            out.add(app.packageName)
        }
        return out
    }

    fun onNewPackage(context: Context, packageName: String): Int {
        val store = com.downloadchecker.app.App.instance.session
        if (!store.isLoggedIn()) return -1
        if (store.monitoringStartedAt <= 0L) return -1
        if (packageName == context.packageName) return -1
        if (store.baseline().contains(packageName)) return -1

        val stage = store.stage
        val task = when (stage) {
            "TASK1" -> 1
            "TASK2" -> 2
            else -> return -1
        }
        val set = store.counted(task)
        if (set.contains(packageName)) return set.size
        // also ignore if counted in the other task
        if (store.counted(1).contains(packageName) || store.counted(2).contains(packageName)) return set.size

        store.addCounted(task, packageName)
        return store.counted(task).size
    }

    fun progress(task: Int): Int = com.downloadchecker.app.App.instance.session.counted(task).size

    fun startMonitoring(context: Context) {
        val store = com.downloadchecker.app.App.instance.session
        val baseline = snapshotPackages(context.packageManager)
        store.saveBaseline(baseline)
        store.monitoringStartedAt = System.currentTimeMillis()
    }
}
