/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import coverall.InternalException
import coverall.NotConstructible
import coverall.NotConstructible2
import coverall.NotConstructibleException
import coverall.newNotConstructible
import coverall.newNotConstructible2
import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

// Covers test cases not in the original unit test in upstream.
class AdditionalCoverallTest {
    @Test
    fun testPanicInsideFunctionReturningObject() {
        if (uniffiSupported) {
            shouldThrow<InternalException> {
                NotConstructible()
            }
            shouldThrow<InternalException> {
                newNotConstructible()
            }
        }
    }

    @Test
    fun testErrorThrownFromFunctionReturningObject() {
        if (uniffiSupported) {
            shouldThrow<NotConstructibleException> {
                NotConstructible2()
            }
            shouldThrow<NotConstructibleException> {
                newNotConstructible2()
            }
        }
    }
}