package uk.dnpk.sekund.runtime.android

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import co.touchlab.kermit.Logger
import uk.dnpk.sekund.runtime.platform.KioskControl

/**
 * Kiosk mode on Android: "lock task mode", which pins Sekund to the screen. Only offered when Sekund is the
 * device owner; then the allowed apps are set by DeviceOwner.applyKioskPolicies and no prompt is shown.
 *
 * The screen enters kiosk mode by itself each time it appears ([enterIfWanted], called from the Activity's
 * onResume), unless someone used "Exit kiosk"; that lasts until "Enter kiosk" or the app restarts.
 */
class AndroidKiosk(context: Context, private val screen: AppScreen) : KioskControl {
    private val log = Logger.withTag("Kiosk")
    private val activities = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /** Set by "Exit kiosk", so coming back to the screen doesn't lock it again straight away. */
    @Volatile
    private var exitedByUser = false

    override fun isLocked(): Boolean = activities.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    override fun enter() {
        exitedByUser = false
        val done = screen.runOnScreen { activity -> enterIfWanted(activity) }
        if (!done) log.i { "No screen yet: kiosk mode starts when it appears" }
    }

    override fun exit() {
        exitedByUser = true
        screen.runOnScreen { activity ->
            activity.stopLockTask()
            log.i { "Left kiosk mode" }
        }
    }

    /** Locks [activity] to the screen unless someone chose "Exit kiosk". Call from the Activity's onResume. */
    fun enterIfWanted(activity: Activity) {
        if (exitedByUser || isLocked()) return
        try {
            activity.startLockTask()
            log.i { "In kiosk mode" }
        } catch (error: IllegalStateException) {
            log.w(error) { "Couldn't enter kiosk mode" }
        }
    }
}
