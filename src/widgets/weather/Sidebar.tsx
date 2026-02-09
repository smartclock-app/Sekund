import { Config } from ".";

const WeatherSidebar = ({ config }: { config: Config }) => {
  return (
    <>
      <div>Sidebar</div>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};

export default WeatherSidebar;
