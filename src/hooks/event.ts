import { useEffect } from "react";

export enum EventType {
  Refresh = "refresh",
  HttpRequest = "http-request",
}

export const dispatchEvent = (eventName: EventType) => {
  window.dispatchEvent(new Event(eventName));
};

const useEventListener = (eventName: EventType, callback: (event: Event) => void) => {
  useEffect(() => {
    window.addEventListener(eventName, callback);

    return () => {
      window.removeEventListener(eventName, callback);
    };
  }, [eventName, callback]);
};

export default useEventListener;
