use std::sync::{Arc, Mutex};
use std::thread;
use tauri::{Emitter, State, Window};

pub struct HttpServerState {
    pub handle: Arc<Mutex<Option<thread::JoinHandle<()>>>>,
    pub running: Arc<Mutex<bool>>,
}

#[tauri::command]
pub fn start_http_server(
    port: u16,
    window: Window,
    state: State<HttpServerState>,
) -> Result<(), String> {
    let mut running = state.running.lock().unwrap();

    if *running {
        log::info!("Server already running");
        return Ok(());
    }

    *running = true;
    let running_clone = state.running.clone();

    let handle = thread::spawn(move || {
        let addr = format!("0.0.0.0:{}", port);
        let server = match tiny_http::Server::http(&addr) {
            Ok(s) => {
                log::info!("Server started on {}", addr);
                s
            }
            Err(e) => {
                log::error!("Failed to start server: {}", e);
                *running_clone.lock().unwrap() = false;
                return;
            }
        };

        for mut request in server.incoming_requests() {
            // Check if we should stop
            if !*running_clone.lock().unwrap() {
                break;
            }

            let method = request.method().to_string();
            let url = request.url().to_string();
            let headers: Vec<(String, String)> = request
                .headers()
                .iter()
                .map(|h| (h.field.to_string(), h.value.to_string()))
                .collect();

            let mut body = String::new();
            {
                let reader = request.as_reader();
                let _ = reader.read_to_string(&mut body);
            }

            let payload = serde_json::json!({
                "method": method,
                "path": url,
                "headers": headers,
                "body": body
            });

            let _ = window.emit("http-request", payload);

            let response = tiny_http::Response::from_string("{}").with_header(
                tiny_http::Header::from_bytes(&b"Content-Type"[..], &b"application/json"[..])
                    .unwrap(),
            );
            let _ = request.respond(response);
        }
    });

    *state.handle.lock().unwrap() = Some(handle);

    Ok(())
}

#[tauri::command]
pub fn stop_http_server(state: State<HttpServerState>) -> Result<(), String> {
    let mut running = state.running.lock().unwrap();

    if !*running {
        log::warn!("Server not running");
        return Ok(());
    }

    *running = false;
    if let Some(handle) = state.handle.lock().unwrap().take() {
        let _ = handle.join();
        log::info!("Server stopped");
    } else {
        log::warn!("No server thread to join");
    }

    Ok(())
}
