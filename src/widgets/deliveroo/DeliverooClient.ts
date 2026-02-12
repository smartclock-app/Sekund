import { fetch } from "@tauri-apps/plugin-http";
import { error } from "@tauri-apps/plugin-log";
import { Order, OrderStatus } from "./types";

class DeliverooClient {
  constructor(private token: string) {}

  async getLatestOrder() {
    const url = "https://co-m.uk.deliveroo.com/consumer/order-history/v1/orders?limit=1";
    const options = {
      method: "GET",
      headers: {
        host: "co-m.uk.deliveroo.com",
        "user-agent": "Deliveroo-OrderApp/3.312.0 (iPhone17,3; iOS26.2.1; Release; en_GB; 391277)",
        priority: "u=3",
        authorization: `Bearer ${this.token}`,
        "accept-language": "en-GB,en;q=0.9",
        accept: "*/*",
        "accept-encoding": "deflate, gzip",
      },
    };

    try {
      const response = await fetch(url, options);
      const data = (await response.json()) as Order[];
      return data[0];
    } catch (e) {
      error(`Failed to fetch latest order: ${e}`);
      return null;
    }
  }

  async getOrderStatus(orderId: string) {
    const url = `https://co-m.uk.deliveroo.com/consumer/v2-6/consumer_order_statuses/${orderId}?tz=Europe%2FLondon`;
    const options = {
      method: "GET",
      headers: {
        host: "co-m.uk.deliveroo.com",
        "user-agent": "Deliveroo-OrderApp/3.312.0 (iPhone17,3; iOS26.2.1; Release; en_GB; 391277)",
        priority: "u=3",
        authorization: `Bearer ${this.token}`,
        "accept-language": "en-GB,en;q=0.9",
        accept: "*/*",
        "accept-encoding": "deflate, gzip",
      },
    };

    try {
      const response = await fetch(url, options);
      const data = (await response.json()) as OrderStatus;
      return data;
    } catch (e) {
      error(`Failed to fetch order status for order ${orderId}: ${e}`);
      return null;
    }
  }
}

export default DeliverooClient;
