mod http_server;
mod mdns;
mod migrations;

use mdns::{start_mdns, MdnsState};
use std::collections::HashMap;
use std::sync::atomic::AtomicU64;
use std::sync::Arc;
use std::sync::Mutex;

use http_server::{http_respond, start_http_server, stop_http_server, HttpServerState};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    #[allow(unused_mut)]
    let mut builder = tauri::Builder::default()
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
        );

    #[cfg(not(any(target_os = "android", target_os = "ios")))]
    {
        builder = builder.plugin(tauri_plugin_updater::Builder::new().build());
    }

    builder
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
            http_respond
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
