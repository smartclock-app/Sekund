import useLongPress from "@/hooks/useLongPress";
import useRouter, { RouterScreen } from "@/hooks/useRouter";
import { info } from "@tauri-apps/plugin-log";
import { openUrl } from "@tauri-apps/plugin-opener";
import { useState } from "react";
import { createPortal } from "react-dom";
import styles from "./menu.module.scss";

const Menu = (props: { show: boolean; onClose: () => void }) => {
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
          <button onClick={async () => await openUrl("https://www.google.co.uk")}>Browser</button>
        </li>
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
