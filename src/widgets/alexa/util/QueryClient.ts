import useConfigStore from "@/hooks/useConfigStore";
import { fetch } from "@tauri-apps/plugin-http";
import { error, info, warn } from "@tauri-apps/plugin-log";
import dayjs from "dayjs";
import { AlexaLoginResponse, Device, Memory, Notification, Queue } from "../util/types";

const DANGEROUS_OPTIONS = {
  // danger: {
  //   acceptInvalidCerts: true,
  //   acceptInvalidHostnames: true,
  // },
  // proxy: {
  //   all: "http://127.0.0.1:8888",
  // },
};

class QueryClient {
  private static _browser = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:1.0) bash-script/1.0";
  private _customerIds: Record<string, string> = {};
  private _csrfToken: string = "";
  private _lastLogin?: [dayjs.Dayjs, boolean];
  private _loginPromise?: Promise<boolean>;

  constructor(
    private _cookies: Record<string, string>,
    private _loginToken: string,
  ) {}

  private async _parseCookies(json: AlexaLoginResponse) {
    const cookiesMap: Record<string, any[]> = json["response"]["tokens"]["cookies"];

    let cookies = "";
    for (const domain of Object.keys(cookiesMap)) {
      for (const cookieData of cookiesMap[domain]) {
        const cookieValue = (cookieData["Value"] as string).replace(/^[" ]+'/, "").replace(/[" ]$/, "");
        cookies += `${cookieData["Name"]}=${cookieValue}; `;
      }
    }

    return cookies;
  }

  public async checkStatus(userId: string): Promise<boolean> {
    if (!this._cookies[userId]) return false;

    const response = await fetch("https://alexa.amazon.co.uk/api/customer-status", {
      method: "GET",
      headers: {
        DNT: "1",
        "User-Agent": QueryClient._browser,
        Connection: "keep-alive",
        Cookie: this._cookies[userId],
        Origin: "https://alexa.amazon.co.uk",
      },
      ...DANGEROUS_OPTIONS,
    });

    if (response.status === 200) {
      // Also fetch and store customer ID
      const usersResponse = await fetch("https://alexa.amazon.co.uk/api/users/me", {
        method: "GET",
        headers: {
          DNT: "1",
          "User-Agent": QueryClient._browser,
          Connection: "keep-alive",
          Cookie: this._cookies[userId],
        },
        ...DANGEROUS_OPTIONS,
      });

      if (usersResponse.ok) {
        const userData = await usersResponse.json();
        this._customerIds[userId] = userData.id;
      }

      return true;
    }

    return false;
  }

  public async login(userId: string, token?: string) {
    if (this._loginPromise) return this._loginPromise;

    this._loginPromise = (async () => {
      if (this._lastLogin != null) {
        const diff = dayjs().diff(this._lastLogin[0], "seconds");
        if (diff < 15) return this._lastLogin[1];
      }

      info(`[Alexa] Checking status for user: ${userId}`);
      const status = await this.checkStatus(userId);
      if (status) {
        info(`[Alexa] User ${userId} logged in`);
        return true;
      }

      token ??= this._loginToken;
      if (!token || token.trim() === "") {
        error("No token provided");
        return false;
      }

      info(`[Alexa] Logging in user: ${userId}`);

      const postBody = new URLSearchParams();
      postBody.append("app_name", "Amazon Alexa");
      postBody.append("requested_token_type", "auth_cookies");
      postBody.append("domain", "www.amazon.co.uk");
      postBody.append("source_token_type", "refresh_token");
      postBody.append("source_token", token);

      const response = await fetch("https://api.amazon.co.uk/ap/exchangetoken/cookies", {
        method: "POST",
        body: postBody,
        headers: {
          "x-amzn-identity-auth-domain": "api.amazon.co.uk",
          "Content-Type": "application/x-www-form-urlencoded",
          Origin: "https://alexa.amazon.co.uk",
        },
        ...DANGEROUS_OPTIONS,
      });

      if (response.status != 200) {
        warn(`[Alexa] Login failed with status code: ${response.status}`);
        return false;
      }

      this._cookies[userId] = await this._parseCookies(await response.json());

      const csrfUrls = [
        "https://alexa.amazon.co.uk/api/language",
        "https://alexa.amazon.co.uk/templates/oobe/d-device-pick.handlebars",
        "https://alexa.amazon.co.uk/api/devices-v2/device?cached=false",
      ];

      let csrfTokenExists = false;

      for (const url of csrfUrls) {
        const response = await fetch(url, {
          method: "GET",
          headers: {
            DNT: "1",
            Connection: "keep-alive",
            Referer: "https://alexa.amazon.co.uk/spa/index.html",
            Origin: "https://alexa.amazon.co.uk",
            Cookie: this._cookies[userId],
          },
          ...DANGEROUS_OPTIONS,
        });

        if (response.headers.get("set-cookie")?.includes("csrf=")) {
          const csrf = response.headers.get("set-cookie")!.split(" ")[0];
          this._cookies[userId] = this._cookies[userId]! + csrf;
          this._csrfToken = csrf.split("=")[1];
          csrfTokenExists = true;
          break;
        }
      }

      if (!csrfTokenExists) {
        warn("[Alexa] CSRF Token not found");
        return false;
      }

      await useConfigStore.getState().editConfigByPath("widgets.alexa.cookies", this._cookies);

      return true;
    })();

    try {
      const loggedIn = await this._loginPromise;
      this._lastLogin = [dayjs(), loggedIn];
      return loggedIn;
    } finally {
      this._loginPromise = undefined;
    }
  }

  public async getDevices(userId: string) {
    const isLoggedIn = await this.login(userId);
    if (!isLoggedIn) throw new Error("User not logged in");

    const response = await fetch("https://alexa.amazon.co.uk/api/devices-v2/device?cached=false", {
      method: "GET",
      headers: {
        DNT: "1",
        Referer: "https://alexa.amazon.co.uk/spa/index.html",
        Origin: "https://alexa.amazon.co.uk",
        Cookie: this._cookies[userId],
        csrf: this._csrfToken,
      },
      ...DANGEROUS_OPTIONS,
    });

    const data = await response.json();

    const devices = (data["devices"] as any[]).map<Device>(device => ({
      accountName: device["accountName"]!,
      deviceFamily: device["deviceFamily"]!,
      deviceType: device["deviceType"]!,
      serialNumber: device["serialNumber"]!,
      parentClusters: device["parentClusters"] ?? [],
    }));

    devices.sort((a, b) => a.accountName.localeCompare(b.accountName));
    return devices;
  }

  public async getMemories(userId: string) {
    const isLoggedIn = await this.login(userId);
    if (!isLoggedIn) throw Error("User not logged in");

    const response = await fetch(
      "https://alexa.amazon.co.uk/api/memories/search?maxResults=50&sortCriteria=CREATED_REVERSE",
      {
        method: "POST",
        headers: {
          DNT: "1",
          "User-Agent": QueryClient._browser,
          Referer: "https://alexa.amazon.co.uk/spa/index.html",
          Origin: "https://alexa.amazon.co.uk",
          "Content-Type": "application/json; charset=UTF-8",
          Cookie: this._cookies[userId],
          csrf: this._csrfToken,
        },
        ...DANGEROUS_OPTIONS,
      },
    );

    // List<Memory>.from(json["memories"]!.map((x) => Memory.fromJson(x)))

    const data = await response.json();

    if (data["memories"] == null) return [];

    return (data["memories"] as any[]).map<Memory>(json => ({
      updatedDateTime: json["updatedDateTime"] != null ? dayjs(json["updatedDateTime"]) : undefined,
      value: json["value"],
    }));
  }

  public async getNotifications(userId: string) {
    // if (_cookies[userId] == null) throw Exception("User not logged in");
    const isLoggedIn = await this.login(userId);
    if (!isLoggedIn) throw new Error("User not logged in");

    const response = await fetch("https://alexa.amazon.co.uk/api/notifications", {
      method: "GET",
      headers: {
        DNT: "1",
        "User-Agent": QueryClient._browser,
        Referer: "https://alexa.amazon.co.uk/spa/index.html",
        Origin: "https://alexa.amazon.co.uk",
        "Content-Type": "application/json; charset=UTF-8",
        Cookie: this._cookies[userId],
        csrf: this._csrfToken,
      },
      ...DANGEROUS_OPTIONS,
    });

    const data = await response.json();

    if (data["notifications"] == null) return [];

    return (data["notifications"] as any[]).map<Notification>(json => ({
      alarmLabel: json["alarmLabel"],
      alarmTime: json["alarmTime"],
      createdDate: json["createdDate"],
      deferredAtTime: json["deferredAtTime"],
      deviceName: json["deviceName"],
      deviceSerialNumber: json["deviceSerialNumber"],
      id: json["id"],
      lastOccurrenceTimeInMilli: json["lastOccurrenceTimeInMilli"],
      lastTriggerTimeInUtc: json["lastTriggerTimeInUtc"],
      lastUpdatedDate: json["lastUpdatedDate"],
      loopCount: json["loopCount"],
      originalDate: json["originalDate"],
      originalDurationInMillis: json["originalDurationInMillis"],
      originalTime: json["originalTime"],
      remainingTime: json["remainingTime"],
      reminderLabel: json["reminderLabel"],
      snoozedToTime: json["snoozedToTime"],
      status: json["status"],
      timerLabel: json["timerLabel"],
      triggerTime: json["triggerTime"],
      type: json["type"],
    }));
  }

  public async getQueue(userId: string, deviceName: string): Promise<Queue> {
    const isLoggedIn = await this.login(userId);
    if (!isLoggedIn) throw new Error("User not logged in");

    const devices = await this.getDevices(userId);
    const device = devices.find(device => device.accountName == deviceName) ?? ({} as Device);
    if (!device.serialNumber) return {};

    const customerId = this._customerIds[userId];
    const url = `https://alexa.amazon.co.uk/api/np/list-media-sessions?deviceSerialNumber=${device.serialNumber}&deviceType=${device.deviceType}&mediaOwnerCustomerId=${customerId}`;

    const response = await fetch(url, {
      method: "GET",
      headers: {
        DNT: "1",
        "User-Agent": QueryClient._browser,
        Referer: "https://alexa.amazon.co.uk/spa/index.html",
        Origin: "https://alexa.amazon.co.uk",
        "Content-Type": "application/json; charset=UTF-8",
        Cookie: this._cookies[userId],
        csrf: this._csrfToken,
      },
      ...DANGEROUS_OPTIONS,
    });

    const data = await response.json();

    if (data["mediaSessionList"] == null) return {};

    const sessionList = data["mediaSessionList"] as any[];
    if (sessionList.length === 0) return {};

    const session = sessionList.find(session => {
      const endpointList = session?.["endpointList"];

      const sessionIncludesDevice = endpointList?.some((endpoint: any) => {
        const endpointSerial = endpoint["id"]?.["deviceSerialNumber"];
        const endpointType = endpoint["id"]?.["deviceType"];

        return endpointSerial == device.serialNumber && endpointType == device.deviceType;
      });

      return sessionIncludesDevice ?? false;
    });

    const nowplaying = session?.["nowPlayingData"];

    if (!nowplaying?.["playerState"]) return {};

    const timestamp = dayjs(response.headers.get("Date")!);
    return { ...nowplaying, timestamp };
  }
}

export default QueryClient;
