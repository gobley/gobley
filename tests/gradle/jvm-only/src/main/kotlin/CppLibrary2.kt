/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.tests.gradle.jvmonly

import com.sun.jna.Library
import com.sun.jna.Native

object CppLibrary2 : Library {
    init {
        Native.register(CppLibrary2::class.java, "gobley-fixture-gradle-jvm-only-cpp-2")
    }

    @JvmName("my_gcd")
    external fun gcd(lhs: Int, rhs: Int): Int
}