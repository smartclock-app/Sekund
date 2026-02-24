import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import useLongPress from "./useLongPress";

describe("useLongPress", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("fires callback after the default 500 ms delay on left mouse down", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback));
    result.current.onMouseDown({ button: 0 } as React.MouseEvent);
    expect(callback).not.toHaveBeenCalled();
    vi.advanceTimersByTime(500);
    expect(callback).toHaveBeenCalledTimes(1);
  });

  it("does not fire if mouse is released before the delay", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback));
    result.current.onMouseDown({ button: 0 } as React.MouseEvent);
    vi.advanceTimersByTime(400);
    result.current.onMouseUp();
    vi.advanceTimersByTime(200);
    expect(callback).not.toHaveBeenCalled();
  });

  it("respects a custom delay", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback, "left", 1000));
    result.current.onMouseDown({ button: 0 } as React.MouseEvent);
    vi.advanceTimersByTime(999);
    expect(callback).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(callback).toHaveBeenCalledTimes(1);
  });

  it("ignores right-click when button is 'left'", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback, "left"));
    result.current.onMouseDown({ button: 2 } as React.MouseEvent);
    vi.advanceTimersByTime(500);
    expect(callback).not.toHaveBeenCalled();
  });

  it("ignores left-click when button is 'right'", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback, "right"));
    result.current.onMouseDown({ button: 0 } as React.MouseEvent);
    vi.advanceTimersByTime(500);
    expect(callback).not.toHaveBeenCalled();
  });

  it("fires for right-click when button is 'right'", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback, "right"));
    result.current.onMouseDown({ button: 2 } as React.MouseEvent);
    vi.advanceTimersByTime(500);
    expect(callback).toHaveBeenCalledTimes(1);
  });

  it("fires for both buttons when button is 'both'", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback, "both"));
    result.current.onMouseDown({ button: 0 } as React.MouseEvent);
    vi.advanceTimersByTime(500);
    expect(callback).toHaveBeenCalledTimes(1);

    result.current.onMouseDown({ button: 2 } as React.MouseEvent);
    vi.advanceTimersByTime(500);
    expect(callback).toHaveBeenCalledTimes(2);
  });

  it("fires on touch start (no 'button' property)", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback));
    result.current.onTouchStart({} as React.TouchEvent);
    vi.advanceTimersByTime(500);
    expect(callback).toHaveBeenCalledTimes(1);
  });

  it("clears on mouse leave", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback));
    result.current.onMouseDown({ button: 0 } as React.MouseEvent);
    result.current.onMouseLeave();
    vi.advanceTimersByTime(500);
    expect(callback).not.toHaveBeenCalled();
  });

  it("clears on touch end", () => {
    const callback = vi.fn();
    const { result } = renderHook(() => useLongPress(callback));
    result.current.onTouchStart({} as React.TouchEvent);
    result.current.onTouchEnd();
    vi.advanceTimersByTime(500);
    expect(callback).not.toHaveBeenCalled();
  });
});
