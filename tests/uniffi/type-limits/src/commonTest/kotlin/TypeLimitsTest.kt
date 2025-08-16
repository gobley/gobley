/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.*
import type_limits.*
import kotlin.test.*

class TypeLimitsTest {
    @Test
    fun testStringLimits() {
        shouldThrow<CharacterCodingException> {
            takeString("\ud800")
        }
        takeString("") shouldBe ""
        takeString("愛") shouldBe "愛"
        takeString("💖") shouldBe "💖"
    }
}
