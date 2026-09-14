import { invoke } from "@tauri-apps/api/core";
import { info } from "@tauri-apps/plugin-log";
import { platform } from "@tauri-apps/plugin-os";
import { useEffect } from "react";

// Android requires a one-time, on-device grant of the device admin permission
// before display_off can work. Requesting it automatically on startup means
// the device (which only ever runs this one app) gets set up without anyone
// having to find the long-press menu's manual fallback.
const DisplayAdmin = () => {
  useEffect(() => {
    if (platform() !== "android") return;

    invoke<boolean>("is_display_admin_active")
      .then(active => {
        if (!active) return invoke("request_display_admin");
      })
      .catch(e => info(`[Display Admin] Failed to check or request device admin: ${e}`));
  }, []);

  return null;
};

export default DisplayAdmin;
