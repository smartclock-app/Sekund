# Sekund on Android

Sekund runs on any Android 7+ tablet or TV box. It works as an ordinary app, but it's meant to be the
device's only job. Making it the **device owner** turns the tablet into a dedicated display.

## What you get at each level

| | Ordinary app | + Display control | + Device owner |
|---|---|---|---|
| Clock, widgets, device API, offline badge | ✓ | ✓ | ✓ |
| Starts when the device boots | ✓ | ✓ | ✓ |
| `display.off` (and `display.on`) | | ✓ | ✓ |
| Kiosk: locked to Sekund, Settings and browsers | | | ✓ |
| Permanent Home app, no lock screen | | | ✓ |
| Automatic brightness, time and time zone | | | ✓ |
| Wi-Fi reconnects by itself when stuck | | | ✓ |
| Updates install without asking | | | ✓ |

## Display control (one tap)

Long-press the screen → **Enable display control** → **Activate**. This makes Sekund a *device admin* that may
only lock the screen. The button disappears once it's done. A device owner already has this.

`display.off` works like a press of the power button. `display.on` wakes the screen. The companion and Home
Assistant can call both (see [the device API](api/device-api.md#actions)). A plugin that publishes
`PresenceDetected` (e.g. a motion sensor) also turns the screen on.

## Making Sekund the device owner

Android only allows this on a device **with no accounts**, so it's done straight after a factory reset:

1. Factory-reset the tablet and go through set-up **without signing in to a Google account** (skip it).
2. Turn on *Developer options* (tap *Build number* seven times) and *USB debugging* (or *Wireless debugging*).
3. Install Sekund (`adb install sekund.apk`, or the release APK).
4. Run:

   ```sh
   adb shell dpm set-device-owner uk.dnpk.sekund/.SekundDeviceAdminReceiver
   ```

5. Open Sekund (or reboot). It sets up kiosk mode by itself, at every start.

Some manufacturers (and devices with a work profile) refuse step 4. The error message says why: usually an
account is still signed in (*Settings → Accounts*).

### Kiosk mode

Sekund is pinned to the screen. Only Sekund, Settings and the installed browsers can be opened, from the
long-press menu (**Browser**, **System settings**). The status bar still shows, so Quick Settings (Wi-Fi,
debugging) stay reachable. The Home button just comes back to Sekund.

To use the tablet for something else, long-press → **Exit kiosk**. It stays unlocked until **Enter kiosk**
or the app restarts. The companion can do the same with `kiosk.exit` / `kiosk.enter`.

### Removing device owner

A device owner can't be uninstalled or force-stopped from Settings. To undo it, factory-reset the device.

## Files

Config, widgets, auth providers and logs are in `/sdcard/Android/data/uk.dnpk.sekund/files/`. Reach them with
`adb pull` / `adb push` or USB file transfer. Secrets stay in the app's private storage, encrypted with a key in
the Android Keystore, and are never backed up.

## Network

The **Offline** badge (bottom corner) appears when `connectivitycheck.gstatic.com` stops answering, which is
the same check Android itself uses. It's checked every minute, more often while offline, and straight away
when Android reports a network change. Everything refreshes when the connection comes back. As device owner,
Sekund asks Wi-Fi to reconnect while it's offline (unless Wi-Fi was switched off).
