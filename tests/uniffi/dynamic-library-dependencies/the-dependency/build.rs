/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::env;

fn main() {
    if env::var("CARGO_CFG_TARGET_FAMILY").unwrap() == "unix"
        && env::var("CARGO_CFG_TARGET_VENDOR").unwrap() != "apple"
    {
        println!("cargo::rustc-link-arg=-Wl,-soname=libgobley_fixture_dynamic_library_dependencies_the_dependency.so");
    }
}
