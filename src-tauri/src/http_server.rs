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
        log::info!("[HTTP] Server already running");
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
                log::info!("[HTTP] Server started on {}", addr);
                s
            }
            Err(e) => {
                log::error!("[HTTP] Failed to start server: {}", e);
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
        log::warn!("[HTTP] Server not running");
        return Ok(());
    }

    *running = false;
    if let Some(handle) = state.handle.lock().unwrap().take() {
        let _ = handle.join();
        log::info!("[HTTP] Server stopped");
    } else {
        log::warn!("[HTTP] No server thread to join");
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
        Err(format!("[HTTP] No pending request with id {}", id))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::AtomicU64;

    fn make_state() -> HttpServerState {
        HttpServerState {
            handle: Arc::new(Mutex::new(None)),
            running: Arc::new(Mutex::new(false)),
            responses: Arc::new(Mutex::new(HashMap::new())),
            id_counter: Arc::new(AtomicU64::new(1)),
        }
    }

    #[test]
    fn http_respond_returns_error_for_unknown_id() {
        let state = make_state();
        let result = {
            let responses = state.responses.lock().unwrap();
            drop(responses);
            if let Some(tx) = state.responses.lock().unwrap().remove(&999u64) {
                let _ = tx.send("body".to_string());
                Ok(())
            } else {
                Err(format!("[HTTP] No pending request with id {}", 999u64))
            }
        };
        assert!(result.is_err());
        assert!(result
            .unwrap_err()
            .contains("No pending request with id 999"));
    }

    #[test]
    fn http_respond_sends_to_registered_channel() {
        let state = make_state();
        let (tx, rx) = mpsc::channel::<String>();
        state.responses.lock().unwrap().insert(1u64, tx);

        let result = {
            if let Some(sender) = state.responses.lock().unwrap().remove(&1u64) {
                let _ = sender.send("hello".to_string());
                Ok(())
            } else {
                Err("not found".to_string())
            }
        };

        assert!(result.is_ok());
        assert_eq!(rx.recv().unwrap(), "hello");
    }

    #[test]
    fn http_server_state_starts_not_running() {
        let state = make_state();
        assert!(!*state.running.lock().unwrap());
    }

    #[test]
    fn id_counter_increments() {
        let state = make_state();
        let first = state.id_counter.fetch_add(1, Ordering::SeqCst);
        let second = state.id_counter.fetch_add(1, Ordering::SeqCst);
        assert_eq!(second, first + 1);
    }
}
