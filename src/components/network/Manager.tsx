import useEventListener, { EventType } from "@/hooks/useEventListener";
import useNetworkStore from "@/hooks/useNetworkStore";
import { info } from "@tauri-apps/plugin-log";
import { Dayjs } from "dayjs";
import { useCallback, useEffect, useRef } from "react";
import { getNonEmptyInterfaces } from "tauri-plugin-network-api";
import hashInterfaces from "./hashInterfaces";

const NetworkManager = () => {
  const cancelled = useRef(false);
  const lastHash = useNetworkStore(state => state.lastHash);
  const connected = useNetworkStore(state => state.connected);
  const probe = useNetworkStore(state => state.probe);

  const probeInterfaces = useCallback(async () => {
    const ifaces = await getNonEmptyInterfaces();
    const ifacesHash = hashInterfaces(ifaces);

    if (!cancelled.current) {
      if (ifacesHash !== lastHash) {
        info(`[NetworkManager] Interfaces changed: ${ifacesHash}`);
        useNetworkStore.setState({ lastHash: ifacesHash });
        await probe();
      }
    }
  }, [connected, lastHash, probe]);

  useEffect(() => {
    cancelled.current = false;
    probeInterfaces();
    return () => {
      cancelled.current = true;
    };
  }, [probe]);

  useEventListener(EventType.Tick, event => {
    const seconds = (event.detail as Dayjs).second();
    if (seconds % 30 === 0) {
      probe();
    }
    if (connected && seconds % 10 === 0) {
      probeInterfaces();
    }
    if (!connected && seconds % 2 === 0) {
      probeInterfaces();
    }
  });

  return null;
};

export default NetworkManager;
