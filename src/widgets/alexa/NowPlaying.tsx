import useDatabaseStore from "@/hooks/useDatabaseStore";
import { error, info } from "@tauri-apps/plugin-log";
import { useCallback, useEffect, useRef, useState } from "react";
import { Config } from ".";
import { Queue } from "./types";
import fetchLyrics from "./util/fetchLyrics";
import Lrc from "./util/Lrc";
import useQueryClient from "./util/useQueryClient";

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

  const getQueue = useCallback(
    async (queue: Queue, config: Config) => {
      if (!queryClientStore.client) {
        error("[Alexa] Query client not initialized");
        return;
      }

      if (queue?.playerState == "REFRESHING") return;

      info("[Alexa] Refetching queue");
      // const database = context.read<sqlite3.Database>();
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

      info(
        `[Alexa] Fetched queue - title: ${q.infoText?.title}, artist: ${q.infoText?.subText1}, lyrics found: ${lyricResult != null}`,
      );

      setLyrics(lyricResult);
      setQueue(q);
      setProgress(q.progress?.mediaProgress ?? 0);
    },
    [databaseStore, queryClientStore.client, config],
  );

  useEffect(() => {
    const initialize = async () => {
      if (!queryClientStore.client?.isInitialized) {
        await queryClientStore.init(config.token);
      }

      if (runOnce.current) return;
      runOnce.current = true;

      await getQueue(queue, config);
    };

    initialize();
  }, [config, queryClientStore.client]);

  return <>{JSON.stringify(queue, null, 2)}</>;
};

export default NowPlaying;
