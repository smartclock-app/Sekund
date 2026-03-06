import { Config } from ".";

const Desktop = ({ config }: { config: Config }) => {
  return <div>{JSON.stringify(config, null, 2)}</div>;
};

export default Desktop;
