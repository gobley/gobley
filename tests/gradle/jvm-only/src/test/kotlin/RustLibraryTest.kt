/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.tests.gradle.jvmonly

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RustLibraryTest {
    @Test
    fun addTest() {
        RustLibrary.add(3, 5) shouldBe 8
        RustLibrary.add(2, 7) shouldBe 9
    }
}