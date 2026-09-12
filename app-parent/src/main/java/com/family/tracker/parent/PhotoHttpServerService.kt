package com.family.tracker.parent

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import fi.iki.elonen.NanoHTTPD
import java.io.File

class PhotoHttpServerService : Service() {
    private var server: PhotoServer? = null

    inner class PhotoServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            if (session.method == Method.POST && session.uri == "/upload") {
                try {
                    val files = session.parseBody(mapOf())
                    val path = files["photo"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No file")
                    val file = File(path)
                    val bytes = file.readBytes()
                    val intent = Intent("PHOTO_RECEIVED").setPackage(packageName)
                    intent.putExtra("photo_data", bytes)
                    sendBroadcast(intent)
                    file.delete()
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK")
                } catch (e: Exception) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message)
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    override fun onCreate() {
        super.onCreate()
        server = PhotoServer(8904)
        try {
            server?.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Port 8904 occupé", Toast.LENGTH_SHORT).show()
        }
        val channel = NotificationChannel("photo_server", "Serveur Photo", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(2, Notification.Builder(this, "photo_server")
            .setContentTitle("Serveur photo actif")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }
}
