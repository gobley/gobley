/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import java.io.Serializable

sealed class CargoBinaryCrateSource : Serializable {
    data class Registry(
        val packageName: String,
        val version: String,
        val registry: String? = null,
    ) : CargoBinaryCrateSource(), Serializable

    data class Path(val path: String) : CargoBinaryCrateSource(), Serializable

    data class Git(
        val repository: String,
        val commit: Commit? = null,
    ) : CargoBinaryCrateSource(), Serializable {
        sealed class Commit : Serializable {
            data class Branch(val branch: String) : Commit(), Serializable
            data class Tag(val tag: String) : Commit(), Serializable
            data class Revision(val revision: String) : Commit(), Serializable
        }
    }
}
