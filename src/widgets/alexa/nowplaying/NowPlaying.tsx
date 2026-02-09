import Card from "@/components/Card";
import useDatabaseStore from "@/hooks/useDatabaseStore";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { error, info } from "@tauri-apps/plugin-log";
import dayjs from "dayjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { Config } from "..";
import fetchLyrics from "../util/fetchLyrics";
import Lrc from "../util/Lrc";
import { Queue } from "../util/types";
import useQueryClient from "../util/useQueryClient";
import styles from "./nowplaying.module.scss";
import NowPlayingLyrics from "./NowPlayingLyrics";

interface NowPlayingProps {
  config: Config;
}

const RADIO_PROVIDERS = ["", "Unknown Provider", "PLANET_RADIO", "TuneIn", "Global Player"];

const NowPlaying: React.FC<NowPlayingProps> = ({ config }) => {
  const runOnce = useRef(false);

  const databaseStore = useDatabaseStore();
  const queryClientStore = useQueryClient();
  const [progress, setProgress] = useState(0);
  const [queue, setQueue] = useState<Queue>({ playerState: "STOPPED" } as Queue);
  const [lyrics, setLyrics] = useState<Lrc | null>(null);

  const isRadio = useCallback(() => {
    let isRadio = false;
    if (queue?.provider?.providerName != null) {
      isRadio = config.radioProviders.includes(queue.provider?.providerName);
    }
    return isRadio || RADIO_PROVIDERS.includes(queue?.provider?.providerName ?? "Unknown Provider");
  }, [queue]);

  const getQueue = useCallback(async () => {
    if (!queryClientStore.client) {
      error("[Alexa] Query client not initialized");
      return;
    }

    if (queue?.playerState == "REFRESHING") return;

    info("[Alexa] Refetching queue");
    let q = { playerState: "STOPPED" } as Queue;
    try {
      for (const device of config.devices) {
        const queue = await queryClientStore.client?.getQueue(config.userId, device);
        if (queue?.playerState == "PLAYING") {
          q = queue;
          break;
        }
      }
    } catch (e) {
      return error(`[Alexa] Failed to fetch queue: ${e}`);
    }

    if (RADIO_PROVIDERS.includes(q.provider?.providerName ?? "Unknown Provider")) {
      setQueue(q);
      setLyrics(null);
      setProgress(0);
      return;
    }

    let lyricResult: Lrc | null = null;
    if (q.infoText?.title != null && q.infoText?.subText1 != null) {
      lyricResult = await fetchLyrics(databaseStore, q.infoText!.title!, q.infoText!.subText1!);
    }

    setLyrics(lyricResult);
    setQueue(q);
    setProgress(q.progress?.mediaProgress ?? 0);
  }, [databaseStore, queryClientStore.client, config]);

  useEffect(() => {
    const initialize = async () => {
      if (!queryClientStore.client?.isInitialized) {
        await queryClientStore.init(config.token);
      }

      if (runOnce.current) return;
      runOnce.current = true;

      await getQueue();
    };

    initialize();
  }, [config, queryClientStore.client]);

  useEventListener(EventType.Tick, () => {
    if (queue == null || queue!.progress == null) return setProgress(0);
    if (queue!.playerState != "PLAYING") return;

    if (!isRadio()) {
      if (progress >= queue!.progress!.mediaLength!) {
        getQueue();
        queue!.playerState = "REFRESHING";
      }

      setProgress(Math.min(progress + 1000, queue!.progress!.mediaLength!));
    }
  });

  useEventListener(EventType.Refresh, () => {
    getQueue();
  });

  if (
    queue == null ||
    queue?.playerState == null ||
    !(queue!.playerState == "PLAYING" || queue!.playerState == "REFRESHING")
  ) {
    return null;
  }

  return (
    <Card padding={false}>
      <div className={styles.nowplaying}>
        <div className={styles.media}>
          {queue?.mainArt?.fullUrl && <img src={queue.mainArt.fullUrl} />}
          <div className={styles.info}>
            <div>
              <p className={styles.title}>{queue?.infoText?.title ?? "No media playing"}</p>
              <p className={styles.artist}>{queue?.infoText?.subText1 ?? ""}</p>
            </div>
            {!isRadio() && (
              <div className={styles.progressContainer}>
                <span>{dayjs(progress).format("mm:ss")}</span>
                <div className={styles.progress}>
                  <div
                    className={styles.progressBar}
                    style={{ width: `${(progress / (queue?.progress?.mediaLength ?? 1)) * 100}%` }}
                  />
                </div>
                <span>{dayjs(queue?.progress?.mediaLength).format("mm:ss")}</span>
              </div>
            )}
          </div>
        </div>
        {lyrics && <NowPlayingLyrics progress={progress} lyrics={lyrics} config={config} />}
      </div>
    </Card>
  );
};

export default NowPlaying;
