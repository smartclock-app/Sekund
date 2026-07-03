import { fetch } from "@tauri-apps/plugin-http";
import { info, warn } from "@tauri-apps/plugin-log";
import { Config } from ".";

const getImagesFromImmich = async (config: Config) => {
  try {
    let page = 1;
    let nextPage;
    const images = [];

    do {
      const request = await fetch(`${config.immichUrl}/api/search/metadata`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${config.immichAccessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          albumIds: [config.immichAlbumId],
          page: page,
        }),
      });

      const response = await request.json();
      const assets = response?.assets?.items ?? [];

      for (const asset of assets) {
        images.push(`${config.immichUrl}/api/assets/${asset.id}/thumbnail?key=${config.immichShareKey}&size=preview`);
      }

      nextPage = response.assets.nextPage;
    } while (nextPage && ++page <= 5);

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
