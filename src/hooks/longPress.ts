import { useCallback, useRef } from "react";

const useLongPress = (callback: () => void, button: "left" | "right" | "both" = "left", delay: number = 500) => {
  const timeoutRef = useRef<number | null>(null);

  const start = useCallback(
    (e: React.MouseEvent) => {
      if (button === "left" && e.button !== 0) return;
      if (button === "right" && e.button !== 2) return;

      timeoutRef.current = window.setTimeout(callback, delay);
    },
    [callback, delay],
  );

  const clear = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  }, []);

  return {
    onMouseDown: start,
    onTouchStart: start,
    onMouseUp: clear,
    onMouseLeave: clear,
    onTouchEnd: clear,
  };
};

export default useLongPress;
