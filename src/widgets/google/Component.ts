import { CalendarExtensionComponent } from "@/helpers/types";
import { Config } from ".";

const Component: CalendarExtensionComponent<Config> = config => {
  console.log("Calendar Extension Config:", config);
  return [];
};

export default Component;
