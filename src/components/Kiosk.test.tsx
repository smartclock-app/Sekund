import { mockIPC } from "@tauri-apps/api/mocks";
import { render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import Kiosk from "./Kiosk";

const mockPlatform = vi.fn();
vi.mock("@tauri-apps/plugin-os", () => ({
  platform: () => mockPlatform(),
}));

const DEVICE_OWNER_COMMANDS = [
  "enable_kiosk_mode",
  "enable_auto_brightness",
  "set_as_persistent_home",
  "disable_keyguard",
  "enable_auto_time",
];

describe("Kiosk", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("does nothing on non-Android platforms", async () => {
    mockPlatform.mockReturnValue("linux");
    const calledCommands: string[] = [];
    mockIPC(cmd => {
      calledCommands.push(cmd);
      return null;
    });

    render(<Kiosk />);

    await new Promise(resolve => setTimeout(resolve, 0));
    expect(calledCommands).not.toContain("is_device_owner");
    for (const command of DEVICE_OWNER_COMMANDS) {
      expect(calledCommands).not.toContain(command);
    }
  });

  it("applies all device-owner setup commands on Android when the app is the device owner", async () => {
    mockPlatform.mockReturnValue("android");
    const calledCommands: string[] = [];
    mockIPC(cmd => {
      calledCommands.push(cmd);
      if (cmd === "is_device_owner") return true;
      return null;
    });

    render(<Kiosk />);

    for (const command of DEVICE_OWNER_COMMANDS) {
      await waitFor(() => expect(calledCommands).toContain(command));
    }
  });

  it("does not apply any device-owner setup commands on Android when not the device owner", async () => {
    mockPlatform.mockReturnValue("android");
    const calledCommands: string[] = [];
    mockIPC(cmd => {
      calledCommands.push(cmd);
      if (cmd === "is_device_owner") return false;
      return null;
    });

    render(<Kiosk />);

    await waitFor(() => expect(calledCommands).toContain("is_device_owner"));
    for (const command of DEVICE_OWNER_COMMANDS) {
      expect(calledCommands).not.toContain(command);
    }
  });
});
