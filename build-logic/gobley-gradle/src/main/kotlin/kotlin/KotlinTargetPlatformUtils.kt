/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.kotlin

import com.android.build.api.dsl.KotlinMultiplatformAndroidTarget
import gobley.gradle.InternalGobleyGradleApi
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@InternalGobleyGradleApi
val KotlinTarget.gobleyPlatformType: KotlinPlatformType
    get() = when {
        this is KotlinMultiplatformAndroidTarget -> KotlinPlatformType.androidJvm
        else -> platformType
    }

@InternalGobleyGradleApi
val KotlinTarget.isGobleyAndroidTarget: Boolean
    get() = gobleyPlatformType == KotlinPlatformType.androidJvm
