import { fetch } from "@tauri-apps/plugin-http";
import { info, warn } from "@tauri-apps/plugin-log";
import { Config } from ".";

const getImagesFromImmich = async (config: Config) => {
  try {
    const request = await fetch(`${config.immichUrl}/api/albums/${config.immichAlbumId}`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${config.immichAccessToken}`,
      },
    });
    const response = await request.json();
    const assets = response["assets"];
    const images = assets.map(
      (e: { id: string }) =>
        `${config.immichUrl}/api/assets/${e["id"]}/thumbnail?key=${config.immichShareKey}&size=preview`,
    );

    info(`[Photos] Fetched ${images.length} images from Immich`);
    return images;
  } catch (e) {
    info(`[Photos] Error fetching images from Immich: ${e}`);
    return [];
  }
};

const fetchImages = async (config: Config) => {
  let images: string[] = [];
  if (config.useStaticLinks) {
    images = config.images;
  } else {
    if (!config.immichUrl || !config.immichAccessToken || !config.immichAlbumId || !config.immichShareKey) {
      warn("[Photos] Cannot get images from Immich, missing required fields");
      return [];
    }

    images = await getImagesFromImmich(config);
  }

  images.sort(() => Math.random() - 0.5);
  return images;
};

export default fetchImages;
