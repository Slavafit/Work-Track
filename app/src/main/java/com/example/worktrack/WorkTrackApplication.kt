package com.example.worktrack

import android.app.Application

class WorkTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
    }
}
