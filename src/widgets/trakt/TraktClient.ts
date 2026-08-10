import useDatabaseStore from "@/hooks/useDatabaseStore";
import { error, info } from "@tauri-apps/plugin-log";
import dayjs from "dayjs";
import { Config } from ".";
import {
  DeviceCodeResponse,
  DevicePollResult,
  ListItem,
  MovieSummary,
  ShowSummary,
  TokenPair,
  TraktManagerAPIError,
} from "./TraktClientTypes";

/// Based on dart package:trakt_dart, only includes the code necessary for retrieving list items. (Translated from Dart to TypeScript)
class TraktManager {
  static _baseURL: string = "api.trakt.tv";
  _headers: Record<string, string>;

  constructor(
    public clientId: string,
    public clientSecret: string,
    public redirectURI: string,
    public accessToken: string,
    public refreshToken: string,
  ) {
    this._headers = {
      "Content-Type": "application/json",
      "trakt-api-version": "2",
      "trakt-api-key": clientId,
    };
  }

  async refreshAccessToken(): Promise<TokenPair> {
    const response = await fetch(`https://${TraktManager._baseURL}/oauth/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        refresh_token: this.refreshToken,
        client_id: this.clientId,
        client_secret: this.clientSecret,
        redirect_uri: this.redirectURI,
        grant_type: "refresh_token",
      }),
    });

    if (![200, 201, 204].includes(response.status)) {
      throw new TraktManagerAPIError(response.status, response.statusText, response);
    }

    const data = await response.json();
    this.accessToken = data.access_token;
    this.refreshToken = data.refresh_token;

    return [this.accessToken, this.refreshToken];
  }

  async generateDeviceCode(): Promise<DeviceCodeResponse> {
    const response = await fetch(`https://${TraktManager._baseURL}/oauth/device/code`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ client_id: this.clientId }),
    });

    if (response.status != 200) {
      throw new TraktManagerAPIError(response.status, response.statusText, response);
    }

    const data = await response.json();
    return {
      deviceCode: data.device_code,
      userCode: data.user_code,
      verificationUrl: data.verification_url,
      expiresIn: data.expires_in,
      interval: data.interval,
    };
  }

  async pollDeviceToken(deviceCode: string): Promise<DevicePollResult> {
    const response = await fetch(`https://${TraktManager._baseURL}/oauth/device/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        code: deviceCode,
        client_id: this.clientId,
        client_secret: this.clientSecret,
      }),
    });

    switch (response.status) {
      case 200: {
        const data = await response.json();
        this.accessToken = data.access_token;
        this.refreshToken = data.refresh_token;
        return {
          status: "success",
          tokens: {
            accessToken: data.access_token,
            tokenType: data.token_type,
            expiresIn: data.expires_in,
            refreshToken: data.refresh_token,
            scope: data.scope,
            createdAt: data.created_at,
          },
        };
      }
      case 400:
        return { status: "pending" };
      case 404:
        return { status: "not_found" };
      case 409:
        return { status: "already_used" };
      case 410:
        return { status: "expired" };
      case 418:
        return { status: "denied" };
      case 429:
        return { status: "slow_down" };
      default:
        throw new TraktManagerAPIError(response.status, response.statusText, response);
    }
  }

  /// Fetches the Trakt list and compares it to the watchlist database.
  /// Returns a tuple of a boolean indicating if the list has changed and a set of item IDs.
  async getClockList(config: Config, watchlist: any[]): Promise<[boolean, Set<string>]> {
    let items: ListItem[];

    const listId = config.listId;
    try {
      items = await this.getListItems(listId);
    } catch (e) {
      if (!(e instanceof TraktManagerAPIError)) throw e;
      if (e.statusCode != 401) throw e;
      await this.refreshAccessToken();
      items = await this.getListItems(listId);
    }

    if (config.includeWatchlist) {
      const watchlistItems = await this.getWatchlistItems();
      if (watchlistItems.length > 0) {
        items.push(...watchlistItems);
      }
    }

    const watchlistIds = new Set(watchlist.map(e => e["id"]));

    const itemTypes = new Set(["show", "movie", ...(config.includeEpisodesAsShow ? ["episode"] : [])]);

    const itemIds = new Set(
      items
        .filter(e => itemTypes.has(e.type))
        .map(e => {
          if (e.type == "show" || e.type == "episode") {
            return `tv--${e.show?.ids.slug}`;
          } else {
            return `movie--${e.movie?.ids.slug}`;
          }
        }),
    );

    const setsHaveChanged = itemIds.difference(watchlistIds).size > 0 || watchlistIds.difference(itemIds).size > 0;
    return [setsHaveChanged, itemIds];
  }

  async getListItems(listId: string) {
    const request = `users/me/lists/${listId}/items`;
    const headers = this._headers;
    headers["Authorization"] = `Bearer ${this.accessToken}`;

    const url = new URL(`https://${TraktManager._baseURL}/${request}`);
    const response = await fetch(url, { headers });

    if (![200, 201, 204].includes(response.status)) {
      throw new TraktManagerAPIError(response.status, response.statusText, response);
    }

    const jsonResult = await response.json();

    if (Array.isArray(jsonResult)) return jsonResult;
    return [];
  }

  async getShowSummary(slug: string) {
    const request = `shows/${slug}`;
    const headers = this._headers;
    headers["Authorization"] = `Bearer ${this.accessToken}`;

    const url = new URL(`https://${TraktManager._baseURL}/${request}`);

    const response = await fetch(url, { headers });

    if (![200, 201, 204].includes(response.status)) {
      throw new TraktManagerAPIError(response.status, response.statusText, response);
    }

    const jsonResult = (await response.json()) as ShowSummary;
    return jsonResult;
  }

  async getShowNextEpisode(slug: string) {
    const request = `shows/${slug}/next_episode`;
    const headers = this._headers;
    headers["Authorization"] = `Bearer ${this.accessToken}`;
    const url = new URL(`https://${TraktManager._baseURL}/${request}?extended=full`);

    const response = await fetch(url, { headers });
    if (response.status == 204) return null;

    if (![200, 201].includes(response.status)) {
      throw new TraktManagerAPIError(response.status, response.statusText, response);
    }

    const jsonResult = await response.json();
    return jsonResult["first_aired"] as string;
  }

  async getTodayEpisode(slug: string) {
    const request = `shows/${slug}/last_episode`;
    const headers = this._headers;
    headers["Authorization"] = `Bearer ${this.accessToken}`;

    const query = new URLSearchParams({ extended: "full" });
    const url = new URL(`https://${TraktManager._baseURL}/${request}?${query.toString()}`);

    const response = await fetch(url, { headers });
    if (response.status == 204) return null;

    if (![200, 201].includes(response.status)) {
      throw new TraktManagerAPIError(response.status, response.statusText, response);
    }

    const jsonResult = await response.json();
    const airedDate = jsonResult["first_aired"];

    // if (await isEpisodeWatched(jsonResult["ids"]["trakt"])) {
    //   return null;
    // }

    if (!dayjs(airedDate).isSame(dayjs(), "day")) {
      return null;
    }

    return airedDate as string;
  }

  async getMovieSummary(slug: string) {
    const request = `movies/${slug}`;
    const headers = this._headers;
    headers["Authorization"] = `Bearer ${this.accessToken}`;

    const url = new URL(`https://${TraktManager._baseURL}/${request}?extended=full`);
    const response = await fetch(url, { headers });

    if (![200, 201, 204].includes(response.status)) {
      throw new TraktManagerAPIError(response.status, response.statusText, response);
    }

    const jsonResult = (await response.json()) as MovieSummary;
    return jsonResult;
  }

  async getWatchlistItems() {
    const request = "sync/watchlist/all/released/desc";
    const headers = this._headers;
    headers["Authorization"] = `Bearer ${this.accessToken}`;

    const url = new URL(`https://${TraktManager._baseURL}/${request}`);
    const response = await fetch(url, { headers });

    if (![200, 201, 204].includes(response.status)) {
      throw new TraktManagerAPIError(response.status, response.statusText, response);
    }

    const jsonResult = await response.json();
    if (Array.isArray(jsonResult)) {
      return jsonResult as ListItem[];
    }

    return [];
  }

  async updateWatchlist(items: Set<string>) {
    const database = useDatabaseStore.getState();
    if (!database.db) {
      await database.init();
    }

    info("[Watchlist] Refetching list item details");
    await database.write("DELETE FROM watchlist");

    const insertPromises = Array.from(items).map(async item => {
      const [type, slug] = item.split("--");

      let data: Record<string, any>;
      try {
        if (type == "movie") {
          const summary = await this.getMovieSummary(slug);

          data = {
            id: item,
            name: summary.title,
            status: summary.status,
            nextAirDate: summary.released,
          };
        } else {
          const [todayEpisode, nextEpisode, summary] = await Promise.all([
            this.getTodayEpisode(slug),
            this.getShowNextEpisode(slug),
            this.getShowSummary(slug),
          ]);

          data = {
            id: item,
            name: summary.title,
            status: summary.status,
            nextAirDate: todayEpisode ?? nextEpisode,
          };
        }

        return data;
      } catch (e) {
        error(`[Watchlist] Failed to insert ${item}: ${e}`);
        throw new Error(`${item}: ${e}`);
      }
    });

    const allData = await Promise.allSettled(insertPromises);
    const successfulData = allData.filter(r => r.status === "fulfilled").map(r => r.value);
    for (const d of successfulData) {
      await database.write("INSERT INTO watchlist (id, name, status, nextAirDate) VALUES (?, ?, ?, ?)", [
        d.id,
        d.name,
        d.status,
        d.nextAirDate,
      ]);
    }
  }
}

export default TraktManager;
