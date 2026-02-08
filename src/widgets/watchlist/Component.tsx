import { CalendarExtensionComponent } from "@/helpers/types";
import { Config } from ".";

const Component: CalendarExtensionComponent<Config> = config => {
  console.log(config);

  return [];
};

export default Component;
