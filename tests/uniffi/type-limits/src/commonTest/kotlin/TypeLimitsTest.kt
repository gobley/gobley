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

    @Test
    fun testUnsignedSmallIntegerLimits() {
        takeU8(UByte.MAX_VALUE) shouldBe UByte.MAX_VALUE
        takeU8(0x80u) shouldBe 0x80u.toUByte()
        takeU16(UShort.MAX_VALUE) shouldBe UShort.MAX_VALUE
        takeU16(0x8000u) shouldBe 0x8000u.toUShort()
        takePort(0xFFFF) shouldBe 0xFFFF
        takePort(0x8000) shouldBe 0x8000
    }
}
