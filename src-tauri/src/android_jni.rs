#![cfg(target_os = "android")]

pub type Result<T> = std::result::Result<T, String>;

// Maps a JNI call error (including a pending Java exception, which is cleared so
// the JNIEnv stays usable for subsequent calls) to a plain String. The JNI call
// itself must already have been made into `result` before calling this, since
// borrowing `env` here at the same time as evaluating the call would conflict.
pub fn map_jni_err<T>(env: &mut jni::JNIEnv, result: jni::errors::Result<T>) -> Result<T> {
    result.map_err(|e| {
        let _ = env.exception_clear();
        e.to_string()
    })
}

// Looks up an app class (as opposed to a system/framework class) by its fully
// qualified name, via the context's own class loader. A natively-attached
// thread's FindClass can't see app classes directly, so this is the standard
// workaround.
pub fn load_class<'local>(
    env: &mut jni::JNIEnv<'local>,
    context: &jni::objects::JObject<'local>,
    binary_name: &str,
) -> Result<jni::objects::JObject<'local>> {
    use jni::objects::JValue;

    let result = env.call_method(context, "getClassLoader", "()Ljava/lang/ClassLoader;", &[]);
    let class_loader = map_jni_err(env, result)?.l().map_err(|e| e.to_string())?;

    let class_name = env.new_string(binary_name).map_err(|e| e.to_string())?;
    let result = env.call_method(
        &class_loader,
        "loadClass",
        "(Ljava/lang/String;)Ljava/lang/Class;",
        &[JValue::Object(&class_name)],
    );
    map_jni_err(env, result)?.l().map_err(|e| e.to_string())
}

// Builds a ComponentName pointing at SekundDeviceAdminReceiver — the admin
// component every DevicePolicyManager call needs to identify the caller.
pub fn get_admin_component<'local>(
    env: &mut jni::JNIEnv<'local>,
    context: &jni::objects::JObject<'local>,
) -> Result<jni::objects::JObject<'local>> {
    use jni::objects::JValue;

    let admin_class = load_class(env, context, "uk.dnpk.sekund.SekundDeviceAdminReceiver")?;

    let component_class = env
        .find_class("android/content/ComponentName")
        .map_err(|e| e.to_string())?;

    let result = env.new_object(
        component_class,
        "(Landroid/content/Context;Ljava/lang/Class;)V",
        &[JValue::Object(context), JValue::Object(&admin_class)],
    );
    map_jni_err(env, result)
}

pub fn get_device_policy_manager<'local>(
    env: &mut jni::JNIEnv<'local>,
    context: &jni::objects::JObject<'local>,
) -> Result<jni::objects::JObject<'local>> {
    use jni::objects::JValue;

    let service_name = env.new_string("device_policy").map_err(|e| e.to_string())?;
    let result = env.call_method(
        context,
        "getSystemService",
        "(Ljava/lang/String;)Ljava/lang/Object;",
        &[JValue::Object(&service_name)],
    );
    map_jni_err(env, result)?.l().map_err(|e| e.to_string())
}
