#!/usr/bin/env bash
# Starts a release: sets the version in gradle.properties, commits it and tags it.
# Pushing the tag makes GitHub Actions build and publish the release (.github/workflows/release.yml).
#
#   scripts/release.sh 1.2.0
#   git push origin main v1.2.0
#
# See docs/releasing.md.
set -euo pipefail

version="${1:-}"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: scripts/release.sh <major.minor.patch>, e.g. scripts/release.sh 1.2.0" >&2
  exit 1
fi

cd "$(dirname "$0")/.."

# Release only from a clean main, so the tag points at exactly what's in the repository.
if [[ "$(git branch --show-current)" != "main" ]]; then
  echo "Releases are made from main." >&2
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Commit or stash your changes first." >&2
  exit 1
fi
if git rev-parse -q --verify "refs/tags/v$version" >/dev/null; then
  echo "The tag v$version already exists." >&2
  exit 1
fi

current="$(sed -n 's/^sekund\.version=//p' gradle.properties)"
if [[ "$current" == "$version" ]]; then
  echo "gradle.properties already says $version; tagging without a version commit."
else
  # The update check only offers a version newer than the installed one, so never go backwards.
  newest="$(printf '%s\n%s\n' "$current" "$version" | sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1)"
  if [[ "$newest" != "$version" ]]; then
    echo "$version is older than the current $current." >&2
    exit 1
  fi
  # Rewrite the one line; -i.bak works with both GNU and macOS sed.
  sed -i.bak "s/^sekund\.version=.*/sekund.version=$version/" gradle.properties
  rm gradle.properties.bak
  git add gradle.properties
  git commit -q -m "Release $version"
fi

git tag -a "v$version" -m "Sekund $version"
echo "Tagged v$version. Publish it with:"
echo "  git push origin main v$version"
