import Card from "@/components/Card";
import { WidgetComponent } from "@/helpers/types";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import dayjs, { type Dayjs } from "dayjs";
import { useEffect, useRef, useState } from "react";
import { Config } from ".";
import DeliverooClient from "./DeliverooClient";
import { OrderStatus } from "./types";

const Deliveroo: WidgetComponent<Config> = ({ config }) => {
  const [client] = useState(() => new DeliverooClient(config.token));
  const [orderStatus, setOrderStatus] = useState<OrderStatus | null>(null);
  const lastUpdate = useRef(dayjs());

  const fetchOrderData = async () => {
    const latestOrder = await client.getLatestOrder();
    if (!latestOrder || latestOrder.status !== "delivered") return setOrderStatus(null);

    const status = await client.getOrderStatus(latestOrder.id);
    setOrderStatus(status);
  };

  useEffect(() => {
    lastUpdate.current = dayjs();
    fetchOrderData();
  }, [config.token]);

  useEventListener(EventType.Tick, event => {
    if (orderStatus && (event.detail as Dayjs).diff(lastUpdate.current, "second") >= 60) {
      lastUpdate.current = dayjs();
      fetchOrderData();
    } else if (!orderStatus && ((event.detail as Dayjs).minute() + 1) % config.refreshInterval === 0) {
      lastUpdate.current = dayjs();
      fetchOrderData();
    }
  });

  if (!orderStatus) return null;

  return (
    <Card>
      <h1>Deliveroo</h1>
      <h2>{orderStatus.data.attributes.title}</h2>
      <p>{orderStatus.data.attributes.message}</p>
    </Card>
  );
};

export default Deliveroo;
