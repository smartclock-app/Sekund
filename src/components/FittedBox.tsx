import React, { useLayoutEffect, useRef } from "react";

interface FittedBoxProps {
  children: React.ReactNode;
  className?: string;
}

const FittedBox: React.FC<FittedBoxProps> = ({ children, className }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const textRef = useRef<HTMLSpanElement>(null);

  useLayoutEffect(() => {
    const container = containerRef.current;
    const text = textRef.current;
    if (!container || !text) return;

    // We use requestAnimationFrame to ensure the browser has
    // calculated the initial layout.
    const frameId = requestAnimationFrame(() => {
      // 1. Set the initial "ideal" size from your CSS variable
      text.style.fontSize = "2rem";

      const containerWidth = container.offsetWidth;
      const textWidth = text.scrollWidth;

      if (containerWidth > 0 && textWidth > containerWidth) {
        const currentSizePx = parseFloat(window.getComputedStyle(text).fontSize);
        const ratio = containerWidth / textWidth;

        // Apply the shrink ratio
        text.style.fontSize = `${currentSizePx * ratio}px`;
      }
    });

    return () => cancelAnimationFrame(frameId);
  }, [children]);

  return (
    <div ref={containerRef} style={{ width: "100%", overflow: "hidden" }}>
      <span
        className={className}
        ref={textRef}
        style={{
          display: "inline-block",
          whiteSpace: "nowrap",
        }}
      >
        {children}
      </span>
    </div>
  );
};

export default FittedBox;
