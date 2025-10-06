/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#[cfg_attr(
    target_os = "windows",
    link(name = "gobley_fixture_dynamic_library_dependencies_the_dependency.dll")
)]
#[cfg_attr(
    not(target_os = "windows"),
    link(name = "gobley_fixture_dynamic_library_dependencies_the_dependency")
)]
extern "C" {
    fn the_dependency_add(lhs: i32, rhs: i32) -> i32;
}

#[uniffi::export]
fn the_dependency_add_delegate(lhs: i32, rhs: i32) -> i32 {
    unsafe { the_dependency_add(lhs, rhs) }
}

uniffi::include_scaffolding!("dynamic-library-dependencies");
