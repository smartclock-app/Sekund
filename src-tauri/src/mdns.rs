use local_ip_address::local_ip;
use mdns_sd::{ServiceDaemon, ServiceInfo};
use std::sync::{Arc, Mutex};

pub struct MdnsState {
    pub daemon: Option<Arc<ServiceDaemon>>,
}

impl MdnsState {
    pub fn new() -> Self {
        Self { daemon: None }
    }
}

#[tauri::command]
pub fn start_mdns(
    state: tauri::State<Mutex<MdnsState>>,
    port: u16,
    name: String,
) -> Result<(), String> {
    log::info!("Starting broadcast...");

    // Check if already registered
    let mut mdns_state = state.lock().unwrap();
    if mdns_state.daemon.is_some() {
        log::info!("Service already registered");
        return Ok(());
    }

    // Validate service name
    if name.is_empty() {
        return Err("Service name cannot be empty".to_string());
    }
    if name.len() > 63 {
        return Err("Service name cannot exceed 63 characters".to_string());
    }
    if !name
        .chars()
        .all(|c| c.is_alphanumeric() || c == '-' || c == '_' || c == ' ')
    {
        log::error!("Invalid service name: {}", name);
        return Err(
            "Service name can only contain alphanumeric characters, hyphens, underscores, and spaces"
                .to_string(),
        );
    }

    let mdns = ServiceDaemon::new().map_err(|e| {
        log::error!("Failed to create daemon: {}", e);
        e.to_string()
    })?;

    let my_local_ip = local_ip().map_err(|e| e.to_string())?;
    let service_info = ServiceInfo::new(
        "_smartclock._tcp.local.",
        &name,
        &format!("{}.local.", hostname::get().unwrap().to_string_lossy()),
        my_local_ip.to_string().as_str(),
        port,
        None,
    )
    .map_err(|e| {
        log::error!("Failed to create service info: {}", e);
        e.to_string()
    })?;

    if let Err(e) = mdns.register(service_info) {
        log::error!("Failed to register: {}", e);
        // Cleanup: shutdown daemon on registration failure
        let _ = mdns.shutdown();
        return Err(e.to_string());
    }

    log::info!("Service registered successfully");
    mdns_state.daemon = Some(Arc::new(mdns));

    Ok(())
}
