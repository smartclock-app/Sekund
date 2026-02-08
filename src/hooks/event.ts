import { useEffect, useRef } from "react";

export enum EventType {
  Tick = "tick",
  Refresh = "refresh",
  HttpRequest = "http-request",
}

export const dispatchEvent = (eventName: EventType) => {
  window.dispatchEvent(new Event(eventName));
};

const useEventListener = (eventName: EventType, callback: (event: Event) => void) => {
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
