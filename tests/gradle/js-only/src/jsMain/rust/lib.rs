/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use wasm_bindgen::prelude::*;

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
unsafe fn external_function() {
    panic!("platform not supported by external_function()")
}

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

#[wasm_bindgen]
pub fn my_wb_add(lhs: i32, rhs: i32) -> i32 {
    lhs + rhs
}

#[wasm_bindgen]
pub fn call_outside_functions() {
    outside_function("outside_function()");
    outside_function_2("outside_function_2()".into());
    outside_function_in_js("outside_function_in_js()");
    outside_function_in_js_2("outside_function_in_js_2()".into());
    outside_function_in_module("outside_function_in_module()");
    outside_function_in_module_2("outside_function_in_module()".into());
}

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = console, js_name = log)]
    fn outside_function(s: &str);
    #[wasm_bindgen(js_namespace = console, js_name = log)]
    fn outside_function_2(j: JsValue);
}

#[wasm_bindgen(inline_js = "
    export function outside_function_in_js(s) {
        console.log(s);
    }
")]
extern "C" {
    fn outside_function_in_js(s: &str);
    #[wasm_bindgen(js_name = outside_function_in_js)]
    fn outside_function_in_js_2(j: JsValue);
}

#[wasm_bindgen(raw_module = "my-outer-module")]
extern "C" {
    fn outside_function_in_module(s: &str);
    #[wasm_bindgen(js_name = outside_function_in_module)]
    fn outside_function_in_module_2(j: JsValue);
}

#[wasm_bindgen]
pub fn add_using_js_delegated(lhs: i32, rhs: i32) -> i32 {
    add_using_js(JsValue::from_f64(lhs as f64), JsValue::from_f64(rhs as f64))
        .as_f64()
        .unwrap() as i32
}

#[wasm_bindgen(inline_js = "
    export function add_using_js(lhs, rhs) {
        return lhs + rhs;
    }
")]
extern "C" {
    fn add_using_js(lhs: JsValue, rhs: JsValue) -> JsValue;
}

#[wasm_bindgen]
pub fn check_module_exported_constants() {
    A.with(|&a| assert_eq!(a, 1));
    B.with(|b| assert_eq!(b, "hello"));
    D.with(|&d| assert_eq!(d, 3));
    E.with(|e| assert_eq!(*e, [4, 5]));
}

#[wasm_bindgen(inline_js = "
    export const a = 1, [b, { c: d }, ...e] = ['hello', { c: 3 }, 4, 5];
")]
extern "C" {
    #[wasm_bindgen(thread_local_v2, js_name = a)]
    static A: i32;
    #[wasm_bindgen(thread_local_v2, js_name = b)]
    static B: String;
    #[wasm_bindgen(thread_local_v2, js_name = d)]
    static D: i32;
    #[wasm_bindgen(thread_local_v2, js_name = e)]
    static E: Vec<i32>;
}

#[wasm_bindgen]
pub struct Foo {
    contents: i32,
}

#[wasm_bindgen]
impl Foo {
    #[wasm_bindgen(constructor)]
    #[allow(clippy::new_without_default)]
    pub fn new() -> Foo {
        Foo { contents: 0 }
    }

    pub fn increment(&mut self) {
        self.contents += 1;
    }

    pub fn decrement(&mut self) {
        self.contents -= 1;
    }

    pub fn get_contents(&self) -> i32 {
        self.contents
    }
}
