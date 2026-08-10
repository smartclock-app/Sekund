export type TokenPair = [accessToken: string, refreshToken: string];

export class TraktManagerAPIError extends Error {
  constructor(
    public statusCode: number,
    public reasonPhrase: string | undefined,
    public response: Response,
  ) {
    super(`Trakt API Error: ${statusCode} ${reasonPhrase ?? ""}`);
  }
}

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  scope: string;
  createdAt: number;
}

export interface DeviceCodeResponse {
  deviceCode: string;
  userCode: string;
  verificationUrl: string;
  expiresIn: number;
  interval: number;
}

export type DevicePollResult =
  | { status: "success"; tokens: AccessTokenResponse }
  | { status: "pending" }
  | { status: "slow_down" }
  | { status: "already_used" }
  | { status: "expired" }
  | { status: "denied" }
  | { status: "not_found" };

export interface ListItem {
  type: string;
  movie?: ListItemType;
  show?: ListItemType;
}

export interface ListItemType {
  ids: {
    tmdb: number;
    slug: string;
  };
}

export interface ShowSummary {
  title: string;
  status: string;
}

export interface MovieSummary {
  title: string;
  released: string;
  status: string;
}
