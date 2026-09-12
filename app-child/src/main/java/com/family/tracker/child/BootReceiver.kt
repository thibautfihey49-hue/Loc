package com.family.tracker.child; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent
class BootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent?){val p=c.getSharedPreferences("tracker",Context.MODE_PRIVATE);if(p.getBoolean("setup_done",false)){c.startForegroundService(Intent(c,LocationService::class.java))}}}
