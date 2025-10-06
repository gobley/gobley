/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import io.kotest.matchers.shouldBe
import uniffi.dynamic_library_dependencies.*
import kotlin.test.Test

class DynamicLibraryDependenciesTests {
    @Test
    fun test() {
        theDependencyAddDelegate(3, 4) shouldBe 7
    }
}