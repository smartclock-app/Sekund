import { WidgetComponent } from "@/helpers/types";
import { Config } from ".";
import Notifications from "./notifications/Notifications";
import NowPlaying from "./nowplaying/NowPlaying";

const Component: WidgetComponent<Config> = ({ config }) => {
  return (
    <>
      {config.features.nowplaying && <NowPlaying config={config} />}
      <Notifications config={config} />
    </>
  );
};

export default Component;
