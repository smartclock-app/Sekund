import { Config } from "..";
import Lrc from "../util/Lrc";

import styles from "./nowplaying.module.scss";

interface NowPlayingLyricsProps {
  progress: number;
  lyrics: Lrc;
  config: Config;
}

type LrcLine = Lrc["lines"][number];

const NowPlayingLyrics: React.FC<NowPlayingLyricsProps> = ({ progress, lyrics }) => {
  let currentLine, previousLine, nextLine: LrcLine | undefined;
  for (const line of lyrics.lines) {
    if (line.timestampInSeconds <= progress / 1000) {
      currentLine = line;
    } else if (line.timestampInSeconds > progress / 1000) {
      break;
    }
  }

  const currentIndex = lyrics.lines.indexOf(currentLine ?? lyrics.lines[0]);
  if (currentIndex > 0) previousLine = lyrics.lines[currentIndex - 1];
  if (currentIndex < lyrics.lines.length - 1) nextLine = lyrics.lines[currentIndex + 1];

  return (
    <div className={styles.lyricsContainer}>
      <p className={styles.previousLine}>{previousLine?.lyrics}</p>
      <p className={styles.currentLine}>{currentLine?.lyrics ?? lyrics.lines[0].lyrics}</p>
      <p className={styles.nextLine}>{nextLine?.lyrics}</p>
    </div>
  );
};

export default NowPlayingLyrics;
