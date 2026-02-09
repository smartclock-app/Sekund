import type { Moment } from "moment";

export interface AlexaLoginResponse {
  response: {
    tokens: {
      cookies: Record<
        string,
        Array<{
          Name: string;
          Value: string;
        }>
      >;
    };
  };
}

export interface Device {
  accountName: string;
  deviceType: string;
  serialNumber: string;
  deviceFamily: string;
  parentClusters: string[];
}

export interface Memory {
  updatedDateTime?: Moment;
  value?: string;
}

export interface Notification {
  alarmLabel: string;
  alarmTime: number;
  createdDate: number;
  deferredAtTime: string;
  deviceName: string;
  deviceSerialNumber: string;
  id: string;
  lastOccurrenceTimeInMilli: number;
  lastTriggerTimeInUtc: string;
  lastUpdatedDate: number;
  loopCount: number;
  originalDate: string;
  originalDurationInMillis: number;
  originalTime: string;
  remainingTime: number;
  reminderLabel: string;
  snoozedToTime: string;
  status: string;
  timerLabel: string;
  triggerTime: number;
  type: string;
}

export interface Queue {
  infoText?: {
    subText1?: string;
    subText2?: string;
    title?: string;
  };
  mainArt?: {
    fullUrl?: string;
  };
  mediaReference?: {
    value?: string;
  };
  playerState?: string;
  progress?: {
    mediaLength?: number;
    mediaProgress?: number;
  };
  provider?: {
    providerLogo?: {
      altText?: string;
      url?: string;
    };
    providerName?: string;
  };
  timestamp?: Moment;
}
