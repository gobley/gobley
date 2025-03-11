/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::ffi::CStr;

use android_logger::Config;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use libc::{dlclose, dlerror, dlopen, RTLD_LAZY, RTLD_LOCAL};
use log::LevelFilter;

#[no_mangle]
pub extern "system" fn Java_gobley_uniffi_tests_gradle_androidlinking_AndroidLinkingLibrary_libraryExists(
    mut env: JNIEnv,
    _class: JClass,
    library_name: JString,
) -> jboolean {
    log::set_max_level(LevelFilter::Debug);
    android_logger::init_once(Config::default());

    let library_name = env.get_string(&library_name).unwrap();
    let library_name = library_name.to_str().unwrap();
    let library_name = format!("lib{}.so\0", library_name);

    log::debug!("loading library {library_name}...");

    let library = unsafe { dlopen(library_name.as_ptr() as *const _, RTLD_LAZY | RTLD_LOCAL) };
    if library.is_null() {
        let error = unsafe { CStr::from_ptr(dlerror()) };
        log::error!("failed to load library {library_name}: {error:?}");
        return JNI_FALSE;
    }

    unsafe { dlclose(library) };
    JNI_TRUE
}
