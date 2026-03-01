# GitHub Flow - Release Workflow

A simple, maintainable release workflow for Sekund following best practices for small projects.

## Overview

- **Development**: Work on feature branches
- **Testing**: Automated tests on every PR and main push
- **Release**: Manual tag creation triggers automated builds and release
- **Version**: Single source of truth in `package.json`, auto-synced to other files

## The Flow

```
Feature Branch
     ↓
Pull Request → Tests ✅ + Build Check ✅ (validates compilation)
     ↓ (after approval & checks pass)
Merge to Main → Tests ✅
     ↓ (when ready to release)
./scripts/release.sh v0.5.0 → Version bump + commit
     ↓
Tag Push → Version Sync ✅ → Build ✅ → Create Release ✅
```

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/my-feature
```

### 2. Make Changes & Commit

Use conventional commits:

```bash
git commit -m "feat: add widget support"
git commit -m "fix: resolve layout issue"
git commit -m "docs: update README"
```

### 3. Push & Open Pull Request

```bash
git push origin feature/my-feature
```

The CI automatically runs tests. After approval and tests pass, merge to main.

### 4. Merge to Main

After your PR is approved and **both tests and build checks pass**, merge it to main. Tests run once more to ensure main is always stable.

**Note**: Only tag creation triggers the full release workflow with artifact generation.

## Creating a Release

### Option A: Use the Release Script (Recommended)

```bash
./scripts/release.sh v0.5.0
```

This script:

1. ✅ Validates the version format
2. 📦 Updates `package.json`
3. 💾 Commits and pushes to main
4. 🏷️ Creates and pushes the tag
5. 🚀 Triggers the automated release workflow

### Option B: Manual Steps

```bash
# 1. Update version in package.json
# Edit package.json, change "version": "0.4.3" to "version": "0.5.0"

git add package.json
git commit -m "chore: bump version to 0.5.0"
git push origin main

# 2. Create and push tag
git tag -a v0.5.0 -m "Release v0.5.0"
git push origin v0.5.0
```

### What Happens After Tag Push

When you push a tag, the release workflow:

1. **Version Sync** - Updates Cargo.toml with the version from tag
2. **Build Artifacts** - Builds Linux AppImage and Android APK (final, signed builds)
3. **Create Release** - Uploads artifacts to GitHub Releases with `latest.json`

## GitHub Actions Workflows

### `ci.yml`

**Triggers:**

- Pull requests to `main`

**Does:**

- Installs dependencies
- Runs tests with Bun
- Builds Linux AppImage (validation only, no artifacts)
- Builds Android APK (validation only, no artifacts)

**Purpose:** Ensure compilation succeeds before allowing PR merge

### `release.yml`

**Triggers:**

- Tag push matching `v*.*.*` (e.g., `v0.5.0`, `v1.0.0`)

**Does:**

1. Extracts version from tag
2. Syncs version to Cargo.toml
3. Builds Linux AppImage (signed, for release)
4. Builds Android APK (signed, for release)
5. Creates GitHub Release with artifacts and `latest.json`

## Version Numbers

Use Semantic Versioning:

- `0.4.3` → `0.4.4` - Bug fix (patch)
- `0.4.3` → `0.5.0` - New features (minor)
- `0.4.3` → `1.0.0` - Breaking changes (major)

## Single Source of Truth

`package.json` is the version source:

- When you tag `v0.5.0`, the release workflow reads one version value
- It automatically syncs to `src-tauri/Cargo.toml`

This eliminates manual version management across files.

## Troubleshooting

### Script says "already exists"

Tag or version already exists. Check what's on remote:

```bash
git tag -l v0.5.0
git log --oneline -n 10
```

### Tag already exists but want to recreate

Delete and recreate:

```bash
git tag -d v0.5.0
git push origin :v0.5.0
bun release v0.5.0
```

### Release workflow failed

Check GitHub Actions logs for which step failed:

- **Sync failed?** → Check Cargo.toml format
- **Build failed?** → Check Docker availability or secrets
- **Release failed?** → Check GitHub token permissions

## Related Files

- [.github/workflows/ci.yml](.github/workflows/ci.yml) - Testing workflow
- [.github/workflows/release.yml](.github/workflows/release.yml) - Build and release workflow
- [scripts/release.sh](../scripts/release.sh) - Release helper script
- [package.json](../package.json) - Version source of truth
