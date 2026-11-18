# Releasing

Releases are built by GitHub Actions from a version tag. What gets released:

| File | For |
|---|---|
| `sekund-<version>.apk` | Android, signed with the release key |
| `sekund_<version>_arm64.deb` | Raspberry Pi and other arm64 Linux |
| `sekund_<version>_amd64.deb` | x64 Linux |
| `update.json` + `update.json.sig` | The signed manifest devices update from ([updates.md](updates.md)) |
| `ghcr.io/<owner>/sekund-server:<version>` and `:latest` | The server's Docker image (amd64 and arm64) |

macOS and Windows are for development and aren't released; build them locally if you need a package
([desktop.md](desktop.md#building-the-packages)).

## Making a release

```sh
scripts/release.sh 1.2.0 "Home Assistant cameras"   # sets sekund.version, commits "Release 1.2.0", tags v1.2.0
git push origin main v1.2.0
```

The notes are optional. They become the tag's message, and from there the text of the GitHub release and of
the update card on devices. The script refuses to go backwards or to reuse a tag, and only runs on a clean `main`.

Pushing the tag starts [`release.yml`](../.github/workflows/release.yml):

1. **check**: the tag must match `sekund.version`, every secret must be set, and `./gradlew check` must pass.
2. **android**, **linux** (amd64 and arm64 runners) and **server-image** build in parallel. The app builds
   get the update source written into them (see below); without it they'd never look for updates.
3. **publish** signs `update.json` for the built files and creates the GitHub release. It goes last because
   devices find updates through "the latest release", so it must only appear once it's complete.

If a job fails, fix the problem, delete the tag (`git push --delete origin v1.2.0 && git tag -d v1.2.0`), and
tag again. A new patch version is simpler once a release has been published.

Every push to `main` and every pull request runs the tests ([`ci.yml`](../.github/workflows/ci.yml)).

## One-time set-up

### 1. The repository

The release workflow uses the `ubuntu-24.04-arm` runner for the Raspberry Pi package. GitHub offers it free to
**public** repositories; a private repository needs a paid plan for it. The release files must be public anyway,
because devices download them without credentials.

### 2. The update signing key

```sh
openssl ecparam -name prime256v1 -genkey -noout -out sekund-update.key
gh secret set UPDATE_SIGNING_KEY < sekund-update.key
```

Keep `sekund-update.key` somewhere safe and **out of git**. Its public half is built into every release, so
devices only accept manifests signed with this key: lose it and existing devices can't be updated any more
(they'd need reinstalling with a build that has the new key).

### 3. The Android release keystore

```sh
keytool -genkeypair -v -keystore sekund-release.jks -alias sekund \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Sekund"
base64 -i sekund-release.jks | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD     # paste the store password
gh secret set ANDROID_KEY_ALIAS --body sekund
gh secret set ANDROID_KEY_PASSWORD          # paste the key password (often the same)
```

Same rule: keep the `.jks` safe. Android only installs an update signed with the same key as the installed app,
so a new key means uninstalling Sekund (and, for a device owner, setting it up again). An app installed from a
debug build has the debug key, so replace it with a release APK once.

To build a signed APK locally, set the same variables the workflow does:

```sh
SEKUND_KEYSTORE_FILE=$PWD/sekund-release.jks SEKUND_KEYSTORE_PASSWORD=... SEKUND_KEY_ALIAS=sekund \
SEKUND_KEY_PASSWORD=... ./gradlew :app:androidApp:assembleRelease
```

Without them `assembleRelease` makes an unsigned APK.

### 4. The server image

Nothing to set up: the workflow pushes to the GitHub Container Registry with its own token. The first push
creates the package as private; to let anyone pull it, open the package on GitHub (your profile → Packages →
`sekund-server` → Package settings) and make it public.

## Versions

`sekund.version` in `gradle.properties` is the only version. Android's `versionName` is the same text, and its
`versionCode` is computed from it (`1.2.3` → `1002003`), so minor and patch stay below 1000. Updates are only
offered when the release's version is higher than the installed one.

## The scripts

| Script | Does |
|---|---|
| `scripts/release.sh` | Bumps the version, commits and tags. |
| `scripts/write-update-source.sh` | Writes the update source (manifest URL + public key) into `:app:runtime`'s resources. CI only; the committed file stays empty so local builds never update. |
| `scripts/make-update-manifest.sh` | Writes and signs `update.json` for a folder of release files. |
