package com.downloadchecker.app.data

import android.content.Context
import org.json.JSONArray

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("dac", Context.MODE_PRIVATE)

    var sessionToken: String?
        get() = prefs.getString("token", null)
        set(v) { prefs.edit().putString("token", v).apply() }

    var telegramId: Long
        get() = prefs.getLong("tid", 0L)
        set(v) { prefs.edit().putLong("tid", v).apply() }

    var username: String
        get() = prefs.getString("uname", "") ?: ""
        set(v) { prefs.edit().putString("uname", v).apply() }

    var stage: String
        get() = prefs.getString("stage", "TASK1") ?: "TASK1"
        set(v) { prefs.edit().putString("stage", v).apply() }

    var monitoringStartedAt: Long
        get() = prefs.getLong("mon_start", 0L)
        set(v) { prefs.edit().putLong("mon_start", v).apply() }

    var rewardTask1: Int
        get() = prefs.getInt("r1", 100)
        set(v) { prefs.edit().putInt("r1", v).apply() }

    var rewardTask2: Int
        get() = prefs.getInt("r2", 150)
        set(v) { prefs.edit().putInt("r2", v).apply() }

    fun deviceId(context: Context): String {
        val existing = prefs.getString("device", null)
        if (existing != null) return existing
        val id = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("device", id).apply()
        return id
    }

    fun saveBaseline(packages: Set<String>) {
        prefs.edit().putString("baseline", JSONArray(packages.toList()).toString()).apply()
    }

    fun baseline(): Set<String> {
        val raw = prefs.getString("baseline", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun counted(task: Int): MutableSet<String> {
        val raw = prefs.getString("counted_$task", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
    }

    fun addCounted(task: Int, pkg: String) {
        val set = counted(task)
        set.add(pkg)
        prefs.edit().putString("counted_$task", JSONArray(set.toList()).toString()).apply()
    }

    fun clearCounted(task: Int) {
        prefs.edit().putString("counted_$task", "[]").apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = !sessionToken.isNullOrBlank()
}
