#!/usr/bin/env bash
# Makes update.json (and its signature, update.json.sig) for the files in a release folder.
#
#   scripts/make-update-manifest.sh <version> <download-url-base> <folder> <private-key.pem> [notes]
#
# <folder> holds the release files, named as release.yml names them:
#   sekund-<version>.apk           -> "android"
#   sekund_<version>_arm64.deb     -> "linux-arm64"
#   sekund_<version>_amd64.deb     -> "linux-x64"
# A missing file just leaves that platform out. update.json and update.json.sig are written into <folder>.
# See docs/updates.md for the format.
set -euo pipefail

usage="usage: make-update-manifest.sh <version> <download-url-base> <folder> <private-key.pem> [notes]"
version="${1:?$usage}"
base_url="${2:?$usage}"
folder="${3:?$usage}"
private_key="${4:?$usage}"
notes="${5:-}"

# Starts with no assets; add_asset adds one platform at a time.
manifest="$(jq -n --arg version "$version" --arg notes "$notes" \
  '{version: $version, notes: (if $notes == "" then null else $notes end), assets: {}}')"

add_asset() {
  local platform="$1"
  local file_name="$2"
  local path="$folder/$file_name"
  if [[ ! -f "$path" ]]; then
    echo "No $file_name, so no $platform update" >&2
    return
  fi
  local sha256
  sha256="$(openssl dgst -sha256 -r "$path" | cut -d' ' -f1)"
  local size
  size="$(wc -c < "$path" | tr -d ' ')"
  manifest="$(jq --arg platform "$platform" --arg url "$base_url/$file_name" --arg sha "$sha256" --argjson size "$size" \
    '.assets[$platform] = {url: $url, sha256: $sha, size: $size}' <<< "$manifest")"
}

add_asset "android" "sekund-$version.apk"
add_asset "linux-arm64" "sekund_${version}_arm64.deb"
add_asset "linux-x64" "sekund_${version}_amd64.deb"

if [[ "$(jq '.assets | length' <<< "$manifest")" == "0" ]]; then
  echo "No release files found in $folder" >&2
  exit 1
fi

printf '%s\n' "$manifest" > "$folder/update.json"
# The app checks this signature against the exact bytes of update.json, so don't touch the file after this.
openssl dgst -sha256 -sign "$private_key" "$folder/update.json" | base64 | tr -d '\n' > "$folder/update.json.sig"
echo "Wrote $folder/update.json and update.json.sig"
