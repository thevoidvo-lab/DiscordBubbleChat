package com.example.discordbubble

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etToken: EditText
    private lateinit var etChannelId: EditText
    private lateinit var tvStatus: TextView

    companion object {
        const val PREFS_NAME = "discord_bubble_prefs"
        const val KEY_TOKEN = "bot_token"
        const val KEY_CHANNEL = "channel_id"
        const val NOTIF_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        etToken = findViewById(R.id.etToken)
        etChannelId = findViewById(R.id.etChannelId)
        tvStatus = findViewById(R.id.tvStatus)

        etToken.setText(prefs.getString(KEY_TOKEN, ""))
        etChannelId.setText(prefs.getString(KEY_CHANNEL, ""))

        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startBubbleService()
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIF_PERMISSION_CODE
                )
            }
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        tvStatus.text = if (hasOverlay) "Izin overlay: OK ✔" else "Izin overlay: belum diizinkan ✘"
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Izin overlay udah aktif", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBubbleService() {
        val token = etToken.text.toString().trim()
        val channelId = etChannelId.text.toString().trim()

        if (token.isEmpty() || channelId.isEmpty()) {
            Toast.makeText(this, "Isi token & channel ID dulu", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan overlay dulu (tombol 1)", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_CHANNEL, channelId)
            .apply()

        val serviceIntent = Intent(this, BubbleChatService::class.java)
        serviceIntent.putExtra("token", token)
        serviceIntent.putExtra("channelId", channelId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        Toast.makeText(this, "Bubble chat dijalankan, minimize app ini", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}