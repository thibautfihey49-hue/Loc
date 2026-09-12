package com.family.tracker.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log

class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
            for (pdu in pdus) {
                val msg = SmsMessage.createFromPdu(pdu as ByteArray)
                val text = msg.messageBody?.trim() ?: continue
                val sender = msg.originatingAddress ?: continue
                val prefs = context.getSharedPreferences("tracker", Context.MODE_PRIVATE)
                val parent = prefs.getString("parent", "")?.replace(" ", "") ?: ""
                if (parent.isNotEmpty() && !sender.replace(" ", "").endsWith(parent.takeLast(9))) return

                when (text) {
                    "STREAM_ON" -> {
                        context.startForegroundService(Intent(context, MJPEGServer::class.java)
                            .setAction(MJPEGServer.START))
                        abortBroadcast()
                    }
                    "STREAM_OFF" -> {
                        context.startForegroundService(Intent(context, MJPEGServer::class.java)
                            .setAction(MJPEGServer.STOP))
                        abortBroadcast()
                    }
                    "STREAM_SWITCH" -> {
                        context.startForegroundService(Intent(context, MJPEGServer::class.java)
                            .setAction(MJPEGServer.SWITCH))
                        abortBroadcast()
                    }
                }
            }
        } catch (e: Exception) { Log.e("CMD", "Erreur: ${e.message}") }
    }
}
