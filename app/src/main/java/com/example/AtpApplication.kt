package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.db.AppDatabase
import com.example.data.preferences.BrowserPreferences

class AtpApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: BrowserPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferences = BrowserPreferences(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val backgroundChannel = NotificationChannel(
                "atp_background_sites_channel",
                "ATP Background Sites",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Keep Alive background websites status"
            }

            val downloadsChannel = NotificationChannel(
                "atp_downloads_channel",
                "ATP Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Download progress and completion alerts"
            }

            nm?.createNotificationChannel(backgroundChannel)
            nm?.createNotificationChannel(downloadsChannel)
        }
    }
}
