import { mockIPC } from "@tauri-apps/api/mocks";
import { render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import DisplayAdmin from "./DisplayAdmin";

const mockPlatform = vi.fn();
vi.mock("@tauri-apps/plugin-os", () => ({
  platform: () => mockPlatform(),
}));

describe("DisplayAdmin", () => {
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

    render(<DisplayAdmin />);

    await new Promise(resolve => setTimeout(resolve, 0));
    expect(calledCommands).not.toContain("is_display_admin_active");
    expect(calledCommands).not.toContain("request_display_admin");
  });

  it("requests device admin on Android when not yet active", async () => {
    mockPlatform.mockReturnValue("android");
    const calledCommands: string[] = [];
    mockIPC(cmd => {
      calledCommands.push(cmd);
      if (cmd === "is_display_admin_active") return false;
      return null;
    });

    render(<DisplayAdmin />);

    await waitFor(() => expect(calledCommands).toContain("request_display_admin"));
  });

  it("does not request device admin on Android when already active", async () => {
    mockPlatform.mockReturnValue("android");
    const calledCommands: string[] = [];
    mockIPC(cmd => {
      calledCommands.push(cmd);
      if (cmd === "is_display_admin_active") return true;
      return null;
    });

    render(<DisplayAdmin />);

    await waitFor(() => expect(calledCommands).toContain("is_display_admin_active"));
    expect(calledCommands).not.toContain("request_display_admin");
  });
});
