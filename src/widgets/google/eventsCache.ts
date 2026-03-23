import { CalendarEvent } from "@/helpers/types";

let cache: CalendarEvent[] | null = null;

export const getCache = () => cache;
export const setCache = (events: CalendarEvent[]) => { cache = events; };
export const clearCache = () => { cache = null; };
