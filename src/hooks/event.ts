import { useEffect } from "react";

enum EventType {
  Refresh = "refresh",
}

export const dispatchEvent = (eventName: EventType) => {
  window.dispatchEvent(new Event(eventName));
};

const useEventListener = (eventName: EventType, callback: VoidFunction) => {
  useEffect(() => {
    window.addEventListener(eventName, callback);

    return () => {
      window.removeEventListener(eventName, callback);
    };
  }, [eventName, callback]);
};

export default useEventListener;
