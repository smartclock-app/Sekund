import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { fetch } from "@tauri-apps/plugin-http";

export interface UpdateInfo {
  version: string;
  url: string;
}

// Full example usage
export async function checkForUpdate(): Promise<UpdateInfo | null> {
  const request = await fetch("https://api.github.com/repos/0x5045414b/ClockBeta/releases/latest", {
    headers: {
      Authorization: `Bearer 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`,
    },
  });

  const assets = await request.json();
  const apkAsset = assets.assets.find((asset: any) => asset.name.endsWith(".apk"));

  if (apkAsset) {
    return {
      version: assets.tag_name,
      url: apkAsset.url,
    };
  }

  return null;
}

export async function downloadApk(url: string, path: string, onProgress: (progress: number) => void): Promise<void> {
  const unlisten = await listen<number>("download-progress", event => {
    onProgress(event.payload);
  });

  try {
    await invoke("download_apk", { url, path });
  } finally {
    unlisten();
  }
}

export async function installApk(path: string): Promise<void> {
  return invoke("plugin:apk-intent|install_apk", {
    payload: {
      path,
    },
  });
}
