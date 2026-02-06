export enum Location {
  Main = "main",
  Sidebar = "sidebar",
  Calendar = "calendar",
  Floating = "floating",
}

export type WidgetComponent<Config> = React.FC<{ config: Config; location: Location }>;
