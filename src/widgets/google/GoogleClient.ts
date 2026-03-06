import { fetch } from "@tauri-apps/plugin-http";
import { info } from "@tauri-apps/plugin-log";
import dayjs, { Dayjs } from "dayjs";
import { Config } from ".";

interface GoogleTokenResponse {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
  token_type: string;
}

interface GoogleCalendarListItem {
  id: string;
  summary: string;
  description?: string;
  timeZone?: string;
  primary?: boolean;
  backgroundColor?: string;
}

interface GoogleCalendarEvent {
  id: string;
  summary: string;
  start: { dateTime: string; date?: string };
  end: { dateTime: string; date?: string };
  description?: string;
}

class GoogleClient {
  private accessToken: string;
  private refreshToken: string;
  private clientId: string;
  private clientSecret: string;
  private tokenExpiry: Dayjs;

  constructor(config: Config) {
    if (!config.accessToken || !config.refreshToken || !config.clientId || !config.clientSecret) {
      throw Error("[Calendar] API credentials must be set in the config file.");
    }

    this.accessToken = config.accessToken;
    this.refreshToken = config.refreshToken;
    this.clientId = config.clientId;
    this.clientSecret = config.clientSecret;
    this.tokenExpiry = dayjs(config.tokenExpiry);
  }

  get credentials() {
    return {
      accessToken: this.accessToken,
      refreshToken: this.refreshToken,
      tokenExpiry: dayjs(this.tokenExpiry).toISOString(),
    };
  }

  private async ensureValidToken(): Promise<void> {
    if (dayjs().isBefore(this.tokenExpiry)) {
      return; // Token still valid
    }

    // Token expired, refresh it
    try {
      const response = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
        body: new URLSearchParams({
          client_id: this.clientId,
          client_secret: this.clientSecret,
          refresh_token: this.refreshToken,
          grant_type: "refresh_token",
        }).toString(),
      });

      if (!response.ok) {
        throw new Error(`Token refresh failed: ${response.statusText}`);
      }

      const data: GoogleTokenResponse = await response.json();
      this.accessToken = data.access_token;
      this.tokenExpiry = dayjs().add(data.expires_in, "seconds");

      // TODO: Save updated token to your config file
      info("[Calendar] Token refreshed");
    } catch (error) {
      throw new Error(`[Calendar] Failed to refresh token: ${error}`);
    }
  }

  async getCalendarLists(): Promise<GoogleCalendarListItem[]> {
    await this.ensureValidToken();

    const response = await fetch("https://www.googleapis.com/calendar/v3/users/me/calendarList", {
      headers: {
        Authorization: `Bearer ${this.accessToken}`,
      },
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch calendar lists: ${response.statusText}`);
    }

    const data = await response.json();
    return data.items || [];
  }

  async getEventsForList(listId: string, maxEvents: number): Promise<GoogleCalendarEvent[]> {
    await this.ensureValidToken();

    const params = new URLSearchParams({
      timeMin: dayjs().toISOString(),
      singleEvents: "true",
      orderBy: "startTime",
      maxResults: maxEvents.toString(),
    });

    const response = await fetch(
      `https://www.googleapis.com/calendar/v3/calendars/${encodeURIComponent(listId)}/events?${params}`,
      {
        headers: {
          Authorization: `Bearer ${this.accessToken}`,
        },
      },
    );

    if (!response.ok) {
      throw new Error(`Failed to fetch events for calendar ${listId}: ${response.statusText}`);
    }

    const data = await response.json();
    return data.items || [];
  }
}

export default GoogleClient;
