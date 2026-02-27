use std::ffi::CStr;
use std::net::{Ipv4Addr, Ipv6Addr};
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
pub struct InterfaceAddresses {
    pub ipv4: Option<String>,
    pub ipv6: Option<String>,
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct NetworkEvent {
    pub interfaces: std::collections::HashMap<String, InterfaceAddresses>,
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
fn get_interface_addresses() -> std::collections::HashMap<String, InterfaceAddresses> {
    use libc::{freeifaddrs, getifaddrs, AF_INET, AF_INET6};

    let mut interfaces = std::collections::HashMap::new();

    unsafe {
        let mut ifaddr_ptr: *mut libc::ifaddrs = std::ptr::null_mut();

        if getifaddrs(&mut ifaddr_ptr) != 0 {
            return interfaces;
        }

        let mut current = ifaddr_ptr;
        while !current.is_null() {
            let ifaddr = &*current;

            if !ifaddr.ifa_name.is_null() {
                let name = CStr::from_ptr(ifaddr.ifa_name)
                    .to_string_lossy()
                    .to_string();

                let entry = interfaces.entry(name).or_insert(InterfaceAddresses {
                    ipv4: None,
                    ipv6: None,
                });

                if !ifaddr.ifa_addr.is_null() {
                    let addr_family = (*ifaddr.ifa_addr).sa_family as i32;

                    if addr_family == AF_INET {
                        let sockaddr_in = ifaddr.ifa_addr as *const libc::sockaddr_in;
                        let ip = Ipv4Addr::from(u32::from_be((*sockaddr_in).sin_addr.s_addr));
                        entry.ipv4 = Some(ip.to_string());
                    } else if addr_family == AF_INET6 {
                        let sockaddr_in6 = ifaddr.ifa_addr as *const libc::sockaddr_in6;
                        let addr_bytes = &(*sockaddr_in6).sin6_addr.s6_addr;
                        let parts = [
                            u16::from_be((addr_bytes[0] as u16) << 8 | addr_bytes[1] as u16),
                            u16::from_be((addr_bytes[2] as u16) << 8 | addr_bytes[3] as u16),
                            u16::from_be((addr_bytes[4] as u16) << 8 | addr_bytes[5] as u16),
                            u16::from_be((addr_bytes[6] as u16) << 8 | addr_bytes[7] as u16),
                            u16::from_be((addr_bytes[8] as u16) << 8 | addr_bytes[9] as u16),
                            u16::from_be((addr_bytes[10] as u16) << 8 | addr_bytes[11] as u16),
                            u16::from_be((addr_bytes[12] as u16) << 8 | addr_bytes[13] as u16),
                            u16::from_be((addr_bytes[14] as u16) << 8 | addr_bytes[15] as u16),
                        ];
                        let ip = Ipv6Addr::new(
                            parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6],
                            parts[7],
                        );
                        entry.ipv6 = Some(ip.to_string());
                    }
                }
            }

            current = ifaddr.ifa_next;
        }

        freeifaddrs(ifaddr_ptr);
    }

    interfaces
}

#[cfg(target_os = "macos")]
fn network_callback(
    _store: SCDynamicStore,
    changed_keys: CFArray<CFString>,
    info: &mut std::sync::Arc<dyn Fn(NetworkEvent) + Send + 'static>,
) {
    let mut changed_interfaces = std::collections::HashSet::new();

    // Get all changed interface names
    for key in changed_keys.iter() {
        let key_str = key.to_string();
        if let Some(interface_name) = key_str.split('/').nth_back(1) {
            changed_interfaces.insert(interface_name.to_string());
        }
    }

    // Query current interface addresses for all changed interfaces
    let all_interfaces = get_interface_addresses();
    let mut result_interfaces = std::collections::HashMap::new();

    for interface_name in changed_interfaces {
        if let Some(addrs) = all_interfaces.get(&interface_name) {
            result_interfaces.insert(interface_name, addrs.clone());
        } else {
            // Interface exists but has no addresses yet
            result_interfaces.insert(
                interface_name,
                InterfaceAddresses {
                    ipv4: None,
                    ipv6: None,
                },
            );
        }
    }

    info(NetworkEvent {
        interfaces: result_interfaces,
    });
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

            let mut addresses = handle.address().get().execute();

            let callback = Arc::new(callback);

            // Monitor address changes
            tokio::spawn(async move {
                while let Some((message, _)) = addresses.try_next().await.unwrap() {
                    if let Some(interface_name) = message.header.interface_name() {
                        let mut interfaces = std::collections::HashMap::new();

                        // Extract address from the message
                        let mut ipv4 = None;
                        let mut ipv6 = None;

                        if let Ok(nlas) = message.nlas() {
                            for nla in nlas {
                                use rtnetlink::packet::address::Nla;
                                match nla {
                                    Nla::Address(addr) => {
                                        if addr.len() == 4 {
                                            ipv4 = Some(format!(
                                                "{}.{}.{}.{}",
                                                addr[0], addr[1], addr[2], addr[3]
                                            ));
                                        } else if addr.len() == 16 {
                                            // Format IPv6 address
                                            ipv6 = Some(format!(
                                                "{:x}:{:x}:{:x}:{:x}:{:x}:{:x}:{:x}:{:x}",
                                                (addr[0] as u16) << 8 | addr[1] as u16,
                                                (addr[2] as u16) << 8 | addr[3] as u16,
                                                (addr[4] as u16) << 8 | addr[5] as u16,
                                                (addr[6] as u16) << 8 | addr[7] as u16,
                                                (addr[8] as u16) << 8 | addr[9] as u16,
                                                (addr[10] as u16) << 8 | addr[11] as u16,
                                                (addr[12] as u16) << 8 | addr[13] as u16,
                                                (addr[14] as u16) << 8 | addr[15] as u16,
                                            ));
                                        }
                                    }
                                    _ => {}
                                }
                            }
                        }

                        interfaces
                            .insert(interface_name.clone(), InterfaceAddresses { ipv4, ipv6 });

                        callback(NetworkEvent { interfaces });
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
