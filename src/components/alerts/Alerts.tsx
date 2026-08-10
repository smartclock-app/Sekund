import useAlertsStore from "@/hooks/useAlertsStore";
import Card from "../Card";
import styles from "./alerts.module.scss";

const Alerts = () => {
  const alerts = useAlertsStore(state => state.alerts);

  return (
    <>
      {Object.entries(alerts).map(([key, alert]) => (
        <Card key={key} padding={false}>
          <div className={styles.alert}>
            {alert.image && <img src={alert.image} />}
            <div className={styles.info}>
              <p className={styles.title}>{alert.title}</p>
              {alert.subtitle && <p className={styles.subtitle}>{alert.subtitle}</p>}
            </div>
          </div>
        </Card>
      ))}
    </>
  );
};

export default Alerts;
