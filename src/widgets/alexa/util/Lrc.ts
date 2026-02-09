class LrcLine {
  get timestampInSeconds() {
    return parseInt(this.timestamp.split(":")[0]) * 60 + parseFloat(this.timestamp.split(":")[1]);
  }

  constructor(
    public timestamp: string,
    public lyrics: string,
  ) {}
}

class Lrc {
  constructor(public lines: LrcLine[]) {}

  static isValid(lyrics?: string) {
    if (!lyrics) return false;

    return RegExp(/\[\d+:\d+\.\d+\].*/, "m").test(lyrics);
  }

  static parse(lyrics: string) {
    const lines = lyrics.split("\n");
    const lrcLines: LrcLine[] = [];
    for (const line of lines) {
      const match = /\[(\d+:\d+\.\d+)\](.*)/.exec(line);
      if (match == null) continue;

      const timestamp = match[1];
      const lyrics = match[2].trim();
      if (lyrics.length === 0) continue;

      lrcLines.push(new LrcLine(timestamp, lyrics));
    }
    return new Lrc(lrcLines);
  }
}

export default Lrc;
