package uk.dnpk.sekund.runtime.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import uk.dnpk.sekund.runtime.updates.InstallResult
import uk.dnpk.sekund.runtime.updates.UpdateInstaller
import java.io.File

/**
 * Installs a verified update APK with Android's PackageInstaller.
 *
 * - As **device owner** Android installs it without asking. Sekund is stopped, replaced and started again
 *   (the app's BootReceiver also listens for "my package was replaced").
 * - Otherwise Android shows its "Do you want to update this app?" screen, which someone has to accept.
 *   The first time, Android may also ask to allow installs from Sekund.
 *
 * How it works: write the APK into an install "session", commit it, and wait for Android's answer, which
 * arrives as a broadcast to a receiver registered just for this install.
 */
class AndroidUpdateInstaller(context: Context) : UpdateInstaller {
    private val log = Logger.withTag("Updates")
    private val appContext = context.applicationContext

    override suspend fun install(file: Path): InstallResult {
        val apk = File(file.toString())
        val installer = appContext.packageManager.packageInstaller
        val sessionId = withContext(Dispatchers.IO) { writeSession(installer, apk) }
        val answer = commitAndWait(installer, sessionId)
        return resultOf(answer)
    }

    /** Creates a session and copies the APK into it. Returns the session's id. */
    private fun writeSession(installer: PackageInstaller, apk: File): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(appContext.packageName)
        params.setSize(apk.length())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ can skip the prompt for an app updating itself, where it's allowed to.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("sekund-update.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
        } catch (error: Exception) {
            session.abandon()
            throw error
        } finally {
            session.close()
        }
        return sessionId
    }

    /** Commits the session and waits for Android's first answer (done, failed, or "ask the user"). */
    private suspend fun commitAndWait(installer: PackageInstaller, sessionId: Int): Intent {
        val action = "${appContext.packageName}.UPDATE_INSTALL_STATUS.$sessionId"
        val answer = CompletableDeferred<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                answer.complete(intent)
            }
        }
        registerPrivateReceiver(appContext, receiver, IntentFilter(action))
        try {
            // setPackage makes the intent explicit, which Android requires for a mutable PendingIntent.
            val statusIntent = Intent(action).setPackage(appContext.packageName)
            // Mutable: Android adds the status to the intent before sending it back.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(appContext, sessionId, statusIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
            installer.openSession(sessionId).use { session -> session.commit(pending.intentSender) }
            return answer.await()
        } finally {
            appContext.unregisterReceiver(receiver)
        }
    }

    private fun resultOf(answer: Intent): InstallResult {
        val status = answer.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                log.i { "Update installed; Sekund restarts" }
                return InstallResult.Installing
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                showConfirmation(answer)
                return InstallResult.WaitingForUser
            }
            else -> {
                val message = answer.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "error $status"
                throw IllegalStateException("Android didn't install the update: $message")
            }
        }
    }

    /** Opens Android's "update this app?" screen, which Android handed back in the answer. */
    @Suppress("DEPRECATION") // The typed getParcelableExtra needs Android 13.
    private fun showConfirmation(answer: Intent) {
        val confirm = answer.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            ?: throw IllegalStateException("Android asked for confirmation but didn't say how")
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(confirm)
    }
}
