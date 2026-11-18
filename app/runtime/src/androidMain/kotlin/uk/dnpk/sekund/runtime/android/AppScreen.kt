package uk.dnpk.sekund.runtime.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Keeps hold of Sekund's screen (its one Activity) while it exists.
 *
 * Some Android calls only work on an Activity (entering and leaving kiosk mode), but the services that
 * need them live as long as the app, longer than any one Activity (Android recreates it, e.g. on
 * rotation). Register this with `Application.registerActivityLifecycleCallbacks` and it always points at
 * the current one. It's a weak reference so a finished Activity can still be freed.
 */
class AppScreen : Application.ActivityLifecycleCallbacks {
    private var current: WeakReference<Activity>? = null

    /** The screen, or null while there isn't one (e.g. the app was started in the background at boot). */
    fun activity(): Activity? = current?.get()

    /** Runs [action] on the screen's main thread, if there is a screen. Returns false if there isn't. */
    fun runOnScreen(action: (Activity) -> Unit): Boolean {
        val activity = activity() ?: return false
        activity.runOnUiThread { action(activity) }
        return true
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        current = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
