package uk.dnpk.sekund.runtime.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build

/**
 * Registers [receiver] for broadcasts that only the system or Sekund itself sends.
 * (Android 13+ requires saying so; older versions don't have the flag.)
 */
fun registerPrivateReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        context.registerReceiver(receiver, filter)
    }
}
