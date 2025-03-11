/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.tests.gradle.androidlinking

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLinkingInstrumentedTest {
    // Tests whether C++ libraries built with the `externalNativeBuild {}` Gradle block can be loaded.
    @Test
    fun loadLibrary_CustomCppLibrary_ReturnsTrue() {
        assertTrue(AndroidLinkingLibrary.libraryExists("android-linking-cpp"))
    }

    // Tests whether C++ libraries built with another method and passed to the `dynamicLibraries` property can be loaded.
    @Test
    fun loadLibrary_AnotherCustomCppLibrary_ReturnsTrue() {
        assertTrue(AndroidLinkingLibrary.libraryExists("another-android-linking-cpp"))
    }

    // Tests whether the NDK libraries configured in the `dynamicLibraries` property can be loaded.
    @Test
    fun loadLibrary_CppShared_ReturnsTrue() {
        assertTrue(AndroidLinkingLibrary.libraryExists("c++_shared"))
    }

    // Tests whether the NDK libraries included in the OS can be loaded.
    @Test
    fun loadLibrary_Vulkan_ReturnsTrue() {
        assertTrue(AndroidLinkingLibrary.libraryExists("vulkan"))
    }
}
