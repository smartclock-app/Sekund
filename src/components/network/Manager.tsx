import useAlertsStore from "@/hooks/useAlertsStore";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import useNetworkStore from "@/hooks/useNetworkStore";
import { listen } from "@tauri-apps/api/event";
import { info } from "@tauri-apps/plugin-log";
import { Dayjs } from "dayjs";
import { useEffect } from "react";
import hashInterfaces, { NetworkInterfaces } from "./hashInterfaces";

const ALERT_KEY = "Network";

const NetworkManager = () => {
  const probe = useNetworkStore(state => state.probe);
  const connected = useNetworkStore(state => state.connected);

  useEffect(() => {
    if (connected) {
      useAlertsStore.getState().clearAlert(ALERT_KEY);
    } else {
      useAlertsStore.getState().pushAlert(ALERT_KEY, {
        title: "Offline",
        subtitle: "Waiting for network connection...",
      });
    }
  }, [connected]);

  useEffect(() => {
    let unlisten: (() => void) | undefined;

    const setupListener = async () => {
      info("[Network] Setting up listener...");
      unlisten = await listen("network-change", async event => {
        const { interfaces } = event.payload as Record<string, NetworkInterfaces>;
        const ifacesHash = hashInterfaces(interfaces);
        const lastHash = useNetworkStore.getState().lastHash;
        if (ifacesHash !== lastHash) {
          info(`[Network] Interfaces changed: ${ifacesHash}`);
          useNetworkStore.setState({ lastHash: ifacesHash });
          await probe();
        }
      });
    };

    setupListener();
    probe();

    return () => unlisten?.();
  }, [probe]);

  useEventListener(EventType.Tick, event => {
    const seconds = (event.detail as Dayjs).second();
    if (seconds % 30 === 0) {
      probe();
    }
  });

  return null;
};

export default NetworkManager;
