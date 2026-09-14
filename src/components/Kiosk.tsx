import { invoke } from "@tauri-apps/api/core";
import { info } from "@tauri-apps/plugin-log";
import { platform } from "@tauri-apps/plugin-os";
import { useEffect } from "react";

// On a device provisioned as Device Owner (see docs/configuration.md), pins the
// screen to just this app plus the browser/Settings, registers it as the
// permanent default Home app, turns on Android's own adaptive brightness
// (ambient light sensor), disables the lock screen, and keeps the clock correct
// via automatic time/timezone — all on every startup. No-ops on a device that
// isn't the device owner.
const Kiosk = () => {
  useEffect(() => {
    if (platform() !== "android") return;

    invoke<boolean>("is_device_owner")
      .then(isOwner => {
        if (!isOwner) return;

        invoke("enable_kiosk_mode").catch(e => info(`[Kiosk] Failed to enable kiosk mode: ${e}`));
        invoke("enable_auto_brightness").catch(e => info(`[Kiosk] Failed to enable auto brightness: ${e}`));
        invoke("set_as_persistent_home").catch(e => info(`[Kiosk] Failed to set as persistent home: ${e}`));
        invoke("disable_keyguard").catch(e => info(`[Kiosk] Failed to disable keyguard: ${e}`));
        invoke("enable_auto_time").catch(e => info(`[Kiosk] Failed to enable auto time: ${e}`));
      })
      .catch(e => info(`[Kiosk] Failed to check device owner status: ${e}`));
  }, []);

  return null;
};

export default Kiosk;
