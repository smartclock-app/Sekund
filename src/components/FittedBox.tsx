import React, { useEffect, useRef, useState } from "react";

interface FittedBoxProps {
  children: React.ReactNode;
  minFontSize?: number;
  maxFontSize?: number;
  width?: string | number;
  height?: string | number;
  className?: string;
}

const FittedBox: React.FC<FittedBoxProps> = ({
  children,
  minFontSize = 8,
  maxFontSize = 100,
  width = "100%",
  height = "100%",
  className = "",
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [fontSize, setFontSize] = useState(maxFontSize);

  useEffect(() => {
    const container = containerRef.current;
    const content = contentRef.current;

    if (!container || !content) return;

    // Binary search for the optimal font size
    let low = minFontSize;
    let high = maxFontSize;
    let bestFontSize = maxFontSize;

    while (low <= high) {
      const mid = (low + high) / 2;
      content.style.fontSize = `${mid}px`;

      // Check if content fits
      const fits = content.scrollWidth <= container.clientWidth && content.scrollHeight <= container.clientHeight;

      if (fits) {
        bestFontSize = mid;
        low = mid + 0.5;
      } else {
        high = mid - 0.5;
      }
    }

    setFontSize(Math.max(minFontSize, bestFontSize));
  }, [children, minFontSize, maxFontSize]);

  return (
    <div
      ref={containerRef}
      style={{
        width,
        height,
        display: "flex",
        overflow: "hidden",
      }}
      className={className}
    >
      <div
        ref={contentRef}
        style={{
          fontSize: `${fontSize}px`,
          whiteSpace: "nowrap",
          transition: "font-size 0.2s ease-out",
        }}
      >
        {children}
      </div>
    </div>
  );
};

export default FittedBox;
