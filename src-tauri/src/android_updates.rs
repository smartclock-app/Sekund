use std::collections::HashMap;

#[derive(serde::Deserialize)]
pub struct PlatformInfo {
    pub url: String,
}

#[derive(serde::Deserialize)]
pub struct LatestJson {
    pub version: String,
    pub platforms: HashMap<String, PlatformInfo>,
}

#[derive(serde::Serialize)]
pub struct UpdateInfo {
    pub version: String,
    pub url: Option<String>,
}

pub type Result<T> = std::result::Result<T, String>;

#[tauri::command]
pub async fn check_for_update() -> Result<Option<UpdateInfo>> {
    let response = reqwest::get(
        "https://github.com/yourusername/ClockBeta/releases/latest/download/latest.json",
    )
    .await
    .map_err(|e| e.to_string())?
    .json::<LatestJson>()
    .await
    .map_err(|e| e.to_string())?;

    let current = env!("CARGO_PKG_VERSION").trim_start_matches('v');
    let remote = response.version.trim_start_matches('v');

    if remote > current {
        Ok(Some(UpdateInfo {
            version: response.version,
            url: response
                .platforms
                .get("android-aarch64")
                .map(|p| p.url.clone()),
        }))
    } else {
        Ok(None)
    }
}

#[tauri::command]
pub async fn download_apk(url: String, path: String) -> Result<()> {
    let bytes = reqwest::get(&url)
        .await
        .map_err(|e| e.to_string())?
        .bytes()
        .await
        .map_err(|e| e.to_string())?;
    std::fs::write(&path, bytes).map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
#[cfg(target_os = "android")]
pub async fn install_apk(path: String) -> Result<()> {
    use jni::objects::{JObject, JValue};

    let _ = tauri::async_runtime::spawn_blocking(move || {
        let ctx = ndk_context::android_context();
        let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
        let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;

        let intent_class = env
            .find_class("android/content/Intent")
            .map_err(|e| e.to_string())?;
        let intent = env
            .new_object(intent_class, "()V", &[])
            .map_err(|e| e.to_string())?;

        let action = env
            .new_string("android.intent.action.VIEW")
            .map_err(|e| e.to_string())?;
        env.call_method(
            &intent,
            "setAction",
            "(Ljava/lang/String;)Landroid/content/Intent;",
            &[JValue::Object(&action.into())],
        )
        .map_err(|e| e.to_string())?;

        let uri_class = env
            .find_class("android/net/Uri")
            .map_err(|e| e.to_string())?;
        let uri_string = env
            .new_string(format!("file://{}", path))
            .map_err(|e| e.to_string())?;
        let uri = env
            .call_static_method(
                uri_class,
                "parse",
                "(Ljava/lang/String;)Landroid/net/Uri;",
                &[JValue::Object(&uri_string.into())],
            )
            .map_err(|e| e.to_string())?
            .l()
            .map_err(|e| e.to_string())?;

        env.call_method(
            &intent,
            "setData",
            "(Landroid/net/Uri;)Landroid/content/Intent;",
            &[JValue::Object(&uri)],
        )
        .map_err(|e| e.to_string())?;

        let mime_type = env
            .new_string("application/vnd.android.package-archive")
            .map_err(|e| e.to_string())?;
        env.call_method(
            &intent,
            "setType",
            "(Ljava/lang/String;)Landroid/content/Intent;",
            &[JValue::Object(&mime_type.into())],
        )
        .map_err(|e| e.to_string())?;

        let context = unsafe { JObject::from_raw(ctx.context() as jni::sys::jobject) };
        env.call_method(
            &context,
            "startActivity",
            "(Landroid/content/Intent;)V",
            &[JValue::Object(&intent.into())],
        )
        .map_err(|e| e.to_string())?;

        Ok::<(), String>(())
    })
    .await
    .map_err(|e| e.to_string())?;

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub async fn install_apk(_path: String) -> Result<()> {
    Err("APK installation only supported on Android".into())
}
