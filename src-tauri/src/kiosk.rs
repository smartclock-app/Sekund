pub type Result<T> = std::result::Result<T, String>;

#[cfg(target_os = "android")]
use crate::android_jni::{get_admin_component, get_device_policy_manager, load_class, map_jni_err};

// The heavy lifting (lock-task package resolution, PackageInstaller sessions) lives in
// Kotlin — KioskManager, looked up via the context's class loader — since it's much
// more awkward to drive over raw JNI than plain Device Admin calls.
#[cfg(target_os = "android")]
fn kiosk_manager_class<'local>(
    env: &mut jni::JNIEnv<'local>,
    context: &jni::objects::JObject<'local>,
) -> Result<jni::objects::JClass<'local>> {
    let class = load_class(env, context, "uk.dnpk.sekund.KioskManager")?;
    Ok(jni::objects::JClass::from(class))
}

#[cfg(target_os = "android")]
pub fn is_device_owner_with<'local>(
    env: &mut jni::JNIEnv<'local>,
    context: &jni::objects::JObject<'local>,
) -> Result<bool> {
    use jni::objects::JValue;

    let class = kiosk_manager_class(env, context)?;
    let result = env.call_static_method(
        class,
        "isDeviceOwner",
        "(Landroid/content/Context;)Z",
        &[JValue::Object(context)],
    );
    map_jni_err(env, result)?.z().map_err(|e| e.to_string())
}

#[tauri::command]
#[cfg(target_os = "android")]
pub fn is_device_owner() -> Result<bool> {
    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    is_device_owner_with(&mut env, &context)
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn is_device_owner() -> Result<bool> {
    Err("Device owner mode is only supported on Android".into())
}

/// Pins the screen to just this app plus the device's browser(s) and Settings, via
/// DevicePolicyManager.setLockTaskPackages()/Activity.startLockTask(). Requires the
/// app to already be the device owner (see docs/configuration.md).
#[tauri::command]
#[cfg(target_os = "android")]
pub fn enable_kiosk_mode() -> Result<()> {
    use jni::objects::JValue;

    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let class = kiosk_manager_class(&mut env, &context)?;
    let result = env.call_static_method(
        class,
        "enableLockTask",
        "(Landroid/app/Activity;)V",
        &[JValue::Object(&context)],
    );
    map_jni_err(&mut env, result)?;

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn enable_kiosk_mode() -> Result<()> {
    Err("Kiosk mode is only supported on Android".into())
}

#[tauri::command]
#[cfg(target_os = "android")]
pub fn disable_kiosk_mode() -> Result<()> {
    use jni::objects::JValue;

    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let class = kiosk_manager_class(&mut env, &context)?;
    let result = env.call_static_method(
        class,
        "disableLockTask",
        "(Landroid/app/Activity;)V",
        &[JValue::Object(&context)],
    );
    map_jni_err(&mut env, result)?;

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn disable_kiosk_mode() -> Result<()> {
    Err("Kiosk mode is only supported on Android".into())
}

/// Silently and permanently registers MainActivity as the preferred handler for the
/// HOME intent via DevicePolicyManager.addPersistentPreferredActivity(), so the
/// device boots straight into Sekund without a "Complete action using" chooser or a
/// manual "set as launcher" step. Requires the app to already be the device owner.
#[tauri::command]
#[cfg(target_os = "android")]
pub fn set_as_persistent_home() -> Result<()> {
    use jni::objects::JValue;

    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let class = kiosk_manager_class(&mut env, &context)?;
    let result = env.call_static_method(
        class,
        "setAsPersistentHome",
        "(Landroid/app/Activity;)V",
        &[JValue::Object(&context)],
    );
    map_jni_err(&mut env, result)?;

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn set_as_persistent_home() -> Result<()> {
    Err("Setting the launcher is only supported on Android".into())
}

/// Turns on automatic time and timezone (Settings.Global.AUTO_TIME/AUTO_TIME_ZONE)
/// via DevicePolicyManager.setGlobalSetting(), so the clock stays correct without
/// anyone touching Settings. Requires the app to be the device owner.
#[tauri::command]
#[cfg(target_os = "android")]
pub fn enable_auto_time() -> Result<()> {
    use jni::objects::JValue;

    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let dpm = get_device_policy_manager(&mut env, &context)?;
    let component = get_admin_component(&mut env, &context)?;

    for setting in ["auto_time", "auto_time_zone"] {
        let setting_name = env.new_string(setting).map_err(|e| e.to_string())?;
        let value = env.new_string("1").map_err(|e| e.to_string())?;
        let result = env.call_method(
            &dpm,
            "setGlobalSetting",
            "(Landroid/content/ComponentName;Ljava/lang/String;Ljava/lang/String;)V",
            &[
                JValue::Object(&component),
                JValue::Object(&setting_name),
                JValue::Object(&value),
            ],
        );
        map_jni_err(&mut env, result)?;
    }

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn enable_auto_time() -> Result<()> {
    Err("Auto time control is only supported on Android".into())
}

/// Installs an update APK: silently via a PackageInstaller session when the app is
/// the device owner (skipping the "install unknown app" dialog entirely), or via the
/// normal ACTION_INSTALL_PACKAGE confirmation flow otherwise. `context` must actually
/// be the current Activity, since the confirmation flow needs one.
#[cfg(target_os = "android")]
pub fn install_apk_with<'local>(
    env: &mut jni::JNIEnv<'local>,
    context: &jni::objects::JObject<'local>,
    path: &str,
) -> Result<()> {
    use jni::objects::JValue;

    let class = kiosk_manager_class(env, context)?;
    let path_string = env.new_string(path).map_err(|e| e.to_string())?;
    let result = env.call_static_method(
        class,
        "installApk",
        "(Landroid/app/Activity;Ljava/lang/String;)V",
        &[JValue::Object(context), JValue::Object(&path_string)],
    );
    map_jni_err(env, result)?;

    Ok(())
}
