import { WidgetComponent } from "@/helpers/types";
import { Config } from ".";

const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <>
      <h1>Updater Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};

export default Component;
