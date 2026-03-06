import { info } from "@tauri-apps/plugin-log";
import { useEffect, useRef } from "react";

export enum EventType {
  Tick = "tick",
  Refresh = "refresh",
  HttpRequest = "http-request",
  SkipPhoto = "skip-photo",
}

export const dispatchEvent = (eventName: EventType, detail?: any) => {
  if (eventName !== EventType.Tick) info(`[Event] Dispatching ${eventName}`);
  window.dispatchEvent(new CustomEvent(eventName, { detail }));
};

const useEventListener = (eventName: EventType, callback: (event: Event & { detail?: any }) => void) => {
  const savedCallback = useRef(callback);

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    const eventListener = (event: Event) => savedCallback.current(event);
    window.addEventListener(eventName, eventListener);

    return () => {
      window.removeEventListener(eventName, eventListener);
    };
  }, [eventName]);
};

export default useEventListener;
