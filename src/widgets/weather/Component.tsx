import { WidgetComponent } from "@/helpers/types";
import { Config } from ".";

const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <div style={{ position: "absolute", top: 0, left: 0, width: "100%" }}>
      <h1>Weather Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </div>
  );
};

export default Component;
