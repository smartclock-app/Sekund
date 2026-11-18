#!/usr/bin/env bash
# Writes the update source built into release builds: where to find update.json, and the public key
# its signature must match. The repository's copy is empty, which keeps development builds from updating.
#
#   scripts/write-update-source.sh <owner/repo> <private-key.pem>
#
# The public key is derived from the private signing key, so CI needs only one secret.
# Used by .github/workflows/release.yml; see docs/updates.md.
set -euo pipefail

repository="${1:?usage: write-update-source.sh <owner/repo> <private-key.pem>}"
private_key="${2:?usage: write-update-source.sh <owner/repo> <private-key.pem>}"

cd "$(dirname "$0")/.."
target="app/runtime/src/commonMain/composeResources/files/updates/update-source.json"

public_key="$(openssl ec -in "$private_key" -pubout -outform DER 2>/dev/null | base64 | tr -d '\n')"
if [[ -z "$public_key" ]]; then
  echo "Couldn't read an EC private key from $private_key" >&2
  exit 1
fi

jq -n \
  --arg url "https://github.com/$repository/releases/latest/download/update.json" \
  --arg key "$public_key" \
  '{manifestUrl: $url, publicKey: $key}' > "$target"
echo "Wrote $target"
