package com.family.tracker.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log

class CommandReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CMD"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
            for (pdu in pdus) {
                val msg = SmsMessage.createFromPdu(pdu as ByteArray)
                val text = msg.messageBody?.trim() ?: continue
                val sender = msg.originatingAddress ?: continue

                val prefs = context.getSharedPreferences("tracker", Context.MODE_PRIVATE)
                val parentNum = prefs.getString("parent", "")?.replace(" ", "") ?: ""
                val senderClean = sender.replace(" ", "")
                
                if (parentNum.isNotEmpty() && !senderClean.endsWith(parentNum.takeLast(9))) {
                    Log.d(TAG, "❌ Numéro non autorisé")
                    return
                }

                when {
                    text == "VIDEO_ON" -> {
                        Log.d(TAG, "🎥 Démarrage caméra")
                        val i = Intent(context, WebRTCService::class.java)
                        i.action = WebRTCService.ACTION_START
                        context.startForegroundService(i)
                        abortBroadcast()
                    }
                    text == "VIDEO_OFF" -> {
                        Log.d(TAG, "🛑 Arrêt caméra")
                        val i = Intent(context, WebRTCService::class.java)
                        i.action = WebRTCService.ACTION_STOP
                        context.startForegroundService(i)
                        abortBroadcast()
                    }
                    text == "VIDEO_SWITCH" -> {
                        Log.d(TAG, "🔄 Changement caméra")
                        val i = Intent(context, WebRTCService::class.java)
                        i.action = WebRTCService.ACTION_SWITCH
                        context.startForegroundService(i)
                        abortBroadcast()
                    }
                    text.startsWith("OFFER:") || text.startsWith("ANSWER:") || text.startsWith("ICE:") -> {
                        Log.d(TAG, "📥 Signal WebRTC reçu")
                        WebRTCService.SignalingHandler().receiveSignal(text)
                        abortBroadcast()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur: ${e.message}")
        }
    }
}
