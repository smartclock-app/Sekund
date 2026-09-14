package uk.dnpk.sekund

import android.app.admin.DeviceAdminReceiver

// Grants Sekund the "force-lock" device admin policy, which is required to
// call DevicePolicyManager.lockNow() and turn the display off remotely.
// Activated by the user once via Settings > Security > Device admin apps.
class SekundDeviceAdminReceiver : DeviceAdminReceiver()
