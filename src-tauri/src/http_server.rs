use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{mpsc, Arc, Mutex};
use std::thread;
use std::time::Duration;
use tauri::{Emitter, State, Window};

pub struct HttpServerState {
    pub handle: Arc<Mutex<Option<thread::JoinHandle<()>>>>,
    pub running: Arc<Mutex<bool>>,
    // Map of pending request id -> sender for the response
    pub responses: Arc<Mutex<HashMap<u64, mpsc::Sender<String>>>>,
    // Counter to generate unique request ids
    pub id_counter: Arc<AtomicU64>,
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
    let responses_clone = state.responses.clone();
    let id_counter_clone = state.id_counter.clone();

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
            // Create a unique id and channel for this request
            let id = id_counter_clone.fetch_add(1, Ordering::SeqCst);
            let (tx, rx) = mpsc::channel::<String>();
            responses_clone.lock().unwrap().insert(id, tx);

            let payload = serde_json::json!({
                "id": id,
                "method": method,
                "path": url,
                "headers": headers,
                "body": body
            });

            let _ = window.emit("http-request", payload);

            // Wait for frontend to send a response via `http_respond` command.
            // Timeout after 5 seconds and return an empty JSON object on timeout.
            let response_string = match rx.recv_timeout(Duration::from_secs(5)) {
                Ok(resp) => resp,
                Err(_) => {
                    // cleanup pending sender if still present
                    let _ = responses_clone.lock().unwrap().remove(&id);
                    "{}".to_string()
                }
            };

            let response = tiny_http::Response::from_string(response_string).with_header(
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

#[tauri::command]
pub fn http_respond(
    id: u64,
    response_body: String,
    state: State<HttpServerState>,
) -> Result<(), String> {
    // Take the sender for this request id (if any) and forward the response
    if let Some(tx) = state.responses.lock().unwrap().remove(&id) {
        let _ = tx.send(response_body);
        Ok(())
    } else {
        Err(format!("No pending request with id {}", id))
    }
}
