#!/usr/bin/env bash
# Quick release script for Sekund
# Usage: ./scripts/release.sh v0.5.0

set -e

if [ -z "$1" ]; then
    echo "Usage: ./scripts/release.sh v0.5.0"
    exit 1
fi

TAG="$1"
VERSION="${TAG#v}"

echo "Preparing release: $TAG"

# Validate tag format
if ! [[ $TAG =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Invalid tag format. Use: v0.5.0"
    exit 1
fi

# Ensure clean git state
if ! git diff --quiet; then
    echo "Working directory has uncommitted changes"
    exit 1
fi

# Ensure on main branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "Must be on 'main' branch. Currently on: $CURRENT_BRANCH"
    exit 1
fi

# Fetch latest changes
echo "Fetching latest changes..."
git fetch origin

# Ensure up to date
if ! git diff --quiet remotes/origin/main..HEAD; then
    echo "Local main is not up to date with remote"
    exit 1
fi

# Check if tag already exists
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null 2>&1; then
    echo "Tag $TAG already exists"
    exit 1
fi

echo "Creating tag: $TAG"
git tag -a "$TAG" -m "Release $TAG"

echo "Pushing tag to remote..."
git push origin "$TAG"

echo ""
echo "Release initiated! Monitor the GitHub Actions workflow:"
echo "   https://github.com/0x5045414b/Sekund/actions"