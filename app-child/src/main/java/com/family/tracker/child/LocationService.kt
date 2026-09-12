package com.family.tracker.child

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LocationService : Service() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("LocationService", "Localisation", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(2, NotificationCompat.Builder(this, "LocationService")
            .setContentTitle("Système Services")
            .setContentText("Actif")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setSilent(true)
            .setOngoing(true)
            .build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
