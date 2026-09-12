package com.family.tracker.child

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SetupActivity : AppCompatActivity() {
    private val permissions = mutableListOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.INTERNET
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val prefs = getSharedPreferences("tracker", MODE_PRIVATE)
        val etParent = findViewById<EditText>(R.id.et_parent_number)
        val btnSave = findViewById<Button>(R.id.btn_save)

        etParent.setText(prefs.getString("parent", ""))

        btnSave.setOnClickListener {
            val number = etParent.text.toString().trim()
            if (number.isEmpty()) {
                Toast.makeText(this, "⚠️ Entre ton numéro de téléphone", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("parent", number).apply()
            Toast.makeText(this, "✅ Configuré !", Toast.LENGTH_LONG).show()
            finish()
        }

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, permissions, 1001)
        }
    }

    private fun hasPermissions(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
