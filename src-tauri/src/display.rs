pub type Result<T> = std::result::Result<T, String>;

#[cfg(target_os = "android")]
const SCREEN_BRIGHT_WAKE_LOCK: i32 = 0x0000000a;
#[cfg(target_os = "android")]
const ACQUIRE_CAUSES_WAKEUP: i32 = 0x10000000;
#[cfg(target_os = "android")]
const ON_AFTER_RELEASE: i32 = 0x20000000;

#[cfg(target_os = "android")]
use crate::android_jni::{get_admin_component, get_device_policy_manager, map_jni_err};

#[cfg(target_os = "android")]
fn is_admin_active(
    env: &mut jni::JNIEnv,
    dpm: &jni::objects::JObject,
    component: &jni::objects::JObject,
) -> Result<bool> {
    use jni::objects::JValue;

    let result = env.call_method(
        dpm,
        "isAdminActive",
        "(Landroid/content/ComponentName;)Z",
        &[JValue::Object(component)],
    );
    map_jni_err(env, result)?.z().map_err(|e| e.to_string())
}

#[tauri::command]
#[cfg(target_os = "android")]
pub fn is_display_admin_active() -> Result<bool> {
    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let dpm = get_device_policy_manager(&mut env, &context)?;
    let component = get_admin_component(&mut env, &context)?;

    is_admin_active(&mut env, &dpm, &component)
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn is_display_admin_active() -> Result<bool> {
    Err("Display control is only supported on Android".into())
}

/// Opens the system "Activate device admin app" dialog so the user can grant
/// Sekund the permission it needs to turn the display off. This is a one-time,
/// on-device step — it cannot be triggered remotely/headlessly.
#[tauri::command]
#[cfg(target_os = "android")]
pub fn request_display_admin() -> Result<()> {
    use jni::objects::JValue;

    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let component = get_admin_component(&mut env, &context)?;

    let intent_class = env
        .find_class("android/content/Intent")
        .map_err(|e| e.to_string())?;
    let result = env.new_object(intent_class, "()V", &[]);
    let intent = map_jni_err(&mut env, result)?;

    let action = env
        .new_string("android.app.action.ADD_DEVICE_ADMIN")
        .map_err(|e| e.to_string())?;
    let result = env.call_method(
        &intent,
        "setAction",
        "(Ljava/lang/String;)Landroid/content/Intent;",
        &[JValue::Object(&action)],
    );
    map_jni_err(&mut env, result)?;

    let admin_extra_key = env
        .new_string("android.app.extra.DEVICE_ADMIN")
        .map_err(|e| e.to_string())?;
    let result = env.call_method(
        &intent,
        "putExtra",
        "(Ljava/lang/String;Landroid/os/Parcelable;)Landroid/content/Intent;",
        &[JValue::Object(&admin_extra_key), JValue::Object(&component)],
    );
    map_jni_err(&mut env, result)?;

    let explanation_key = env
        .new_string("android.app.extra.ADD_EXPLANATION")
        .map_err(|e| e.to_string())?;
    let explanation = env
        .new_string("Allows Sekund to turn the display on and off remotely.")
        .map_err(|e| e.to_string())?;
    let result = env.call_method(
        &intent,
        "putExtra",
        "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
        &[
            JValue::Object(&explanation_key),
            JValue::Object(&explanation),
        ],
    );
    map_jni_err(&mut env, result)?;

    let result = env.call_method(
        &context,
        "startActivity",
        "(Landroid/content/Intent;)V",
        &[JValue::Object(&intent)],
    );
    map_jni_err(&mut env, result)?;

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn request_display_admin() -> Result<()> {
    Err("Display control is only supported on Android".into())
}

/// Turns the display off via DevicePolicyManager.lockNow() — the same mechanism
/// triggered by a single press of the physical power button. Requires the device
/// admin permission granted via `request_display_admin` first.
#[tauri::command]
#[cfg(target_os = "android")]
pub fn turn_display_off() -> Result<()> {
    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let dpm = get_device_policy_manager(&mut env, &context)?;
    let component = get_admin_component(&mut env, &context)?;

    if !is_admin_active(&mut env, &dpm, &component)? {
        return Err(
            "Device admin permission not granted. Call request_display_admin and activate it in Settings first."
                .into(),
        );
    }

    let result = env.call_method(&dpm, "lockNow", "()V", &[]);
    map_jni_err(&mut env, result)?;

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn turn_display_off() -> Result<()> {
    Err("Display control is only supported on Android".into())
}

/// Turns the display back on by briefly acquiring a wake lock that forces the
/// screen awake, regardless of how it was turned off.
#[tauri::command]
#[cfg(target_os = "android")]
pub fn turn_display_on() -> Result<()> {
    use jni::objects::JValue;

    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let service_name = env.new_string("power").map_err(|e| e.to_string())?;
    let result = env.call_method(
        &context,
        "getSystemService",
        "(Ljava/lang/String;)Ljava/lang/Object;",
        &[JValue::Object(&service_name)],
    );
    let power_manager = map_jni_err(&mut env, result)?
        .l()
        .map_err(|e| e.to_string())?;

    let flags = SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP | ON_AFTER_RELEASE;
    let tag = env
        .new_string("Sekund:DisplayOnWakeLock")
        .map_err(|e| e.to_string())?;
    let result = env.call_method(
        &power_manager,
        "newWakeLock",
        "(ILjava/lang/String;)Landroid/os/PowerManager$WakeLock;",
        &[JValue::Int(flags), JValue::Object(&tag)],
    );
    let wake_lock = map_jni_err(&mut env, result)?
        .l()
        .map_err(|e| e.to_string())?;

    let result = env.call_method(&wake_lock, "acquire", "(J)V", &[JValue::Long(10_000)]);
    map_jni_err(&mut env, result)?;

    let result = env.call_method(&wake_lock, "release", "()V", &[]);
    map_jni_err(&mut env, result)?;

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn turn_display_on() -> Result<()> {
    Err("Display control is only supported on Android".into())
}

/// Turns on Android's own adaptive brightness (using the device's ambient light
/// sensor and its own tuned auto-brightness curve) via
/// DevicePolicyManager.setSystemSetting(). Requires the app to be the device
/// owner; callers should check `is_device_owner` first.
#[tauri::command]
#[cfg(target_os = "android")]
pub fn enable_auto_brightness() -> Result<()> {
    use jni::objects::JValue;

    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let dpm = get_device_policy_manager(&mut env, &context)?;
    let component = get_admin_component(&mut env, &context)?;

    let setting = env
        .new_string("screen_brightness_mode")
        .map_err(|e| e.to_string())?;
    // Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    let value = env.new_string("1").map_err(|e| e.to_string())?;
    let result = env.call_method(
        &dpm,
        "setSystemSetting",
        "(Landroid/content/ComponentName;Ljava/lang/String;Ljava/lang/String;)V",
        &[
            JValue::Object(&component),
            JValue::Object(&setting),
            JValue::Object(&value),
        ],
    );
    map_jni_err(&mut env, result)?;

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn enable_auto_brightness() -> Result<()> {
    Err("Brightness control is only supported on Android".into())
}

/// Disables the lock screen entirely via DevicePolicyManager.setKeyguardDisabled(),
/// so waking the display always lands directly on the clock face rather than a
/// lock screen. Requires the app to be the device owner.
#[tauri::command]
#[cfg(target_os = "android")]
pub fn disable_keyguard() -> Result<()> {
    use jni::objects::JValue;

    let ctx = ndk_context::android_context();
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
    let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
    let context = unsafe { jni::objects::JObject::from_raw(ctx.context() as jni::sys::jobject) };

    let dpm = get_device_policy_manager(&mut env, &context)?;
    let component = get_admin_component(&mut env, &context)?;

    let result = env.call_method(
        &dpm,
        "setKeyguardDisabled",
        "(Landroid/content/ComponentName;Z)Z",
        &[JValue::Object(&component), JValue::from(true)],
    );
    map_jni_err(&mut env, result)?;

    Ok(())
}

#[tauri::command]
#[cfg(not(target_os = "android"))]
pub fn disable_keyguard() -> Result<()> {
    Err("Keyguard control is only supported on Android".into())
}
