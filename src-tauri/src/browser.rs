#[tauri::command]
pub fn launch_browser() -> Result<String, String> {
    #[cfg(not(any(target_os = "android", target_os = "macos")))]
    {
        return Err("Browser launch only supported on Android and macOS".to_string());
    }

    #[cfg(target_os = "android")]
    {
        use std::process::Command;

        Command::new("am")
            .args(&[
                "start",
                "-a",
                "android.intent.action.MAIN",
                "-c",
                "android.intent.category.APP_BROWSER",
            ])
            .output()
            .map_err(|e| format!("Failed to launch browser: {}", e))?;
    }

    #[cfg(target_os = "macos")]
    {
        use std::process::Command;

        // First, read the Launch Services preferences
        let defaults_output = Command::new("defaults")
            .arg("read")
            .arg(format!(
                "{}/Library/Preferences/com.apple.LaunchServices/com.apple.launchservices.secure",
                std::env::var("HOME").unwrap()
            ))
            .output()
            .map_err(|e| format!("Failed to read defaults: {}", e))?;

        let defaults_str = String::from_utf8_lossy(&defaults_output.stdout);

        // Write the defaults output to awk's stdin
        use std::io::Write;
        let mut awk_process = Command::new("awk")
            .arg("-F\"")
            .arg("/http;/{print window[(NR)-1]}{window[NR]=$2}")
            .stdin(std::process::Stdio::piped())
            .stdout(std::process::Stdio::piped())
            .spawn()
            .map_err(|e| format!("Failed to spawn awk: {}", e))?;

        awk_process
            .stdin
            .as_mut()
            .unwrap()
            .write_all(defaults_str.as_bytes())
            .map_err(|e| format!("Failed to write to awk stdin: {}", e))?;
        let awk_output = awk_process
            .wait_with_output()
            .map_err(|e| format!("Failed to wait for awk: {}", e))?;

        let browser_name = String::from_utf8_lossy(&awk_output.stdout)
            .trim()
            .to_string();

        log::info!("[Browser] Launching {}", browser_name);

        // Finally, open the browser
        Command::new("open")
            .arg("-b")
            .arg(&browser_name)
            .output()
            .map_err(|e| format!("Failed to launch browser: {}", e))?;
    }

    Ok("Browser launched successfully".to_string())
}
