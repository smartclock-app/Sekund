package uk.dnpk.sekund

import android.app.admin.DeviceAdminReceiver

/**
 * Makes Sekund a device admin (policies in res/xml/device_admin_receiver.xml), and the component
 * named when making it device owner:
 * `adb shell dpm set-device-owner uk.dnpk.sekund/.SekundDeviceAdminReceiver`.
 *
 * It needs no code: Android only needs the class to exist. The work is in runtime's DeviceOwner.
 */
class SekundDeviceAdminReceiver : DeviceAdminReceiver()
