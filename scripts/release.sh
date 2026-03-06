#!/usr/bin/env bash
# Quick release script - Create a version tag and trigger the automated release workflow
# Usage: ./scripts/release.sh v0.5.0

set -e

if ! command -v jq >/dev/null 2>&1; then
    echo "❌ 'jq' is required to update package.json. Please install jq and retry."
    exit 1
fi

if [ -z "$1" ]; then
    echo "❌ Usage: ./scripts/release.sh v0.5.0"
    exit 1
fi

TAG="$1"
VERSION="${TAG#v}"

# Validate tag format
if ! [[ $TAG =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "❌ Invalid format. Use semantic versioning: v0.5.0"
    exit 1
fi

echo "📝 Preparing release: $TAG (version: $VERSION)"

# Check clean working directory
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "❌ Working directory has uncommitted changes"
    exit 1
fi

# Check on main branch
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "main" ]; then
    echo "❌ Must be on 'main' branch (currently on: $BRANCH)"
    exit 1
fi

# Sync with remote
echo "🔄 Fetching latest..."
git fetch origin

# Check if local is ahead
if ! git diff --quiet origin/main..HEAD; then
    echo "❌ Local main is ahead of remote"
    exit 1
fi

# Check if tag exists
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null 2>&1; then
    echo "❌ Tag $TAG already exists"
    exit 1
fi

echo "📦 Updating version numbers..."

# Update package.json
jq ".version = \"$VERSION\"" package.json > package.json.tmp && mv package.json.tmp package.json

# Update Cargo.toml
awk -v ver="$VERSION" '/^version = ".*"/ && !found {sub(/^version = ".*"/, "version = \"" ver "\""); found=1} 1' src-tauri/Cargo.toml > tmp && mv tmp src-tauri/Cargo.toml

# Refresh Cargo.lock
cargo fetch --manifest-path src-tauri/Cargo.toml

# Commit version bump
echo "💾 Committing version bump..."
git add package.json src-tauri/Cargo.toml src-tauri/Cargo.lock
git commit -m "chore: bump version to $VERSION"
git push origin main

# Create and push tag
echo "🏷️  Creating tag..."
git tag -a "$TAG" -m "Release $TAG"
git push origin "$TAG"

echo ""
echo "✅ Release initiated!"
echo ""
echo "📋 What's happening next:"
echo "   1️⃣  Build Linux AppImage"
echo "   2️⃣  Build Android APK"
echo "   3️⃣  Create GitHub Release with artifacts"
echo ""
echo "🔗 Monitor at: https://github.com/0x5045414b/Sekund/actions"