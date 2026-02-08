import { ClockThemeComponent } from "@/helpers/types";
import useEventListener, { EventType } from "@/hooks/event";
import { useCallback, useEffect, useState } from "react";
import { Config } from ".";
import loadImages from "./loadImages";
import styles from "./photos.module.scss";

const Component: ClockThemeComponent<Config> = ({ config, now }) => {
  const [photos, setPhotos] = useState<string[]>();
  const [index, setIndex] = useState(0);
  const [loadedImage, setLoadedImage] = useState<string>();

  useEffect(() => {
    loadImages(config).then(setPhotos);
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
      <h1>Photos Theme</h1>
      <pre>{JSON.stringify(config, null, 2)}</pre>
      <h2>{now.format("HH:mm:ss")}</h2>
    </div>
  );
};

export default Component;
