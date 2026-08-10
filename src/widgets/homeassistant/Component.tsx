import { WidgetComponent } from "@/helpers/types";
import useAlertsStore from "@/hooks/useAlertsStore";
import { fetch } from "@tauri-apps/plugin-http";
import { info, warn } from "@tauri-apps/plugin-log";
import WebSocket from "@tauri-apps/plugin-websocket";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Config } from ".";
import Camera from "./Camera";
import styles from "./homeassistant.module.scss";

const Component: WidgetComponent<Config> = ({ config }) => {
  const [reconnectAttempt, setReconnectAttempt] = useState<[Date, number]>([new Date(), 0]);
  const [isConnected, setIsConnected] = useState(false);
  const [cameras, setCameras] = useState<Array<[URL, Config["cameras"][number]]>>([]);
  const channel = useRef<WebSocket | null>(null);
  const messageId = useRef(1);
  const [messageIds, setMessageIds] = useState<Record<number, Config["cameras"][number]>>({});

  const connectToHomeAssistant = async () => {
    try {
      const response = await fetch(`${config.url}/api/`, {
        headers: { Authorization: `Bearer ${config.token}` },
      });
      const data = await response.json();
      if (response.status != 200 || data["message"] != "API running.") {
        warn("[Home Assistant] Connection failed");
        return;
      }
    } catch (e: any) {
      warn(`[Home Assistant] Connection failed: ${e.response?.statusCode} ${e.response?.statusMessage}`);
      setReconnectAttempt([new Date(), reconnectAttempt[1] + 1]);
      warn(`[Home Assistant] Waiting ${30 * reconnectAttempt[1]} seconds before reconnecting`);
      return;
    }

    const webSocketUri = `${config.url.replace("http", "ws")}/api/websocket`;
    channel.current = await WebSocket.connect(webSocketUri);

    info("[Home Assistant] Connected successfully");
    setIsConnected(true);
    channel.current.addListener(handleMessage);
  };

  const handleMessage = async (message: any) => {
    info(`[Home Assistant] ${message}`);

    const data = JSON.parse(message);

    switch (data["type"]) {
      // Called on first connection
      case "auth_required":
        channel.current?.send(JSON.stringify({ type: "auth", access_token: config.token }));
        break;

      // Called if authentication fails
      case "auth_invalid":
        channel.current?.disconnect();
        break;

      // Called if authentication succeeds
      case "auth_ok":
        for (const camera of config.cameras) {
          channel.current?.send(
            JSON.stringify({
              id: messageId.current++,
              type: "subscribe_trigger",
              trigger: {
                platform: "state",
                entity_id: camera.trigger,
              },
            }),
          );
        }

        break;

      // Called when trigger state changes
      case "event":
        messageId.current += 1;
        const trigger = data?.["event"]?.["variables"]?.["trigger"];
        const entityId = trigger?.["entity_id"];
        const state = trigger?.["to_state"]?.["state"];
        if (state == "on") {
          const camera = config.cameras.find(camera => camera.trigger == entityId)!;

          if (camera.streamUri != null) {
            const streamUri = new URL(camera.streamUri!);
            setCameras(prev => [...prev, [streamUri, camera]]);
            // _cameraOverlayController.show();
          } else {
            setMessageIds(prev => ({ ...prev, [messageId.current]: camera })); // Store camera with message id for use with result
            channel.current?.send(
              JSON.stringify({ id: messageId.current, type: "camera/stream", entity_id: camera.id }),
            );
          }
        } else {
          const waitTime = config.cameraWaitTime;
          await new Promise(resolve => setTimeout(resolve, waitTime * 1000));
          setCameras(prev => prev.filter(camera => camera[1].trigger != entityId));
          if (cameras.length == 0) {
            // _cameraOverlayController.hide();
          }
        }
        break;

      // Called with camera stream result
      case "result":
        const id = data?.["id"];
        const url = data?.["result"]?.["url"];
        if (url != null) {
          const streamUri = new URL(`${config.url}${url}`);
          const camera = messageIds[id];
          setCameras(prev => [...prev, [streamUri, camera]]);
          // _cameraOverlayController.show();
          setMessageIds(prev => {
            const newIds = { ...prev };
            delete newIds[id];
            return newIds;
          });
        }
        break;

      default:
        warn(`[Home Assistant] Unhandled message type: ${data["type"]}`);
        break;
    }
  };

  useEffect(() => {
    if (!config.url || !config.token) {
      useAlertsStore.getState().pushAlert("Home Assistant", { title: "Home Assistant", subtitle: "URL or token not set" });
      warn("[Home Assistant] URL or token not set");
      channel.current?.disconnect();
      return;
    } else {
      useAlertsStore.getState().clearAlert("Home Assistant");
    }

    if (!isConnected) connectToHomeAssistant();

    return () => {
      channel.current?.disconnect();
    };
  }, [config]);

  if (cameras.length == 0) return null;

  return createPortal(
    <div className={styles.cameraOverlay}>
      {cameras.map(([streamUri, camera]) => (
        <Camera key={camera.id} src={streamUri.toString()} aspectRatio={camera.aspectRatio} />
      ))}
    </div>,
    document.getElementById("root")!,
  );
};

export default Component;
