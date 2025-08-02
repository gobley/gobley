/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.kotlin

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import org.gradle.api.NamedDomainObjectCollection
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

@InternalGobleyGradleApi
interface GobleyKotlinSourceSetCollection : NamedDomainObjectCollection<KotlinSourceSet> {
    val commonMain: KotlinSourceSet

    fun androidMain(variant: Variant? = null): KotlinSourceSet
    val androidMain get() = androidMain()

    fun androidUnitTest(variant: Variant? = null): KotlinSourceSet
    val androidUnitTest get() = androidUnitTest()

    val jvmMain: KotlinSourceSet
    val jsMain: KotlinSourceSet
    val wasmJsMain: KotlinSourceSet
    val wasmWasiMain: KotlinSourceSet
}