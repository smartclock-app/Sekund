import useEventListener, { EventType } from "@/hooks/useEventListener";
import useNetworkStore from "@/hooks/useNetworkStore";
import { listen } from "@tauri-apps/api/event";
import { info } from "@tauri-apps/plugin-log";
import { Dayjs } from "dayjs";
import { useEffect } from "react";
import hashInterfaces, { NetworkInterfaces } from "./hashInterfaces";

const NetworkManager = () => {
  const probe = useNetworkStore(state => state.probe);

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
