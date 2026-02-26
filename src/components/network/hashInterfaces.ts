type NetworkInterface = {
  name: string;
  v4_addrs: {
    ip: string;
    ip_octets: number[];
    broadcast: string | null;
    broadcast_octets: number[] | null;
    netmask: string | null;
    netmask_octets: number[] | null;
    prefix: number | null;
    network: string | null;
  }[];
  v6_addrs: {
    ip: string;
    ip_octets: number[];
    broadcast: string | null;
    broadcast_octets: number[] | null;
    netmask: string | null;
    netmask_octets: number[] | null;
    prefix: number | null;
    network: string | null;
  }[];
  mac_addr: string | null;
  index: number;
};

const hashInterfaces = (ifaces: NetworkInterface[]) => {
  ifaces.sort((a, b) => a.name.localeCompare(b.name));

  let hash = 0;
  for (const char of ifaces.join("|")) {
    hash = (hash << 5) - hash + char.charCodeAt(0);
    hash |= 0; // Constrain to 32bit integer
  }
  return String(hash);
};

export default hashInterfaces;
