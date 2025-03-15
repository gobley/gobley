/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

@file:Suppress("unused", "UNUSED_VARIABLE")

import uniffi.docstring.*
import kotlin.test.Test

class DocstringTest {
    @Test
    fun run() {
        test()
        testMultiline()

        EnumTest.ONE
        EnumTest.TWO

        AssociatedEnumTest.Test(0)
        AssociatedEnumTest.Test2(0)

        ErrorTest.One("hello")
        ErrorTest.Two("hello")

        AssociatedErrorTest.Test(0)
        AssociatedErrorTest.Test2(0)

        val obj1 = ObjectTest
        val obj2 = ObjectTest.newAlternate()
        obj2.test()

        val rec = RecordTest(123)
        val recField = rec.test
    }

    class CallbackImpls : CallbackTest {
        override fun test() {}
    }
}