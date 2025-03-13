/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import enum_types.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class EnumTypesTest {
    @Test
    fun checkVariantValues() {
        AnimalLargeUInt.Dog.value shouldBe 4294967298.toULong()
        AnimalLargeUInt.Cat.value shouldBe 4294967299.toULong()

        // could check `value == (-3).toByte()` but that's ugly :)
        AnimalSignedInt.Dog.value + 3 shouldBe 0
    }
}