package com.downloadchecker.app

import android.app.Application
import com.downloadchecker.app.data.SessionStore

class App : Application() {
    lateinit var session: SessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = SessionStore(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
