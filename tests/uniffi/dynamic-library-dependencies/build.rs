/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::path::PathBuf;
use std::process::Command;
use std::{env, fs};

fn main() {
    gobley_fixture_build_common::generate_scaffolding_from_current_dir();

    // Build the dependency
    let build_output_directory = PathBuf::from(env::var("OUT_DIR").unwrap()).join("the-dependency");
    println!("cargo::rerun-if-changed=the-dependency/Cargo.toml");
    println!("cargo::rerun-if-changed=the-dependency/lib.rs");
    let command_output = Command::new(env::var("CARGO").unwrap())
        .args([
            "build",
            "-p",
            "gobley-fixture-dynamic-library-dependencies-the-dependency",
        ])
        .arg("--target")
        .arg(env::var("TARGET").unwrap())
        .arg("--target-dir")
        .arg(&build_output_directory)
        .output()
        .expect("Failed to run cargo");

    if !command_output.status.success() {
        panic!(
            "cargo exited with a status code {}\n--- stdout\n{}\n--- stderr\n{}\n",
            command_output.status,
            String::from_utf8_lossy(&command_output.stdout),
            String::from_utf8_lossy(&command_output.stderr),
        )
    }

    let library_filename =
        get_library_filename("gobley_fixture_dynamic_library_dependencies_the_dependency");
    let build_output = build_output_directory
        .join(env::var("TARGET").unwrap())
        .join("debug")
        .join(&library_filename);

    fs::copy(
        build_output,
        PathBuf::from(env::var("OUT_DIR").unwrap()).join(&library_filename),
    )
    .unwrap();

    // Link the dependency
    println!("cargo::rustc-link-search={}", env::var("OUT_DIR").unwrap());
}

fn get_library_filename(library_name: &str) -> String {
    #[cfg(target_os = "windows")]
    {
        format!("{library_name}.dll")
    }
    #[cfg(target_vendor = "apple")]
    {
        format!("lib{library_name}.dylib")
    }
    #[cfg(all(not(target_os = "windows"), not(target_vendor = "apple")))]
    {
        format!("lib{library_name}.so")
    }
}
