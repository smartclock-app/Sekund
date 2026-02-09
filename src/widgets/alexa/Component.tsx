import { WidgetComponent } from "@/helpers/types";
import { Config } from ".";
import NowPlaying from "./NowPlaying";

const Component: WidgetComponent<Config> = ({ config }) => {
  return (
    <>
      <NowPlaying config={config} />
    </>
  );
};

export default Component;
