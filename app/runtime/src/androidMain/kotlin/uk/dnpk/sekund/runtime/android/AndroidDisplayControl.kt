package uk.dnpk.sekund.runtime.android

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.runtime.platform.DisplayControl

/**
 * Screen on and off on Android.
 *
 * - **Off** is `DevicePolicyManager.lockNow()`, the same as a press of the power button. It needs Sekund to
 *   be a device admin, which someone accepts once on screen ([requestSetup]; the device owner has it already).
 * - **On** briefly holds a wake lock that wakes the screen. With the lock screen turned off (device owner),
 *   that lands straight back on the clock.
 *
 * [isOn] follows the system's screen-on/off broadcasts, so it's also right after a timeout or the power button.
 */
class AndroidDisplayControl(
    context: Context,
    private val owner: DeviceOwner,
    private val screen: AppScreen,
) : DisplayControl {
    private val log = Logger.withTag("Display")
    private val appContext = context.applicationContext
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val screenOn = MutableStateFlow(power.isInteractive)

    override val isOn: StateFlow<Boolean> = screenOn

    init {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
            }
        }
        // These broadcasts are only delivered to receivers registered in code, never from the manifest.
        registerPrivateReceiver(appContext, receiver, filter)
    }

    override fun isReady(): Boolean = owner.isAdminActive()

    override fun requestSetup() {
        if (isReady()) return
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, owner.admin)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets Sekund turn the screen on and off, e.g. at night or from Home Assistant.")
        val shown = screen.runOnScreen { activity -> activity.startActivity(intent) }
        if (!shown) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
    }

    override suspend fun turnOff() {
        check(isReady()) { "Display control isn't set up: choose \"Enable display control\" in the long-press menu on the device" }
        owner.policies.lockNow()
        log.i { "Screen off" }
    }

    @Suppress("DEPRECATION") // FULL_WAKE_LOCK is deprecated for keeping the screen on, but it's the only way to *wake* it.
    override suspend fun turnOn() {
        val flags = PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE
        val wakeLock = power.newWakeLock(flags, "Sekund:DisplayOn")
        wakeLock.acquire(WAKE_FOR_MILLIS)
        // Releasing straight away is fine: ON_AFTER_RELEASE keeps the screen on for the normal timeout.
        wakeLock.release()
        log.i { "Screen on" }
    }

    private companion object {
        const val WAKE_FOR_MILLIS = 10_000L
    }
}
