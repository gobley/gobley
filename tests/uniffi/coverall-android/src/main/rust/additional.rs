/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use std::sync::Arc;

#[derive(uniffi::Object)]
struct NotConstructible {}

#[uniffi::export]
impl NotConstructible {
    #[uniffi::constructor]
    fn new() -> Arc<Self> {
        panic!("constructor panic")
    }
}

#[uniffi::export]
fn new_not_constructible() -> Arc<NotConstructible> {
    panic!("function panic")
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
enum NotConstructibleError {
    #[error("not constructible")]
    NotConstructible,
}

#[derive(uniffi::Object)]
struct NotConstructible2 {}

#[uniffi::export]
impl NotConstructible2 {
    #[uniffi::constructor]
    fn new() -> Result<Arc<Self>, NotConstructibleError> {
        Err(NotConstructibleError::NotConstructible)
    }
}

#[uniffi::export]
fn new_not_constructible2() -> Result<Arc<NotConstructible2>, NotConstructibleError> {
    Err(NotConstructibleError::NotConstructible)
}
