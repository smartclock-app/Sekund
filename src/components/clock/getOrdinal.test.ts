import { describe, expect, it } from "vitest";
import getOrdinal from "./getOrdinal";

describe("getOrdinal", () => {
  it("returns 'st' for 1, 21, 31", () => {
    expect(getOrdinal(1)).toBe("st");
    expect(getOrdinal(21)).toBe("st");
    expect(getOrdinal(31)).toBe("st");
  });

  it("returns 'nd' for 2 and 22", () => {
    expect(getOrdinal(2)).toBe("nd");
    expect(getOrdinal(22)).toBe("nd");
  });

  it("returns 'rd' for 3 and 23", () => {
    expect(getOrdinal(3)).toBe("rd");
    expect(getOrdinal(23)).toBe("rd");
  });

  it("returns 'th' for all other days", () => {
    const thDays = [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 24, 25, 26, 27, 28, 29, 30];
    for (const day of thDays) {
      expect(getOrdinal(day)).toBe("th");
    }
  });
});
