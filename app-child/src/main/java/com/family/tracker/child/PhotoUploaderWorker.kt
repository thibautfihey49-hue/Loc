package com.family.tracker.child

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PhotoUploaderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("tracker", Context.MODE_PRIVATE)
        val parentIp = prefs.getString("parent_ip", "") ?: ""
        if (parentIp.isEmpty()) return Result.success()
        if (!isWiFi(applicationContext)) return Result.retry()

        val db = DbProvider.get(applicationContext)
        val unsent = db.photoDao().getUnsent()

        for (photo in unsent) {
            val file = File(photo.photoPath)
            if (!file.exists()) {
                db.photoDao().markAsSent(photo.copy(sent = true))
                continue
            }
            try {
                val requestBody = MultipartBody.Builder()
                    .addFormDataPart("photo", file.name, 
                        okhttp3.RequestBody.create("image/jpeg".toMediaType(), file))
                    .build()
                
                val req = Request.Builder()
                    .url("http://$parentIp:8904/upload")
                    .post(requestBody)
                    .build()
                
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        db.photoDao().markAsSent(photo.copy(sent = true))
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                return Result.retry()
            }
        }
        return Result.success()
    }

    private fun isWiFi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
