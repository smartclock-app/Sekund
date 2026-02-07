import { BaseDirectory, exists, readTextFile, writeTextFile } from "@tauri-apps/plugin-fs";
import { fetch } from "@tauri-apps/plugin-http";
import { Mutex } from "async-mutex";
import moment, { type Moment } from "moment";
import { Device, Memory, Notification, Queue } from "./types";

const BASE_DIRECTORY = BaseDirectory.AppData;

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
  private static _browser =
    "AppleWebKit PitanguiBridge/2.2.632832.0-[HARDWARE=iPhone14_5][SOFTWARE=18.0.1][DEVICE=iPhone]";
  private _cookies: Record<string, string> = {};
  private _csrfToken: string = "";
  private _mutex = new Mutex();
  private _lastLogin?: [Moment, boolean];
  public isInitialized = false;

  constructor(
    private _cookieFile: string,
    public loginToken?: string,
  ) {}

  public async init() {
    try {
      if (await exists(this._cookieFile, { baseDir: BASE_DIRECTORY })) {
        const content = await readTextFile(this._cookieFile, { baseDir: BASE_DIRECTORY });
        const cookies = JSON.parse(content);
        this._cookies = cookies;
      }
    } catch (error) {
      console.error("Failed to load cookies:", error);
    }
  }

  private async _parseCookies(json: any) {
    const cookiesMap: any = json["response"]["tokens"]["cookies"];

    let cookies = "";
    for (const domain of Object.keys(cookiesMap)) {
      for (const cookieData of cookiesMap[domain]) {
        const cookieValue = (cookieData["Value"] as string).replace(/^[" ]+'/, "").replace(/[" ]$/, "");
        cookies += `${cookieData["Name"]}=${cookieValue}; `;
      }
    }

    return cookies;
  }

  public async checkStatus(userId: string) {
    const response = await fetch("https://alexa.amazon.co.uk/api/bootstrap?version=0", {
      method: "GET",
      headers: {
        DNT: "1",
        "User-Agent": QueryClient._browser,
        Cookie: this._cookies[userId],
        Origin: "https://alexa.amazon.co.uk",
      },
      ...DANGEROUS_OPTIONS,
    });

    return response.status == 200;
  }

  public async login(userId: string, token?: string) {
    const loggedIn = await this._mutex.runExclusive(async () => {
      if (this._lastLogin != null) {
        const diff = moment().diff(this._lastLogin[0], "seconds");
        if (diff < 15) return this._lastLogin[1];
      }

      console.log("Checking status for user: $userId", "trace");
      const status = await this.checkStatus(userId);
      if (status == true) return true;

      token ??= this.loginToken;
      if (!token || token.trim() === "") {
        console.error("No token provided");
        return false;
      }

      console.log("Logging in user: $userId", "trace");

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
        console.warn(`Login failed with status code: ${response.status}`);
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

        if (csrfTokenExists) break;
      }

      if (!csrfTokenExists) {
        console.error("CSRF Token not found");
        return false;
      }

      await writeTextFile(this._cookieFile, JSON.stringify(this._cookies), { baseDir: BASE_DIRECTORY });

      return true;
    });

    this._lastLogin = [moment(), loggedIn];
    return loggedIn;
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
      updatedDateTime: json["updatedDateTime"] != null ? moment(json["updatedDateTime"]) : undefined,
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

    const url = `https://alexa.amazon.co.uk/api/np/list-media-sessions?deviceSerialNumber=${device.serialNumber}&deviceType=${device.deviceType}`;
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

    const timestamp = moment(response.headers.get("Date")!);
    return { ...nowplaying, timestamp };
  }
}

export default QueryClient;
