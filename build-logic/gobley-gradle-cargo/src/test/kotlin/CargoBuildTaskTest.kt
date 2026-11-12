/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.cargo.CargoMessage
import io.kotest.matchers.shouldBe
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test

class CargoBuildTaskTest {
    @Test
    fun testLibrarySearchPathsFromLinkedPaths() {
        val tmp = createTempDirectory("gobley-link-search")
        val oldOut = tmp.resolve("target/debug/build/foo-0123abcd/out")
        val newOut = tmp.resolve("target/debug/build/foo/0123abcd/out")
        oldOut.createDirectories()
        newOut.createDirectories()

        val linkedPaths = listOf(
            "native=$tmp/a",
            "$tmp/b",
            "framework=$tmp/c",
            "dependency=$tmp/d",
            "all=$tmp/e",
            "crate=$tmp/f",
            "native=C:\\foo\\bar",
        )
        val expected = listOf(
            "$tmp/a",
            "$tmp/b",
            "$tmp/c",
            "$tmp/e",
            "C:/foo/bar",
        )

        checkNotNull(newOut.parent).resolve("output").exists() shouldBe false

        val oldMessage = CargoMessage(buildScriptExecutedLine(oldOut.toString(), linkedPaths))
            as CargoMessage.BuildScriptExecuted
        val newMessage = CargoMessage(buildScriptExecutedLine(newOut.toString(), linkedPaths))
            as CargoMessage.BuildScriptExecuted

        oldMessage.outDir shouldBe oldOut.toString()
        newMessage.outDir shouldBe newOut.toString()
        oldMessage.librarySearchPaths() shouldBe expected
        newMessage.librarySearchPaths() shouldBe expected
        oldMessage.librarySearchPaths() shouldBe newMessage.librarySearchPaths()
    }

    private fun buildScriptExecutedLine(outDir: String, linkedPaths: List<String>): String {
        val linkedPathsJson = linkedPaths.joinToString(prefix = "[", postfix = "]") { it.toJsonString() }
        return buildString {
            append("""{"reason":"build-script-executed","package_id":"path+file:///tmp/foo#foo@0.1.0",""")
            append(""""linked_libs":[],"linked_paths":""")
            append(linkedPathsJson)
            append(""","cfgs":[],"env":[],"out_dir":""")
            append(outDir.toJsonString())
            append("}")
        }
    }

    private fun String.toJsonString(): String {
        return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
