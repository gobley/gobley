/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use log::LevelFilter;

#[no_mangle]
pub extern "system" fn Java_gobley_uniffi_tests_gradle_androidlinking_AndroidLinkingLibrary_libraryExists(
    mut env: JNIEnv,
    _class: JClass,
    library_name: JString,
) -> jboolean {
    log::set_max_level(LevelFilter::Debug);

    #[cfg(target_os = "android")]
    {
        use android_logger::Config;
        android_logger::init_once(Config::default());
    }
    #[cfg(not(target_os = "android"))]
    {
        env_logger::init();
    }

    let library_name = env.get_string(&library_name).unwrap();
    let library_name = library_name.to_str().unwrap();

    #[cfg(target_os = "windows")]
    let library_name = format!("{library_name}.dll\0");
    #[cfg(all(not(target_os = "windows"), target_vendor = "apple"))]
    let library_name = format!("lib{library_name}.dylib\0");
    #[cfg(all(not(target_os = "windows"), not(target_vendor = "apple")))]
    let library_name = format!("lib{library_name}.so\0");

    log::debug!("loading library {library_name}...");

    #[cfg(target_family = "unix")]
    {
        use libc::{dlclose, dlerror, dlopen, RTLD_LAZY, RTLD_LOCAL};

        let library = unsafe { dlopen(library_name.as_ptr() as *const _, RTLD_LAZY | RTLD_LOCAL) };
        if library.is_null() {
            let error = unsafe { std::ffi::CStr::from_ptr(dlerror()) };
            log::error!("failed to load library {library_name}: {error:?}");
            return JNI_FALSE;
        }
        unsafe { dlclose(library) };
    }
    #[cfg(not(target_family = "unix"))]
    {
        use windows::Win32::{Foundation::FreeLibrary, System::LibraryLoader::LoadLibraryA};

        let library = unsafe {
            use windows::core::PCSTR;
            LoadLibraryA(PCSTR(library_name.as_ptr() as *const _))
        };
        let library = match library {
            Ok(library) => library,
            Err(error) => {
                log::error!("failed to load library {library_name}: {}", error.message());
                return JNI_FALSE;
            }
        };

        let _ = unsafe { FreeLibrary(library) };
    }

    JNI_TRUE
}
