# :core:protocol

Plain `@Serializable` data classes for everything that crosses a network boundary:

- the device API (`/v1/...`) used by the companion app,
- relay frames between devices and the optional server,
- settings descriptors (how a settings screen is described as data),
- auth provider definitions (`*.auth.json`).

Shared by the app **and** the server. Keep it free of UI and platform code.
