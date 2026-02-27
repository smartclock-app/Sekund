use std::thread;

#[cfg(target_os = "macos")]
use system_configuration::{
    core_foundation::array::CFArray,
    core_foundation::runloop::{kCFRunLoopCommonModes, CFRunLoop},
    core_foundation::string::CFString,
    dynamic_store::{SCDynamicStore, SCDynamicStoreBuilder, SCDynamicStoreCallBackContext},
};

#[cfg(any(target_os = "linux", target_os = "android"))]
use rtnetlink::new_connection;

#[cfg(any(target_os = "linux", target_os = "android"))]
use futures::stream::StreamExt;

#[derive(Debug, Clone, serde::Serialize)]
pub enum NetworkEvent {
    InterfaceChanged(String),
    AddressChanged(String),
}

pub fn listen_network_changes<F>(callback: F) -> Result<(), Box<dyn std::error::Error>>
where
    F: Fn(NetworkEvent) + Send + 'static,
{
    #[cfg(target_os = "macos")]
    {
        listen_network_changes_macos(callback)
    }

    #[cfg(any(target_os = "linux", target_os = "android"))]
    {
        listen_network_changes_linux(callback)
    }

    #[cfg(not(any(target_os = "macos", target_os = "linux", target_os = "android")))]
    {
        Err("Unsupported platform".into())
    }
}

#[cfg(target_os = "macos")]
fn listen_network_changes_macos<F>(callback: F) -> Result<(), Box<dyn std::error::Error>>
where
    F: Fn(NetworkEvent) + Send + 'static,
{
    use std::sync::Arc;

    log::info!("[Network] Starting monitor on macOS");

    thread::spawn(move || {
        let callback = Arc::new(callback);
        let callback_clone = callback.clone();

        let callback_context = SCDynamicStoreCallBackContext {
            callout: network_callback,
            info: callback_clone,
        };

        let store = SCDynamicStoreBuilder::new("network-monitor")
            .callback_context(callback_context)
            .build();

        // Watch for network interface changes
        let watch_keys = CFArray::<CFString>::from_CFTypes(&[]);
        let watch_patterns = CFArray::from_CFTypes(&[
            CFString::new("State:/Network/Interface/.*/Link"),
            CFString::new("State:/Network/Interface/.*/IPv4"),
            CFString::new("State:/Network/Interface/.*/IPv6"),
        ]);

        if !store.set_notification_keys(&watch_keys, &watch_patterns) {
            eprintln!("Failed to set notification keys");
            return;
        }

        let run_loop_source = store.create_run_loop_source();
        let run_loop = CFRunLoop::get_current();
        run_loop.add_source(&run_loop_source, unsafe { kCFRunLoopCommonModes });

        CFRunLoop::run_current();
    });

    Ok(())
}

#[cfg(target_os = "macos")]
fn network_callback(
    _store: SCDynamicStore,
    changed_keys: CFArray<CFString>,
    info: &mut std::sync::Arc<dyn Fn(NetworkEvent) + Send + 'static>,
) {
    for key in changed_keys.iter() {
        let key_str = key.to_string();

        if key_str.contains("Interface") {
            // Extract interface name if possible
            let interface = key_str.split('/').last().unwrap_or("unknown").to_string();

            info(NetworkEvent::InterfaceChanged(interface));
        } else if key_str.contains("IPv4") || key_str.contains("IPv6") {
            let interface = key_str
                .split('/')
                .nth_back(1)
                .unwrap_or("unknown")
                .to_string();

            info(NetworkEvent::AddressChanged(interface));
        }
    }
}

#[cfg(any(target_os = "linux", target_os = "android"))]
fn listen_network_changes_linux<F>(callback: F) -> Result<(), Box<dyn std::error::Error>>
where
    F: Fn(NetworkEvent) + Send + 'static,
{
    use std::sync::Arc;

    thread::spawn(move || {
        let rt = tokio::runtime::Runtime::new().unwrap();

        rt.block_on(async {
            let (connection, handle, _messages) = new_connection().unwrap();

            tokio::spawn(connection);

            let mut links = handle.link().get().execute();
            let mut addresses = handle.address().get().execute();

            let callback = Arc::new(callback);
            let callback_clone = callback.clone();

            // Monitor link changes
            tokio::spawn(async move {
                while let Some((message, _)) = links.try_next().await.unwrap() {
                    if let Some(interface_name) = message.header.interface_name() {
                        callback(NetworkEvent::InterfaceChanged(interface_name));
                    }
                }
            });

            // Monitor address changes
            tokio::spawn(async move {
                while let Some((message, _)) = addresses.try_next().await.unwrap() {
                    if let Some(interface_name) = message.header.interface_name() {
                        callback_clone(NetworkEvent::AddressChanged(interface_name));
                    }
                }
            });

            // Keep the runtime alive
            loop {
                tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;
            }
        });
    });

    Ok(())
}

#[tauri::command]
pub fn start_network_monitor(app: tauri::AppHandle) {
    use tauri::Emitter;

    listen_network_changes(move |event| {
        let _ = app.emit("network-change", event);
    })
    .unwrap();
}
