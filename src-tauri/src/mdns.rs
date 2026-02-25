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

pub fn validate_mdns_name(name: &str) -> Result<(), String> {
    if name.is_empty() {
        return Err("[MDNS] Service name cannot be empty".to_string());
    }
    if name.len() > 63 {
        return Err("[MDNS] Service name cannot exceed 63 characters".to_string());
    }
    if !name
        .chars()
        .all(|c| c.is_alphanumeric() || c == '-' || c == '_' || c == ' ')
    {
        return Err(
            "[MDNS] Service name can only contain alphanumeric characters, hyphens, underscores, and spaces"
                .to_string(),
        );
    }
    Ok(())
}

#[tauri::command]
pub fn start_mdns(
    state: tauri::State<Mutex<MdnsState>>,
    port: u16,
    name: String,
) -> Result<(), String> {
    log::info!("[MDNS] Starting broadcast...");

    // Check if already registered
    let mut mdns_state = state.lock().unwrap();
    if mdns_state.daemon.is_some() {
        log::info!("[MDNS] Service already registered");
        return Ok(());
    }

    // Validate service name
    validate_mdns_name(&name).map_err(|e| {
        if e.contains("can only contain") {
            log::error!("[MDNS] Invalid service name: {}", name);
        }
        e
    })?;

    let mdns = ServiceDaemon::new().map_err(|e| {
        log::error!("[MDNS] Failed to create daemon: {}", e);
        e.to_string()
    })?;

    let my_local_ip = local_ip().map_err(|e| e.to_string())?;
    let service_info = ServiceInfo::new(
        "_sekund._tcp.local.",
        &name,
        &format!("{}.local.", hostname::get().unwrap().to_string_lossy()),
        my_local_ip.to_string().as_str(),
        port,
        None,
    )
    .map_err(|e| {
        log::error!("[MDNS] Failed to create service info: {}", e);
        e.to_string()
    })?;

    if let Err(e) = mdns.register(service_info) {
        log::error!("[MDNS] Failed to register: {}", e);
        // Cleanup: shutdown daemon on registration failure
        let _ = mdns.shutdown();
        return Err(e.to_string());
    }

    log::info!("[MDNS] Service registered successfully");
    mdns_state.daemon = Some(Arc::new(mdns));

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::validate_mdns_name;

    #[test]
    fn valid_name_is_accepted() {
        assert!(validate_mdns_name("Sekund").is_ok());
        assert!(validate_mdns_name("my-device").is_ok());
        assert!(validate_mdns_name("my_device").is_ok());
        assert!(validate_mdns_name("Device123").is_ok());
    }

    #[test]
    fn empty_name_is_rejected() {
        let err = validate_mdns_name("").unwrap_err();
        assert!(err.contains("cannot be empty"));
    }

    #[test]
    fn name_exceeding_63_chars_is_rejected() {
        let long_name = "a".repeat(64);
        let err = validate_mdns_name(&long_name).unwrap_err();
        assert!(err.contains("cannot exceed 63 characters"));
    }

    #[test]
    fn name_of_exactly_63_chars_is_accepted() {
        let name = "a".repeat(63);
        assert!(validate_mdns_name(&name).is_ok());
    }

    #[test]
    fn name_with_invalid_chars_is_rejected() {
        for invalid in &["hello!", "hello@world", "hello.world", "hello/world"] {
            let err = validate_mdns_name(invalid).unwrap_err();
            assert!(
                err.contains("can only contain"),
                "expected rejection for: {}",
                invalid
            );
        }
    }
}
