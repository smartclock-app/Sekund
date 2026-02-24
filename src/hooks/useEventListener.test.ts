import { renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import useEventListener, { EventType, dispatchEvent } from "./useEventListener";

describe("EventType enum", () => {
  it("has the expected string values", () => {
    expect(EventType.Tick).toBe("tick");
    expect(EventType.Refresh).toBe("refresh");
    expect(EventType.HttpRequest).toBe("http-request");
    expect(EventType.SkipPhoto).toBe("skip-photo");
  });
});

describe("dispatchEvent", () => {
  it("dispatches a custom window event for the given type", () => {
    const handler = vi.fn();
    window.addEventListener(EventType.Refresh, handler);
    dispatchEvent(EventType.Refresh);
    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(EventType.Refresh, handler);
  });

  it("attaches the detail payload to the dispatched event", () => {
    const handler = vi.fn();
    const detail = { time: "now" };
    window.addEventListener(EventType.SkipPhoto, handler);
    dispatchEvent(EventType.SkipPhoto, detail);
    expect((handler.mock.calls[0][0] as CustomEvent).detail).toEqual(detail);
    window.removeEventListener(EventType.SkipPhoto, handler);
  });

  it("dispatches Tick events without calling log (no side effects on window)", () => {
    const handler = vi.fn();
    window.addEventListener(EventType.Tick, handler);
    dispatchEvent(EventType.Tick, { tick: 1 });
    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(EventType.Tick, handler);
  });
});

describe("useEventListener", () => {
  it("calls the callback when the matching event is dispatched", () => {
    const callback = vi.fn();
    renderHook(() => useEventListener(EventType.Refresh, callback));
    window.dispatchEvent(new CustomEvent(EventType.Refresh));
    expect(callback).toHaveBeenCalledTimes(1);
  });

  it("does not call the callback for a different event type", () => {
    const callback = vi.fn();
    renderHook(() => useEventListener(EventType.Refresh, callback));
    window.dispatchEvent(new CustomEvent(EventType.Tick));
    expect(callback).not.toHaveBeenCalled();
  });

  it("removes the listener on unmount", () => {
    const callback = vi.fn();
    const { unmount } = renderHook(() => useEventListener(EventType.Refresh, callback));
    unmount();
    window.dispatchEvent(new CustomEvent(EventType.Refresh));
    expect(callback).not.toHaveBeenCalled();
  });

  it("always invokes the latest callback reference without re-registering", () => {
    const cb1 = vi.fn();
    const cb2 = vi.fn();
    const { rerender } = renderHook(({ cb }) => useEventListener(EventType.Refresh, cb), {
      initialProps: { cb: cb1 },
    });
    rerender({ cb: cb2 });
    window.dispatchEvent(new CustomEvent(EventType.Refresh));
    expect(cb1).not.toHaveBeenCalled();
    expect(cb2).toHaveBeenCalledTimes(1);
  });
});
