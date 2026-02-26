mod android_updates;
mod http_server;
mod mdns;
mod migrations;

use sentry;
use std::collections::HashMap;
use std::sync::atomic::AtomicU64;
use std::sync::Arc;
use std::sync::Mutex;

use android_updates::{download_apk, install_apk};
use http_server::{http_respond, start_http_server, stop_http_server, HttpServerState};
use mdns::{start_mdns, MdnsState};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let _guard = sentry::init((
        "https://0b0251fbf24f4418b1a6cc689fbb51e2@sentry.danpeak.co.uk/1",
        sentry::ClientOptions {
            release: sentry::release_name!(),
            ..Default::default()
        },
    ));

    tauri::Builder::default()
        .plugin(tauri_plugin_network::init())
        .setup(|_app| {
            #[cfg(desktop)]
            _app.handle()
                .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
                    use tauri::Manager;
                    let _ = app
                        .get_webview_window("main")
                        .expect("no main window")
                        .set_focus();
                }))?;
            Ok(())
        })
        .plugin(tauri_plugin_deep_link::init())
        .plugin(tauri_plugin_os::init())
        .plugin(tauri_plugin_apk_intent::init())
        .setup(|_app| {
            #[cfg(not(target_os = "android"))]
            _app.handle()
                .plugin(tauri_plugin_updater::Builder::new().build())?;
            Ok(())
        })
        .plugin(
            tauri_plugin_sql::Builder::new()
                .add_migrations("sqlite:database.sqlite", migrations::get_migrations())
                .build(),
        )
        .plugin(
            tauri_plugin_log::Builder::new()
                .level(tauri_plugin_log::log::LevelFilter::Info)
                .target(tauri_plugin_log::Target::new(
                    tauri_plugin_log::TargetKind::Webview,
                ))
                .build(),
        )
        .plugin(tauri_plugin_websocket::init())
        .plugin(tauri_plugin_http::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_opener::init())
        .manage(Mutex::new(MdnsState::new()))
        .manage(HttpServerState {
            handle: Arc::new(Mutex::new(None)),
            running: Arc::new(Mutex::new(false)),
            responses: Arc::new(Mutex::new(HashMap::new())),
            id_counter: Arc::new(AtomicU64::new(1)),
        })
        .invoke_handler(tauri::generate_handler![
            start_mdns,
            start_http_server,
            stop_http_server,
            http_respond,
            download_apk,
            install_apk
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
