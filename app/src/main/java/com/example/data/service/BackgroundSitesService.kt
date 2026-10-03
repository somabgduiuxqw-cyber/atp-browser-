package com.example.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BackgroundSitesService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeCount = 0

    companion object {
        const val CHANNEL_ID = "atp_background_sites_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_PAUSE_ALL = "com.example.ACTION_PAUSE_ALL"
        const val ACTION_STOP_ALL = "com.example.ACTION_STOP_ALL"
        const val ACTION_START = "com.example.ACTION_START"

        fun start(context: Context) {
            val intent = Intent(context, BackgroundSitesService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundSitesService::class.java).apply {
                action = ACTION_STOP_ALL
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(activeCount))

        serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext).browserDao()
            db.getAllBackgroundSites().collectLatest { sites ->
                val runningSites = sites.filter { it.status == "Running" }
                activeCount = runningSites.size
                if (activeCount == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val notification = buildNotification(activeCount)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> {
                serviceScope.launch {
                    val db = AppDatabase.getInstance(applicationContext).browserDao()
                    db.getAllBackgroundSites().collectLatest { sites ->
                        for (site in sites) {
                            if (site.status == "Running") {
                                db.updateBackgroundSite(site.copy(status = "Suspended"))
                            }
                        }
                    }
                }
            }
            ACTION_STOP_ALL -> {
                serviceScope.launch {
                    val db = AppDatabase.getInstance(applicationContext).browserDao()
                    db.getAllBackgroundSites().collectLatest { sites ->
                        for (site in sites) {
                            db.updateBackgroundSite(site.copy(status = "Stopped"))
                        }
                    }
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ATP Browser Background Sites",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing notification for Keep Alive background websites"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(count: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseAllIntent = Intent(this, BackgroundSitesService::class.java).apply {
            action = ACTION_PAUSE_ALL
        }
        val pauseAllPendingIntent = PendingIntent.getService(
            this,
            1,
            pauseAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAllIntent = Intent(this, BackgroundSitesService::class.java).apply {
            action = ACTION_STOP_ALL
        }
        val stopAllPendingIntent = PendingIntent.getService(
            this,
            2,
            stopAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "ATP Browser"
        val content = if (count == 1) "1 background site active" else "$count background sites active"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open", openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause All", pauseAllPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop All", stopAllPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
