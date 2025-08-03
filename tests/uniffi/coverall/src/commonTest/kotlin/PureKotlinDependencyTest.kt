/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import io.kotest.matchers.shouldBe
import kotlin.test.Test

// Tests whether projects with Gobley plugins can depend on other pure-Kotlin projects
// without the Rust plugin.
class PureKotlinDependencyTest {
    @Test
    fun canDependOnPureKotlinProject() {
        PureKotlinLibrary.add(3, 5) shouldBe 8
    }
}