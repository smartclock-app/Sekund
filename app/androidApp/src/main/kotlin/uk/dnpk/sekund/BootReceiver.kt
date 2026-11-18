package uk.dnpk.sekund

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Opens Sekund when the device finishes starting up (as the device owner's Home app it would appear
 * anyway; this covers devices where it isn't Home), and again after an update has replaced it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val booted = intent.action == Intent.ACTION_BOOT_COMPLETED
        val updated = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!booted && !updated) return
        val open = Intent(context, MainActivity::class.java)
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(open)
    }
}
