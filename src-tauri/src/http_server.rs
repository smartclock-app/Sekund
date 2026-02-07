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
) -> Result<String, String> {
    let mut running = state.running.lock().unwrap();

    if *running {
        return Err("Server already running".to_string());
    }

    *running = true;
    let running_clone = state.running.clone();

    let handle = thread::spawn(move || {
        let addr = format!("0.0.0.0:{}", port);
        let server = match tiny_http::Server::http(&addr) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("Failed to start server: {}", e);
                *running_clone.lock().unwrap() = false;
                return;
            }
        };

        println!("HTTP server listening on http://{}", addr);

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

            let response = tiny_http::Response::from_string("OK");
            let _ = request.respond(response);
        }
    });

    *state.handle.lock().unwrap() = Some(handle);

    Ok(format!("Server started on port {}", port))
}

#[tauri::command]
pub fn stop_http_server(state: State<HttpServerState>) -> Result<String, String> {
    let mut running = state.running.lock().unwrap();

    if !*running {
        return Err("Server not running".to_string());
    }

    *running = false;

    Ok("Server stopped".to_string())
}
