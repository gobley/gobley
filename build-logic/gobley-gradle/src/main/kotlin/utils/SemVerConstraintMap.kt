/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.utils

import gobley.gradle.InternalGobleyGradleApi
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.Constraint
import io.github.z4kn4fein.semver.satisfies

@InternalGobleyGradleApi
class SemVerConstraintMap<T>(
    val default: T,
    private val valueByConstraint: List<Pair<Constraint, T>> = emptyList(),
) {
    constructor(
        default: T,
        vararg valueByConstraint: Pair<Constraint, T>
    ) : this(default, valueByConstraint.toList())

    operator fun get(version: String): T {
        return get(Version.parse(version, strict = false))
    }

    operator fun get(version: Version): T {
        for ((constraint, value) in valueByConstraint) {
            if (version satisfies constraint) {
                return value
            }
        }
        return default
    }
}
