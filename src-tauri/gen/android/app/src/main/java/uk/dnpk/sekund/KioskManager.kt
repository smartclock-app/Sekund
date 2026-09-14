package uk.dnpk.sekund

import android.app.Activity
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

// Bridges the Device Owner APIs used for kiosk pinning and silent updates. These are
// awkward to drive over raw JNI from Rust (PackageInstaller sessions in particular need
// Java streams and a callback), so Rust looks this class up via the context's class
// loader and calls these static methods directly instead.
object KioskManager {
    @JvmStatic
    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    // Pins the screen to this app plus whatever browser(s) and Settings are installed,
    // so nothing else on the device is reachable. Requires the app to already be the
    // device owner (set up once via `adb shell dpm set-device-owner`).
    @JvmStatic
    fun enableLockTask(activity: Activity) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(activity.packageName)) {
            throw IllegalStateException("Sekund is not the device owner")
        }

        val admin = ComponentName(activity, SekundDeviceAdminReceiver::class.java)
        val packages = (listOf(activity.packageName, "com.android.settings") + resolveBrowserPackages(activity)).distinct()

        dpm.setLockTaskPackages(admin, packages.toTypedArray())
        activity.startLockTask()
    }

    @JvmStatic
    fun disableLockTask(activity: Activity) {
        activity.stopLockTask()
    }

    // Silently and permanently registers this activity as the preferred handler for
    // the HOME intent, so the device boots straight into Sekund without ever showing
    // the "Complete action using" chooser or requiring a manual "set as launcher"
    // step in Settings. Persists across reboots; requires device owner.
    @JvmStatic
    fun setAsPersistentHome(activity: Activity) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(activity.packageName)) {
            throw IllegalStateException("Sekund is not the device owner")
        }

        val admin = ComponentName(activity, SekundDeviceAdminReceiver::class.java)
        val activityComponent = ComponentName(activity, MainActivity::class.java)

        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        dpm.addPersistentPreferredActivity(admin, filter, activityComponent)
    }

    // Installs an update APK, silently via PackageInstaller when the app is the device
    // owner (skipping the "install unknown app" dialog entirely), or via the normal
    // ACTION_INSTALL_PACKAGE confirmation flow otherwise.
    @JvmStatic
    fun installApk(activity: Activity, path: String) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(activity.packageName)) {
            installApkSilently(activity, path)
        } else {
            installApkWithConfirmation(activity, path)
        }
    }

    private fun installApkWithConfirmation(activity: Activity, path: String) {
        val file = File(path)
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }

        activity.startActivity(intent)
    }

    private fun installApkSilently(context: Context, path: String) {
        val file = File(path)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setSize(file.length())

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        session.openWrite("sekund_update", 0, file.length()).use { output ->
            FileInputStream(file).use { input -> input.copyTo(output) }
            session.fsync(output)
        }

        val receiverIntent = Intent(context, SilentInstallReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        session.commit(pendingIntent.intentSender)
        session.close()
    }

    private fun resolveBrowserPackages(context: Context): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .distinct()
    }
}
