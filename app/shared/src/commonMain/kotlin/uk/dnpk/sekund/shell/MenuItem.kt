package uk.dnpk.sekund.shell

/**
 * One button in the long-press menu. App builds the list each time the menu opens, so it only
 * shows what this device can do right now (e.g. "Exit kiosk" only while in kiosk mode).
 */
data class MenuItem(val label: String, val onClick: () -> Unit)
