import { ReadDatabase, WriteDatabase } from "@/hooks/useDatabaseStore";
import Lrc from "./Lrc";

const fetchLyrics = async (db: { read: ReadDatabase; write: WriteDatabase }, title: string, artist: string) => {
  const lyricsFromDb = await db.read<any[]>("SELECT * FROM lyrics WHERE id = ?", [`${title} - ${artist}`]);
  console.log(lyricsFromDb);
  if (lyricsFromDb.length > 0) return Lrc.parse(lyricsFromDb[0]["lyrics"] as string);

  const request = await fetch(`https://lrclib.net/api/search?q=${title}+${artist}`);
  const response = await request.json();
  const lyrics = response[0]?.["syncedLyrics"];
  if (lyrics != null && Lrc.isValid(lyrics)) {
    db.write("INSERT INTO lyrics (id, lyrics) VALUES (?, ?)", [`${title} - ${artist}`, lyrics]);
    return Lrc.parse(lyrics);
  } else {
    return null;
  }
};

export default fetchLyrics;
