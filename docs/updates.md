# App updates

Sekund updates itself from a **signed release manifest**. No token or secret is built into the app: the app
only holds a *public* key, and believes a manifest only if it was signed with the matching private key.

## How a device updates

1. Sekund checks by itself, 30 seconds after starting and then every `updates.checkEvery` (default `6h`,
   at least `5m`). Updating is part of the app, not a widget, so it happens whatever the layout contains.
2. The runtime downloads `update.json` and `update.json.sig` from the `manifestUrl` built into the app, and
   checks the signature against the built-in `publicKey`. A bad signature means no update.
3. If the manifest's `version` is newer than the running app and lists an asset for this device, the update
   is offered.
4. The download goes to `<cache>/updates/`, and its SHA-256 must match the manifest's, or it's deleted.
5. The verified file goes to the platform's installer:
   - **Desktop**: the file is opened with the system, which shows its installer (`.deb`, `.dmg`, `.msi`).
   - **Android**: not yet; PackageInstaller (silent as device owner) comes with the Android platform phase.
     Until then the card says installing isn't supported, and the download is kept.

A new version appears as a card at the top of the sidebar with an **Install** button. It's installed when
someone taps it, when the companion runs the `update.install` action, or straight away if
`updates.installAutomatically` is on (General settings: *Install updates by themselves*).

```json
"updates": { "installAutomatically": false, "checkEvery": "6h" }
```

Development builds (version `dev`) never update.

## Built-in settings

`app/runtime/src/commonMain/composeResources/files/updates/update-source.json`:

```json
{
  "manifestUrl": "https://github.com/<owner>/<repo>/releases/latest/download/update.json",
  "publicKey": "<base64 DER public key>"
}
```

Both are empty for now, which turns updates off ("This build has no update source"). The release
downloads must be public: the app never sends credentials for them.

## Making the key (once)

```sh
openssl ecparam -name prime256v1 -genkey -noout -out sekund-update.key      # PRIVATE: keep it out of git
openssl ec -in sekund-update.key -pubout -outform DER | base64               # -> "publicKey" above
```

Store the private key as a CI secret (phase 10), never in the repository.

## Publishing a release

`update.json` lists one asset per platform (`android`, `linux-arm64`, `linux-x64`, `macos-arm64`,
`macos-x64`, `windows-x64`):

```json
{
  "version": "2.1.0",
  "notes": "Home Assistant cameras",
  "assets": {
    "android": { "url": "https://github.com/<owner>/<repo>/releases/download/v2.1.0/sekund-2.1.0.apk",
                 "sha256": "<shasum -a 256 sekund-2.1.0.apk>", "size": 41234567 }
  }
}
```

Sign it and upload both files to the release:

```sh
openssl dgst -sha256 -sign sekund-update.key update.json | base64 > update.json.sig
```

Sign the exact bytes you upload: reformatting `update.json` afterwards breaks the signature.
