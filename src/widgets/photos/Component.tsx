import getOrdinal from "@/helpers/getOrdinal";
import { ClockThemeComponent } from "@/helpers/types";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { useCallback, useEffect, useState } from "react";
import { Config } from ".";
import fetchImages from "./fetchImages";
import styles from "./photos.module.scss";

const Component: ClockThemeComponent<Config> = ({ config, clockConfig, now }) => {
  const [photos, setPhotos] = useState<string[]>();
  const [index, setIndex] = useState(0);
  const [loadedImage, setLoadedImage] = useState<string>();

  useEffect(() => {
    fetchImages(config).then(setPhotos);
  }, [config]);

  useEffect(() => {
    if (!photos?.[index]) return;

    let cancelled = false;
    const img = new Image();
    img.onload = () => {
      if (!cancelled) setLoadedImage(photos[index]);
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

  return (
    <div className={styles.container} style={{ backgroundImage: loadedImage ? `url(${loadedImage})` : undefined }}>
      <div className={styles.overlay}>
        <div className={styles.time}>
          <p className={styles.main}>{now.format(`${clockConfig.format == "12h" ? "hh" : "HH"}:mm`)}</p>
          <div className={styles.sub}>
            <p>{now.format("A")}</p>
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
