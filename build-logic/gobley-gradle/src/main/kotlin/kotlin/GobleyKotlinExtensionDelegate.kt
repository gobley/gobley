/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.kotlin

import gobley.gradle.InternalGobleyGradleApi
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectCollection
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@InternalGobleyGradleApi
interface GobleyKotlinExtensionDelegate {
    val pluginId: String
    val targets: DomainObjectCollection<KotlinTarget>
    val sourceSets: NamedDomainObjectCollection<KotlinSourceSet>
    val implementationVersion: String?
}