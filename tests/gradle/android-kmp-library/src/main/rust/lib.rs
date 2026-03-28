#[unsafe(no_mangle)]
pub extern "C" fn gobley_fixture_android_kmp_library_add(a: i32, b: i32) -> i32 {
    a + b
}
