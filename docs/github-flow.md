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
Pull Request → Tests Run ✅
     ↓ (after approval)
Merge to Main → Tests Run ✅
     ↓
./scripts/release.sh v0.5.0 → Version bump + commit
     ↓
Tag Push → Version Sync ✅ → Linux Build ✅ → Android Build ✅ → Release Created ✅
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

After your PR is approved, merge it to main. Tests run again to ensure main is always stable.

**Note**: Merging to main does NOT trigger a build. Only creating a tag triggers the full release workflow.

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

The `release.yml` workflow automatically:

1. **Version Sync** - Updates Cargo.toml and tauri.conf.json with the version from tag
2. **Build Linux** - Creates AppImage in Docker environment
3. **Build Android** - Creates APK in Docker environment
4. **Create Release** - Uploads artifacts to GitHub Releases with `latest.json`

All in parallel where possible. Check progress at: https://github.com/0x5045414b/Sekund/actions

## GitHub Actions Workflows

### `ci.yml`

Runs on every PR and main push. Just tests.

**Triggers:**

- Pull requests to `main`
- Pushes to `main`

**Does:**

- Installs dependencies
- Runs tests with Bun

**Duration:** ~2-3 minutes

### `release.yml`

Runs on tag pushes only. Builds and releases the app.

**Triggers:**

- Tag push matching `v*.*.*` (e.g., `v0.5.0`, `v1.0.0`)

**Does:**

1. Extracts version from tag
2. Syncs version to Cargo.toml and tauri.conf.json
3. Builds Linux AppImage (on ARM runner for RPi support)
4. Builds Android APK
5. Creates GitHub Release with all artifacts
6. Generates `latest.json` for auto-updates

**Duration:** ~30-45 minutes (most CI time)

## Version Numbers

Use Semantic Versioning:

- `0.4.3` → `0.4.4` - Bug fix (patch)
- `0.4.3` → `0.5.0` - New features (minor)
- `0.4.3` → `1.0.0` - Breaking changes (major)

## Single Source of Truth

`package.json` is the version source:

- When you tag `v0.5.0`, the release workflow reads one version value
- It automatically syncs to:
  - `src-tauri/Cargo.toml` (Rust side)
  - `src-tauri/tauri.conf.json` (Tauri config)

This eliminates manual version management across three files.

## Branch Protection

Set up on GitHub (Settings → Branches):

1. Add rule for `main` branch
2. Require pull request before merging ✅
3. Require status checks to pass:
   - `Tests` (from ci.yml)
4. Include administrators ✅ (prevents accidental pushes)

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
./scripts/release.sh v0.5.0
```

### Release workflow failed

Check GitHub Actions logs for which step failed:

- **Sync failed?** → Check Cargo.toml/tauri.conf.json format
- **Build failed?** → Check Docker availability or secrets
- **Release failed?** → Check GitHub token permissions

## Related Files

- [.github/workflows/ci.yml](.github/workflows/ci.yml) - Testing workflow
- [.github/workflows/release.yml](.github/workflows/release.yml) - Build and release workflow
- [scripts/release.sh](../scripts/release.sh) - Release helper script
- [package.json](../package.json) - Version source of truth
