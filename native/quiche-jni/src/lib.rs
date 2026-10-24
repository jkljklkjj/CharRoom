pub mod driver;

use driver::Driver;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jint, jlong};
use jni::JNIEnv;
use std::collections::HashMap;
use std::io;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;

type SharedDriver = Arc<Mutex<Driver>>;
static DRIVERS: OnceLock<Mutex<HashMap<i64, SharedDriver>>> = OnceLock::new();
static NEXT_ID: AtomicI64 = AtomicI64::new(1);
fn registry() -> &'static Mutex<HashMap<i64, SharedDriver>> {
    DRIVERS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn failure(error: impl ToString) -> io::Error {
    io::Error::other(error.to_string())
}
fn get(id: i64) -> io::Result<SharedDriver> {
    registry()
        .lock()
        .map_err(failure)?
        .get(&id)
        .cloned()
        .ok_or_else(|| failure("QUIC handle is closed"))
}
fn invoke<T: Default>(env: &mut JNIEnv, action: impl FnOnce(&mut JNIEnv) -> io::Result<T>) -> T {
    match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| action(env))) {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            T::default()
        }
        Err(_) => {
            let _ = env.throw_new("java/io/IOException", "QUIC native operation failed");
            T::default()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_chatlite_charroom_core_quic_QuicheNative_open(
    mut env: JNIEnv,
    _class: JClass,
    host: JString,
    port: jint,
    ca: JString,
    alpn: JString,
) -> jlong {
    invoke(&mut env, |env| {
        let host: String = env.get_string(&host).map_err(failure)?.into();
        let ca: String = env.get_string(&ca).map_err(failure)?.into();
        let alpn: String = env.get_string(&alpn).map_err(failure)?.into();
        let port = u16::try_from(port).map_err(failure)?;
        if port == 0 {
            return Err(failure("QUIC port must be positive"));
        }
        let driver = Driver::connect(&host, port, &ca, &alpn)?;
        let id = NEXT_ID.fetch_add(1, Ordering::Relaxed);
        registry()
            .lock()
            .map_err(failure)?
            .insert(id, Arc::new(Mutex::new(driver)));
        Ok(id)
    })
}
#[no_mangle]
pub extern "system" fn Java_com_chatlite_charroom_core_quic_QuicheNative_poll(
    mut env: JNIEnv,
    _class: JClass,
    id: jlong,
    timeout: jint,
) -> jbyteArray {
    invoke(&mut env, |env| {
        let driver = get(id)?;
        let event = driver
            .lock()
            .map_err(failure)?
            .poll(Duration::from_millis(timeout.clamp(0, 50) as u64))?;
        match event {
            Some(event) => Ok(env
                .byte_array_from_slice(&event.encode())
                .map_err(failure)?
                .into_raw()),
            None => Ok(std::ptr::null_mut()),
        }
    })
}
#[no_mangle]
pub extern "system" fn Java_com_chatlite_charroom_core_quic_QuicheNative_send(
    mut env: JNIEnv,
    _class: JClass,
    id: jlong,
    stream: jlong,
    bytes: JByteArray,
) {
    invoke(&mut env, |env| {
        let bytes = env.convert_byte_array(bytes).map_err(failure)?;
        let driver = get(id)?;
        let result = driver
            .lock()
            .map_err(failure)?
            .send(u64::try_from(stream).map_err(failure)?, bytes);
        result
    })
}
#[no_mangle]
pub extern "system" fn Java_com_chatlite_charroom_core_quic_QuicheNative_close(
    mut env: JNIEnv,
    _class: JClass,
    id: jlong,
) {
    invoke(&mut env, |_| {
        if let Some(driver) = registry().lock().map_err(failure)?.remove(&id) {
            driver.lock().map_err(failure)?.close();
        }
        Ok(())
    })
}
