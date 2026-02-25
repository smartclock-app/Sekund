import Card from "@/components/Card";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { path } from "@tauri-apps/api";
import { getVersion } from "@tauri-apps/api/app";
import { BaseDirectory, exists, readDir, remove } from "@tauri-apps/plugin-fs";
import { error, info } from "@tauri-apps/plugin-log";
import dayjs from "dayjs";
import { useState } from "react";
import { Config } from ".";
import { checkForUpdate, downloadApk, installApk } from "./android_updates";

/// Compares two semver version strings. Returns 1 if current > latest, -1 if current < latest, and 0 if equal.
const semver = (current: string, latest: string) => {
  const currentParts = current.split(".");
  const latestParts = latest.split(".");

  for (let i = 0; i < Math.max(currentParts.length, latestParts.length); i++) {
    const currentPart = parseInt(currentParts[i] || "0", 10);
    const latestPart = parseInt(latestParts[i] || "0", 10);

    if (currentPart > latestPart) return 1;
    if (currentPart < latestPart) return -1;
  }

  return 0;
};

const Android = ({ config }: { config: Config }) => {
  const [lastChecked, setLastChecked] = useState<dayjs.Dayjs | null>(null);
  const [updateInfo, setUpdateInfo] = useState<{ version: string; url: string } | null>(null);
  const [downloadProgress, setDownloadProgress] = useState<number>(0);

  const checkForUpdates = async () => {
    const update = await checkForUpdate();
    const current = await getVersion();

    info(`[Updater] Current version: ${current}, Latest version: ${update?.version}`);

    if (update && semver(current.replace("v", ""), update.version.replace("v", "")) === -1) {
      setUpdateInfo(update);
    } else {
      setUpdateInfo(null);
    }
  };

  const downloadUpdate = async (url: string) => {
    try {
      const cachePath = await path.appCacheDir();

      const apks = (await readDir(cachePath)).filter(file => file.name.endsWith(".apk"));
      // Clean up old APKs
      for (const apk of apks) {
        if (apk.name !== `${updateInfo!.version}.apk`) {
          await remove(apk.name, { baseDir: BaseDirectory.AppCache });
        }
      }

      const filename = await path.join(cachePath, `${updateInfo!.version}.apk`);
      if (!(await exists(filename))) await downloadApk(url, filename, setDownloadProgress);

      installApk(filename);
    } catch (e) {
      error(`Failed to download or install update: ${e}`);
    }
  };

  useEventListener(EventType.Tick, e => {
    const now = e.detail as dayjs.Dayjs;
    if (!lastChecked || now.diff(lastChecked, "minute") >= config.updateInterval) {
      checkForUpdates();
      setLastChecked(now);
    }
  });

  if (!updateInfo) return null;

  return (
    <Card>
      <h1>Update Available: {updateInfo?.version}</h1>
      <button onClick={() => downloadUpdate(updateInfo!.url)}>Install Update</button>
      <p>Download Progress: {downloadProgress.toFixed(2)}%</p>
    </Card>
  );
};

export default Android;
