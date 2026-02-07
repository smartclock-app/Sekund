mod http_server;
mod mdns;

use mdns::{start_mdns, MdnsState};
use std::sync::Arc;
use std::sync::Mutex;

use http_server::{start_http_server, stop_http_server, HttpServerState};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let mut builder = tauri::Builder::default();

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
        })
        .invoke_handler(tauri::generate_handler![
            start_mdns,
            start_http_server,
            stop_http_server
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
