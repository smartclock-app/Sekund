import Database from "@tauri-apps/plugin-sql";
import { create } from "zustand";

interface DatabaseStoreState {
  db: Database | null;
  init: () => Promise<void>;
  read: ReadDatabase;
  write: WriteDatabase;
}

export type ReadDatabase = <T>(query: string, params?: any[]) => Promise<T>;
export type WriteDatabase = (query: string, params?: any[]) => Promise<void>;

const useDatabaseStore = create<DatabaseStoreState>()(set => ({
  db: null,
  init: async () => {
    const db = await Database.load("sqlite:database.sqlite");
    set({ db });
  },
  read: async <T>(query: string, params?: any[]): Promise<T> => {
    let { db, init } = useDatabaseStore.getState();
    if (!db) await init();
    db = useDatabaseStore.getState().db;
    return db!.select<T>(query, params);
  },
  write: async (query: string, params?: any[]) => {
    let { db, init } = useDatabaseStore.getState();
    if (!db) await init();
    db = useDatabaseStore.getState().db;
    await db!.execute(query, params);
  },
}));

export default useDatabaseStore;
