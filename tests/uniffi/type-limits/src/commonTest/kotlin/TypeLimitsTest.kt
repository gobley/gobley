/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import io.kotest.matchers.collections.*
import io.kotest.matchers.*
import type_limits.*
import kotlin.test.*

class TypeLimitsTest {
    @Test
    fun testStringLimits() {
        // Kotlin Stdlib's String.encodeToByteArray() does not throw for invalid UTF-16 sequence.
        // The replacement byte sequence to be used is platform-dependant.
        // On JVM: 0x3F ('?') is used.
        // On Koltin/Native: 0xEF, 0xBF, 0xBD is used. When decoded, this becomes 0xFFFD.
        takeString("\ud800").shouldBeOneOf("?", "\uFFFD")
        takeString("") shouldBe ""
        takeString("愛") shouldBe "愛"
        takeString("💖") shouldBe "💖"
    }
}
