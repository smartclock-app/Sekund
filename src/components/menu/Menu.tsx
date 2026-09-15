import useLongPress from "@/hooks/useLongPress";
import useRouter, { RouterScreen } from "@/hooks/useRouter";
import { invoke } from "@tauri-apps/api/core";
import { info } from "@tauri-apps/plugin-log";
import { openUrl } from "@tauri-apps/plugin-opener";
import { platform } from "@tauri-apps/plugin-os";
import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import styles from "./menu.module.scss";

const Menu = (props: { show: boolean; onClose: () => void }) => {
  const [displayAdminActive, setDisplayAdminActive] = useState(true);

  useEffect(() => {
    if (!props.show || platform() !== "android") return;

    invoke<boolean>("is_display_admin_active")
      .then(setDisplayAdminActive)
      .catch(() => setDisplayAdminActive(false));
  }, [props.show]);

  if (!props.show) return null;
  return createPortal(
    <div className={styles.overlay} onClick={props.onClose}>
      <ul className={styles.menu}>
        <li>
          <button onClick={() => window.location.reload()}>Reload</button>
        </li>
        <li>
          <button onClick={() => useRouter.getState().navigate(RouterScreen.Editor)}>Editor</button>
        </li>
        <li>
          <button onClick={async () => invoke("launch_browser").catch(() => openUrl("https://www.google.com"))}>
            Browser
          </button>
        </li>
        {platform() === "android" && (
          <>
            <li>
              <button onClick={() => invoke("open_settings").catch(e => info(`Failed to open settings: ${e}`))}>
                Settings
              </button>
            </li>
            {!displayAdminActive && (
              <li>
                <button
                  onClick={() =>
                    invoke("request_display_admin").catch(e => info(`Failed to open device admin settings: ${e}`))
                  }
                >
                  Enable Display Control
                </button>
              </li>
            )}
            <li>
              <button onClick={() => invoke("disable_kiosk_mode").catch(e => info(`Failed to exit kiosk mode: ${e}`))}>
                Exit Kiosk Mode
              </button>
            </li>
          </>
        )}
      </ul>
    </div>,
    document.getElementById("root")!,
  );
};

const useOptionsMenu = () => {
  const [showMenu, setShowMenu] = useState(false);

  const longPressProps = useLongPress(() => {
    info("Long press detected");
    setShowMenu(true);
    setTimeout(() => setShowMenu(false), 5000);
  });

  return [longPressProps, () => <Menu show={showMenu} onClose={() => setShowMenu(false)} />] as const;
};

export default useOptionsMenu;
