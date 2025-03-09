/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.utils

import gobley.gradle.InternalGobleyGradleApi
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersionOrNull
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

@InternalGobleyGradleApi
object RustVersionUtils {
    private val rustVersionRegex = Regex("""rustc ([^ ]+)(?: \([^)]+\))?""")

    private fun getRustVersionOrNull(project: Project, toolchainDirectory: File): Version? {
        val version = project.command("rustc") {
            additionalEnvironmentPath(toolchainDirectory)
            arguments("--version")
            captureStandardOutput()
        }.get().apply {
            assertNormalExitValue()
        }.standardOutput!!.trim()

        val match = rustVersionRegex.matchEntire(version)
            ?: return version.toVersionOrNull(strict = false)

        return match.groupValues[1].toVersionOrNull(strict = false)
    }

    fun getRustVersion(project: Project, toolchainDirectory: File): Version {
        return getRustVersionOrNull(project, toolchainDirectory)
            ?: throw GradleException("could not retrieve Rust version")
    }
}