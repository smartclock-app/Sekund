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

// export async function downloadApk(url: string, path: string, onProgress: (progress: number) => void): Promise<void> {
//   const request = await fetch(url, {
//     headers: {
//       Authorization: `Bearer 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`,
//       Accept: "application/octet-stream",
//     },
//   });

//   if (!request.ok) {
//     throw new Error(`HTTP error! status: ${request.status}`);
//   }

//   const contentLength = request.headers.get("content-length");
//   const total = contentLength ? parseInt(contentLength, 10) : 0;
//   let loaded = 0;

//   const reader = request.body!.getReader();
//   const chunks: Uint8Array[] = [];

//   try {
//     while (true) {
//       const { done, value } = await reader.read();
//       if (done) break;

//       chunks.push(value);
//       loaded += value.length;

//       if (total > 0) {
//         onProgress((loaded / total) * 100);
//       }
//     }
//   } finally {
//     reader.releaseLock();
//   }

//   console.log("Download complete, combining chunks...");

//   const uint8Array = new Uint8Array(loaded);
//   let position = 0;
//   for (const chunk of chunks) {
//     uint8Array.set(chunk, position);
//     position += chunk.length;
//   }

//   console.log("Writing file to:", path);
//   await writeFile(path, uint8Array);
//   console.log("File write complete");
// }

export async function installApk(path: string): Promise<void> {
  return invoke<void>("install_apk", { path });
}
