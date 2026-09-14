package uk.dnpk.sekund

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

class SilentInstallReceiver : BroadcastReceiver() {
  @Suppress("DEPRECATION")
  override fun onReceive(context: Context, intent: Intent) {
    when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
      // Only expected if the app somehow isn't recognised as device owner by the
      // time the session commits; forward to the normal confirmation UI rather
      // than silently dropping the update.
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
          intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        confirmIntent?.let { context.startActivity(it) }
      }
      PackageInstaller.STATUS_SUCCESS ->
        Log.i("Sekund", "Silent update installed successfully")
      else ->
        Log.e("Sekund", "Silent update failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
    }
  }
}
