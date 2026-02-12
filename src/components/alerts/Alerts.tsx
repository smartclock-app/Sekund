import useAlertsStore from "@/hooks/useAlertsStore";
import Card from "../Card";

const Alerts = () => {
  const alerts = useAlertsStore(state => state.alerts);

  return (
    <>
      {Object.entries(alerts).map(([key, alert]) => (
        <Card key={key}>
          <div className="alert">
            <h1>{key}</h1>
            {alert}
          </div>
        </Card>
      ))}
    </>
  );
};

export default Alerts;
