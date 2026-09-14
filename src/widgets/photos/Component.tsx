import getOrdinal from "@/components/clock/getOrdinal";
import { ClockThemeComponent } from "@/helpers/types";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { warn } from "@tauri-apps/plugin-log";
import { useCallback, useEffect, useRef, useState } from "react";
import { Config } from ".";
import fetchImages from "./fetchImages";
import styles from "./photos.module.scss";

const Component: ClockThemeComponent<Config> = ({ config, clockConfig, now }) => {
  const [photos, setPhotos] = useState<string[]>();
  const [index, setIndex] = useState(0);
  const [loadedImage, setLoadedImage] = useState<string>();
  // Tracks whether the last fetch genuinely failed (network/API error), as opposed to succeeding
  // with a legitimately empty album - only the former is worth retrying automatically.
  const lastFetchFailed = useRef(false);

  const refreshPhotos = useCallback(() => {
    fetchImages(config).then(({ images, failed }) => {
      lastFetchFailed.current = failed;
      setPhotos(images);
    });
  }, [config]);

  useEffect(() => {
    refreshPhotos();
  }, [refreshPhotos]);

  // Re-fetching the album hits the network (and, with savePhotosToDisk, disk), so only retry after
  // an actual failure rather than on every Refresh tick. A successful fetch, even of an empty
  // album, is left alone until the config changes.
  useEventListener(EventType.Refresh, () => {
    if (lastFetchFailed.current) refreshPhotos();
  });

  useEffect(() => {
    if (!photos?.[index]) return;

    let cancelled = false;
    const img = new Image();
    img.onload = () => {
      if (!cancelled) setLoadedImage(photos[index]);
    };
    img.onerror = () => {
      if (!cancelled) warn(`[Photos] Failed to load image, keeping last loaded photo: ${photos[index]}`);
    };
    img.src = photos[index];
    return () => {
      cancelled = true;
    };
  }, [index, photos]);

  const nextImage = useCallback(() => {
    setIndex(i => (i + 1) % (photos?.length || 1));
  }, [photos]);

  useEventListener(EventType.Refresh, nextImage);
  useEventListener(EventType.SkipPhoto, nextImage);

  return (
    <div className={styles.container} style={{ backgroundImage: loadedImage ? `url(${loadedImage})` : undefined }}>
      <div className={styles.overlay}>
        <div className={styles.time}>
          <p className={styles.main}>{now.format(`${clockConfig.format == "12h" ? "hh" : "HH"}:mm`)}</p>
          <div className={styles.sub}>
            {clockConfig.format == "12h" && <p>{now.format("A")}</p>}
            {clockConfig.showSeconds && <p>{now.format("ss")}</p>}
          </div>
        </div>
        <div className={styles.date}>
          {now.format(`dddd D`)}
          <sup>{getOrdinal(now.date())}</sup>
          {now.format(" MMMM YYYY")}
        </div>
      </div>
    </div>
  );
};

export default Component;
