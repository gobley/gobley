/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::env;
use std::fs;
use std::path::PathBuf;

fn main() {
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let cmake_out_dir = cmake::build(".");
    let target_os = env::var("CARGO_CFG_TARGET_OS").unwrap();
    for library in [
        "gobley-fixture-gradle-jvm-only-cpp",
        "gobley-fixture-gradle-jvm-only-cpp-2",
    ] {
        let dylib_file_name = match target_os.as_str() {
            "windows" => format!("{library}.dll"),
            "macos" => format!("lib{library}.dylib"),
            _ => format!("lib{library}.so"),
        };
        fs::copy(
            cmake_out_dir.join("lib").join(&dylib_file_name),
            out_dir.join(&dylib_file_name),
        )
        .unwrap();
    }
}
