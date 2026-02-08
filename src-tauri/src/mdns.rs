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

    mdns.register(service_info).map_err(|e| {
        log::error!("Failed to register: {}", e);
        e.to_string()
    })?;

    log::info!("Service registered successfully");
    let mut mdns_state = state.lock().unwrap();
    mdns_state.daemon = Some(Arc::new(mdns));

    Ok(())
}
