package uk.dnpk.sekund.runtime.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import co.touchlab.kermit.Logger

/**
 * Sekund's device-admin powers, and the kiosk set-up it applies as device owner.
 *
 * Two levels, both granted by the person setting the device up:
 * - **Device admin** (accepted once on screen, see AndroidDisplayControl.requestSetup): enough to turn
 *   the screen off.
 * - **Device owner** (`adb shell dpm set-device-owner uk.dnpk.sekund/.SekundDeviceAdminReceiver` on a
 *   freshly reset device; see docs/android.md): everything in [applyKioskPolicies], silent updates and
 *   Wi-Fi recovery. A device owner is always an active admin too.
 *
 * @param admin the app's DeviceAdminReceiver (declared in the app's manifest).
 */
class DeviceOwner(private val context: Context, val admin: ComponentName) {
    private val log = Logger.withTag("Kiosk")

    val policies: DevicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(): Boolean = policies.isDeviceOwnerApp(context.packageName)

    fun isAdminActive(): Boolean = policies.isAdminActive(admin)

    /**
     * Sets the device up as a dedicated Sekund display. Does nothing unless Sekund is the device owner.
     * Run at every start (cheap, and repairs anything changed by hand):
     *
     * - kiosk (lock task) allows only Sekund, Settings and the browsers,
     * - Sekund is the permanent Home app, so the device boots into it with no "choose an app" prompt,
     * - no lock screen, so turning the screen on lands straight on the clock,
     * - automatic brightness (the light sensor) and automatic time and time zone.
     *
     * @param homeActivity the Activity to make the Home app.
     */
    fun applyKioskPolicies(homeActivity: ComponentName) {
        if (!isDeviceOwner()) {
            log.i { "Not the device owner: kiosk set-up skipped" }
            return
        }
        tryPolicy("allow apps in kiosk mode") { allowKioskApps() }
        tryPolicy("make Sekund the Home app") { makeHomeApp(homeActivity) }
        tryPolicy("turn off the lock screen") { policies.setKeyguardDisabled(admin, true) }
        tryPolicy("turn on automatic brightness") { automaticBrightness() }
        tryPolicy("turn on automatic time") { automaticTime() }
    }

    private fun allowKioskApps() {
        val packages = mutableListOf(context.packageName, SETTINGS_PACKAGE)
        for (browser in browserPackages()) {
            if (browser !in packages) packages.add(browser)
        }
        policies.setLockTaskPackages(admin, packages.toTypedArray())

        // By default kiosk mode hides the status bar and notification shade completely. Keep Quick
        // Settings reachable (handy for Wi-Fi and debugging); Android only allows that together with
        // the Home button, which is harmless because Sekund *is* Home.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            policies.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW,
            )
        }
    }

    private fun makeHomeApp(homeActivity: ComponentName) {
        val home = IntentFilter(Intent.ACTION_MAIN)
        home.addCategory(Intent.CATEGORY_HOME)
        home.addCategory(Intent.CATEGORY_DEFAULT)
        policies.addPersistentPreferredActivity(admin, home, homeActivity)
    }

    private fun automaticBrightness() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        policies.setSystemSetting(
            admin,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC.toString(),
        )
    }

    @Suppress("DEPRECATION") // The newer setAutoTimePolicy needs Android 16; these work everywhere we run.
    private fun automaticTime() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            policies.setAutoTimeEnabled(admin, true)
            policies.setAutoTimeZoneEnabled(admin, true)
        } else {
            policies.setGlobalSetting(admin, Settings.Global.AUTO_TIME, "1")
            policies.setGlobalSetting(admin, Settings.Global.AUTO_TIME_ZONE, "1")
        }
    }

    /** Every installed browser. (The manifest's `<queries>` lets Sekund see them on Android 11+.) */
    private fun browserPackages(): List<String> {
        val browsers = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        val found = context.packageManager.queryIntentActivities(browsers, PackageManager.MATCH_ALL)
        val result = mutableListOf<String>()
        for (info in found) {
            val name = info.activityInfo.packageName
            if (name !in result) result.add(name)
        }
        return result
    }

    /** One policy failing (e.g. not supported on this device) shouldn't stop the others. */
    private fun tryPolicy(what: String, apply: () -> Unit) {
        try {
            apply()
        } catch (error: SecurityException) {
            log.w(error) { "Couldn't $what" }
        } catch (error: IllegalArgumentException) {
            log.w(error) { "Couldn't $what" }
        } catch (error: UnsupportedOperationException) {
            log.w(error) { "Couldn't $what" }
        }
    }

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
