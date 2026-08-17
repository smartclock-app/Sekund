# Development Setup

## Prerequisites

| Tool                                                                      | Purpose                                      | Install                                     |
| ------------------------------------------------------------------------- | -------------------------------------------- | ------------------------------------------- |
| [Bun](https://bun.sh/)                                                    | JavaScript runtime & package manager         | `curl -fsSL https://bun.sh/install \| bash` |
| [Rust + Cargo](https://rustup.rs/)                                        | Tauri backend                                | `rustup`                                    |
| [Tauri v2 system dependencies](https://v2.tauri.app/start/prerequisites/) | Platform-specific libs (WebView2, GTK, etc.) | See Tauri docs                              |

## Clone & install

```bash
git clone https://github.com/dnpkuk/ClockBeta.git
cd ClockBeta
bun install
```

## Development server

```bash
bun tauri dev
```

This starts Vite (frontend, port `1420`) and the Tauri desktop shell. Hot-module replacement is enabled for React/TypeScript code.

## Production build

```bash
bun tauri build
```

Bundles the app and produces platform-native installers under `src-tauri/target/release/bundle/`.

## Android build

```bash
bun run build:android
```

Produces APKs targeting `aarch64` and `armv7` under the standard Android build output directory.

> **Note:** The Tauri updater plugin is excluded from Android builds. See the conditional dependency in `src-tauri/Cargo.toml`.

## Key project files

| Path                        | Description                                                 |
| --------------------------- | ----------------------------------------------------------- |
| `src/`                      | React + TypeScript frontend                                 |
| `src-tauri/`                | Rust Tauri backend                                          |
| `src-tauri/tauri.conf.json` | Tauri configuration (app name, identifier, bundle settings) |
| `src-tauri/Cargo.toml`      | Rust dependencies                                           |
| `package.json`              | JS dependencies and scripts                                 |
| `vite.config.ts`            | Vite bundler configuration                                  |
| `tsconfig.json`             | TypeScript compiler options                                 |
| `index.html`                | HTML entry point                                            |
