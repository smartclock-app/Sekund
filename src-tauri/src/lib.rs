mod mdns;

use mdns::{start_mdns, MdnsState};
use std::sync::Mutex;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_websocket::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_http::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_opener::init())
        .manage(Mutex::new(MdnsState::new()))
        .invoke_handler(tauri::generate_handler![start_mdns])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
