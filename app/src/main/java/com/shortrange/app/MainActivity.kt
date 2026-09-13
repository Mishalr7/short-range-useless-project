package com.shortrange.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.shortrange.app.proximity.ProximityEngine
import com.shortrange.app.ui.navigation.ShortRangeNavHost
import com.shortrange.app.ui.theme.ShortRangeTheme
import com.shortrange.app.ui.theme.TechnicalWhite

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled; ProximityEngine inspects granted status on demand
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ProximityEngine and WebRtcCallManager singletons
        ProximityEngine.initialize(applicationContext)
        com.shortrange.app.webrtc.WebRtcCallManager.initialize(applicationContext)

        // Request runtime permissions (BLE + Microphone)
        requestPermissionsIfRequired()

        enableEdgeToEdge()
        setContent {
            ShortRangeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TechnicalWhite
                ) {
                    ShortRangeNavHost()
                }
            }
        }
    }

    private fun requestPermissionsIfRequired() {
        val permissionsToRequest = mutableListOf<String>()

        // Microphone permission for WebRTC voice calling
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // Bluetooth permissions for Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val requiredBlePermissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )

            for (perm in requiredBlePermissions) {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(perm)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
