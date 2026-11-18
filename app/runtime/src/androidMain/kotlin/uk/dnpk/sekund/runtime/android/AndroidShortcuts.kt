package uk.dnpk.sekund.runtime.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import co.touchlab.kermit.Logger

/** The long-press menu's "Browser" and "System settings" buttons. Both apps are allowed in kiosk mode. */
class AndroidShortcuts(context: Context) {
    private val log = Logger.withTag("Menu")
    private val appContext = context.applicationContext

    fun openBrowser() {
        val browser = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        if (start(browser)) return
        // No app says it's a browser: open a web page and let Android pick.
        start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
    }

    fun openSystemSettings() {
        start(Intent(Settings.ACTION_SETTINGS))
    }

    private fun start(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
            return true
        } catch (error: ActivityNotFoundException) {
            log.w { "Nothing on this device can open ${intent.action}" }
            return false
        }
    }
}
