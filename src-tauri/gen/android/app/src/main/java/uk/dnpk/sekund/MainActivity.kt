package uk.dnpk.sekund

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

class MainActivity : TauriActivity() {
  fun requestBatteryOptimizationExemption() {
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    val packageName = packageName
    
    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()

    WindowCompat.getInsetsController(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    super.onCreate(savedInstanceState)
    requestBatteryOptimizationExemption()
  }
}
