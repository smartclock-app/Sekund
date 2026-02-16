import useEventListener, { EventType } from "@/hooks/useEventListener";
import { path } from "@tauri-apps/api";
import { exists } from "@tauri-apps/plugin-fs";
import { error } from "@tauri-apps/plugin-log";
import dayjs from "dayjs";
import { useState } from "react";
import { Config } from ".";
import { checkForUpdate, downloadApk, installApk } from "./android_updates";

const Android = ({ config }: { config: Config }) => {
  const [lastChecked, setLastChecked] = useState<dayjs.Dayjs | null>(null);
  const [updateInfo, setUpdateInfo] = useState<{ version: string; url: string } | null>(null);
  const [downloadProgress, setDownloadProgress] = useState<number>(0);

  const checkForUpdates = async () => {
    const update = await checkForUpdate();
    setUpdateInfo(update);
  };

  const downloadUpdate = async (url: string) => {
    try {
      const filename = await path.join(await path.downloadDir(), `${updateInfo!.version}.apk`);

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
    <div>
      <h1>Update Available: {updateInfo?.version}</h1>
      <button onClick={() => downloadUpdate(updateInfo!.url)}>Install Update</button>
      <p>Download Progress: {downloadProgress.toFixed(2)}%</p>
    </div>
  );
};

export default Android;
