# :app:runtime

The engine that makes the SDK's promises true: plugin registry and instance lifecycle, config store, auth registry
and secret store, scheduler, storage, LAN API server, mDNS, relay client and platform services (display, kiosk,
updates). Everything is assembled by hand in `AppGraph` (no dependency-injection framework), so missing pieces
are compile errors.
