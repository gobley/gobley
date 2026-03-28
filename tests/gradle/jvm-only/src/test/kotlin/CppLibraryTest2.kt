/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.tests.gradle.jvmonly

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CppLibraryTest2 {
    @Test
    fun gcdTest() {
        CppLibrary2.gcd(77, 121) shouldBe 11
        CppLibrary2.gcd(15, 21) shouldBe 3
        CppLibrary2.gcd(12, 8) shouldBe 4
    }
}