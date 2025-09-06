/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

#[no_mangle]
pub extern "C" fn add(lhs: i32, rhs: i32) -> i32 {
    lhs + rhs
}

#[repr(C)]
pub struct BigStruct {
    content: [i32; 20],
}

#[no_mangle]
pub extern "C" fn consume_big_struct(s: &BigStruct) -> i32 {
    s.content.iter().sum()
}

#[cfg(target_arch = "wasm32")]
unsafe extern "C" {
    fn external_function();
}

// To prevent undefined symbol error in CI when building for the host target
#[cfg(not(target_arch = "wasm32"))]
unsafe fn external_function() {}

#[no_mangle]
pub extern "C" fn call_external_function_twice() {
    unsafe {
        external_function();
        external_function();
    }
}

#[no_mangle]
pub extern "C" fn call_function_pointer_twice(f: extern "C" fn(i32, f32) -> f64) -> f64 {
    f(5, 6.0) + f(10, 11.0)
}

#[no_mangle]
pub extern "C" fn call_multiple_function_pointers(
    f1: extern "C" fn(i32, f32) -> f64,
    f2: extern "C" fn(i32) -> f32,
) -> f64 {
    f1(15, 23.0) * f2(9) as f64
}
