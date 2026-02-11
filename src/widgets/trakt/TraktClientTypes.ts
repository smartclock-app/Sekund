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
