import { WidgetComponent } from "@/helpers/types";
import { Config } from ".";

const ActualBudget: WidgetComponent<Config> = ({ config }) => {
  return (
    <div>
      <h2>Actual Budget</h2>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </div>
  );
};

export default ActualBudget;
