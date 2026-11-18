# :app:runtime

The engine that makes the SDK's promises true: plugin registry and instance lifecycle, config store, auth registry
and secret store, scheduler, storage, LAN API server, mDNS, relay client and platform services (display, kiosk,
updates). Everything is assembled by hand in `AppGraph` (no dependency-injection framework), so missing pieces
are compile errors.

## Auth (`runtime/auth`)

- `AuthRegistry`: providers, connected accounts (`<data>/accounts.json`), credential refresh (one at a time per
  account) and the central "reconnect" alert.
- `SignedInClient` / `PluginAuthAccess`: what plugins get as `context.auth`. Adds credentials, refreshes, retries
  once on 401, refuses hosts outside `baseUrls` and providers missing from the manifest.
- `DeviceSignIn`, `AuthCodeSignIns`: the OAuth sign-in flows. `AuthCodeSignIns` will be driven by the companion
  app / LAN API (phase 5).
- `AuthProviderLoader`: built-in `*.auth.json` files (`composeResources/files/auth-providers`, listed in
  `BuiltInAuthProviders`) plus the user's `<config>/auth-providers/`.
- `secrets/`: `EncryptedFileSecretStore` (one AES-GCM file in `AppPaths.secretsDir`) with a platform
  `SecretCipher`: `AndroidKeystoreCipher` or `DesktopSecretCipher` (OS keychain, or an owner-only key file).

The definition format is documented in `docs/plugins/authoring.md`.
