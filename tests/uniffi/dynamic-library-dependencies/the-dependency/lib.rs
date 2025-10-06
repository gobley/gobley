/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#[no_mangle]
extern "C" fn the_dependency_add(lhs: i32, rhs: i32) -> i32 {
    lhs + rhs
}
