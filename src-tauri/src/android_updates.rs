pub type Result<T> = std::result::Result<T, String>;

#[tauri::command]
pub async fn download_apk(url: String, path: String, window: tauri::WebviewWindow) -> Result<()> {
    use futures::stream::StreamExt;
    use std::fs::File;
    use std::io::Write;
    use tauri::Emitter;

    let client = reqwest::Client::new();
    let response = client
        .get(&url)
        .header("Authorization", "Bearer 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")
        .send()
        .await
        .map_err(|e| e.to_string())?;

    let total = response.content_length().unwrap_or(0) as f64;
    let mut stream = response.bytes_stream();
    let mut file = File::create(&path).map_err(|e| e.to_string())?;
    let mut downloaded = 0u64;

    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|e| e.to_string())?;
        downloaded += chunk.len() as u64;

        // Write chunk immediately instead of buffering
        file.write_all(&chunk).map_err(|e| e.to_string())?;

        if total > 0.0 {
            let progress = (downloaded as f64 / total) * 100.0;
            let _ = window.emit("download-progress", progress);
        }
    }

    Ok(())
}

#[tauri::command]
#[cfg(target_os = "android")]
pub async fn install_apk(path: String) -> Result<()> {
    use jni::objects::{JObject, JValue};

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

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub async fn install_apk(_path: String) -> Result<()> {
    Err("APK installation only supported on Android".into())
}
