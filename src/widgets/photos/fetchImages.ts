import { BASE_DIRECTORY } from "@/helpers/types";
import { fetch } from "@tauri-apps/plugin-http";
import { exists, mkdir, readDir, readFile, remove, writeFile } from "@tauri-apps/plugin-fs";
import { info, warn } from "@tauri-apps/plugin-log";
import { Config } from ".";

const CACHE_DIR = "photos-cache";

const MIME_BY_EXT: Record<string, string> = {
  jpg: "image/jpeg",
  png: "image/png",
  webp: "image/webp",
  gif: "image/gif",
};
const EXT_BY_MIME = Object.fromEntries(Object.entries(MIME_BY_EXT).map(([ext, mime]) => [mime, ext]));

type ImmichAsset = { id: string; url: string };

const getAssetsFromImmich = async (config: Config): Promise<ImmichAsset[]> => {
  let page = 1;
  let nextPage;
  const assets: ImmichAsset[] = [];

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
    const items = response?.assets?.items ?? [];

    for (const asset of items) {
      assets.push({
        id: asset.id,
        url: `${config.immichUrl}/api/assets/${asset.id}/thumbnail?key=${config.immichShareKey}&size=preview`,
      });
    }

    nextPage = response.assets.nextPage;
  } while (nextPage && ++page <= 5);

  info(`[Photos] Fetched ${assets.length} images from Immich`);
  return assets;
};

const bytesToBase64 = (bytes: Uint8Array) => {
  const CHUNK_SIZE = 0x8000;
  let binary = "";
  for (let i = 0; i < bytes.length; i += CHUNK_SIZE) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK_SIZE));
  }
  return btoa(binary);
};

const readCachedImages = async (): Promise<string[]> => {
  if (!(await exists(CACHE_DIR, { baseDir: BASE_DIRECTORY }))) return [];

  const entries = await readDir(CACHE_DIR, { baseDir: BASE_DIRECTORY });
  const images: string[] = [];

  for (const entry of entries) {
    if (!entry.isFile) continue;

    try {
      const bytes = await readFile(`${CACHE_DIR}/${entry.name}`, { baseDir: BASE_DIRECTORY });
      const ext = entry.name.split(".").pop() ?? "jpg";
      images.push(`data:${MIME_BY_EXT[ext] ?? "image/jpeg"};base64,${bytesToBase64(bytes)}`);
    } catch (e) {
      warn(`[Photos] Failed to read cached image ${entry.name}: ${e}`);
    }
  }

  return images;
};

const syncCachedImages = async (assets: ImmichAsset[]): Promise<string[]> => {
  await mkdir(CACHE_DIR, { baseDir: BASE_DIRECTORY, recursive: true });

  const entries = await readDir(CACHE_DIR, { baseDir: BASE_DIRECTORY });
  const cachedIds = new Set(entries.map(entry => entry.name.split(".")[0]));
  const currentIds = new Set(assets.map(asset => asset.id));

  for (const entry of entries) {
    if (!currentIds.has(entry.name.split(".")[0])) {
      await remove(`${CACHE_DIR}/${entry.name}`, { baseDir: BASE_DIRECTORY });
    }
  }

  for (const asset of assets) {
    if (cachedIds.has(asset.id)) continue;

    try {
      const response = await fetch(asset.url);
      const contentType = response.headers.get("content-type") ?? "image/jpeg";
      const ext = EXT_BY_MIME[contentType] ?? "jpg";
      const bytes = new Uint8Array(await response.arrayBuffer());
      await writeFile(`${CACHE_DIR}/${asset.id}.${ext}`, bytes, { baseDir: BASE_DIRECTORY });
    } catch (e) {
      warn(`[Photos] Failed to cache image ${asset.id}: ${e}`);
    }
  }

  return readCachedImages();
};

const fetchImages = async (config: Config) => {
  let images: string[] = [];

  if (config.useStaticLinks) {
    images = config.images;
  } else if (!config.immichUrl || !config.immichAccessToken || !config.immichAlbumId || !config.immichShareKey) {
    warn("[Photos] Cannot get images from Immich, missing required fields");
    return config.savePhotosToDisk ? await readCachedImages() : [];
  } else {
    let assets: ImmichAsset[] = [];
    let fetchFailed = false;

    try {
      assets = await getAssetsFromImmich(config);
    } catch (e) {
      fetchFailed = true;
      info(`[Photos] Error fetching images from Immich: ${e}`);
    }

    if (config.savePhotosToDisk) {
      try {
        images = fetchFailed ? await readCachedImages() : await syncCachedImages(assets);
      } catch (e) {
        warn(`[Photos] Failed to read/sync photo cache on disk: ${e}`);
      }
    } else {
      images = assets.map(asset => asset.url);
    }
  }

  images.sort(() => Math.random() - 0.5);
  return images;
};

export default fetchImages;
