export type NetworkInterfaces = Record<
  string,
  {
    ipv4: string | null;
    ipv6: string | null;
  }
>;

const hashInterfaces = (ifaces: NetworkInterfaces) => {
  const sortedKeys = Object.keys(ifaces).sort((a, b) => a.localeCompare(b));

  let hash = 0;
  for (const key of sortedKeys) {
    for (const char of `${key}|${ifaces[key].ipv4}|${ifaces[key].ipv6}`) {
      hash = (hash << 5) - hash + char.charCodeAt(0);
      hash |= 0; // Constrain to 32bit integer
    }
  }
  return String(hash);
};

export default hashInterfaces;
