/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.tests.gradle.androidlinking

object AndroidLinkingLibrary {
    init {
        System.loadLibrary("gobley_fixture_gradle_android_linking")
    }

    @JvmStatic
    external fun libraryExists(libraryName: String): Boolean
}
