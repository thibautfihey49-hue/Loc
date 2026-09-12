package com.family.tracker.parent

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.net.NetworkInterface
import java.util.*

class MapActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private var marker: Marker? = null
    private lateinit var statusText: TextView
    private lateinit var serverUrl: TextView
    private lateinit var photoView: ImageView

    private val parentNumber = "+33600000000"

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra("lat", 0.0) ?: return
            val lon = intent?.getDoubleExtra("lon", 0.0) ?: return
            if (lat == 0.0 && lon == 0.0) return
            runOnUiThread {
                val pos = GeoPoint(lat, lon)
                marker?.position = pos
                map.controller.animateTo(pos)
                map.controller.setZoom(17.0)
                map.invalidate()
                val sdf = java.text.SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
                statusText.text = "✅ Position: $lat, $lon - ${sdf.format(Date())}"
            }
        }
    }

    private val photoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getByteArrayExtra("photo_data") ?: return
            runOnUiThread {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                photoView.setImageBitmap(bitmap)
                photoView.visibility = android.view.View.VISIBLE
                Toast.makeText(this@MapActivity, "📷 Photo reçue !", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = Configuration.getInstance()
        config.userAgentValue = "TrackerParent/1.0"
        config.osmdroidBasePath = File(cacheDir, "osmdroid")
        config.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        setContentView(R.layout.activity_map)

        map = findViewById(R.id.map)
        statusText = findViewById(R.id.status)
        serverUrl = findViewById(R.id.serverUrl)
        photoView = findViewById(R.id.receivedPhoto)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(47.4736, -0.5517))
        marker = Marker(map).apply {
            position = GeoPoint(47.4736, -0.5517)
            title = "Enfant"
        }
        map.overlays.add(marker)

        startService(Intent(this, PhotoHttpServerService::class.java))
        val ip = getLocalIPAddress()
        serverUrl.text = "Serveur photo: $ip:8904"
        serverUrl.setBackgroundColor(0xFF008800.toInt())

        findViewById<FloatingActionButton>(R.id.fabCamera).setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS), 101)
                return@setOnClickListener
            }
            try {
                SmsManager.getDefault().sendDataMessage(parentNumber, null, 8902.toShort(), "TAKE_PHOTO".toByteArray(), null, null)
                Toast.makeText(this, "📷 Commande envoyée", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<FloatingActionButton>(R.id.fabFloat).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Autorisez Afficher par-dessus", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startForegroundService(Intent(this, FloatingMapService::class.java))
                Toast.makeText(this, "Mini-carte activée", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getLocalIPAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {}
        return "???.???.???.???"
    }

    override fun onResume() {
        super.onResume()
        try { map.onResume() } catch (_: Exception) {}
        registerReceiver(locationReceiver, IntentFilter("TRACKER_UPDATE"), RECEIVER_NOT_EXPORTED)
        registerReceiver(photoReceiver, IntentFilter("PHOTO_RECEIVED"), RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { map.onPause() } catch (_: Exception) {}
        try { unregisterReceiver(locationReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(photoReceiver) } catch (_: Exception) {}
    }
}
