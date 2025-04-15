/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo

import gobley.gradle.rust.CrateType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * A JSON format message emitted from `cargo build --message-format json`.
 * Forked from the [`cargo_metadata`](https://github.com/oli-obk/cargo_metadata/blob/0b4be024c57d7855a2dfbdf2ec2a48f3e16e9f78/src/messages.rs#L225-L245) crate.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("reason")
internal sealed class CargoMessage {
    @Serializable
    @SerialName("compiler-artifact")
    data class CompilerArtifact(
        @SerialName("package_id") val packageId: String,
        @SerialName("target") val target: Target,
        @SerialName("profile") val profile: ArtifactProfile,
        @SerialName("features") val features: List<String>,
        @SerialName("filenames") val filenames: List<String>,
        @SerialName("executable") val executable: String? = null,
        @SerialName("fresh") val fresh: Boolean,
    ) : CargoMessage()

    @Serializable
    @SerialName("compiler-message")
    data class CompilerMessage(
        @SerialName("package_id") val packageId: String,
        @SerialName("target") val target: Target,
        @SerialName("message") val message: Diagnostic,
    ) : CargoMessage()

    @Serializable
    @SerialName("build-script-executed")
    data class BuildScriptExecuted(
        @SerialName("package_id") val packageId: String,
        @SerialName("linked_libs") val linkedLibs: List<String>,
        @SerialName("linked_paths") val linkedPaths: List<String>,
        @SerialName("cfgs") val cfgs: List<String>,
        @SerialName("env") val env: List<List<String>>,
        @SerialName("out_dir") val outDir: String,
    ) : CargoMessage()

    @Serializable
    @SerialName("build-finished")
    data class BuildFinished(val success: Boolean) : CargoMessage()

    @Serializable
    data class Target(
        @SerialName("name") val name: String,
        @SerialName("kind") val kind: List<CargoTargetKind>,
        @SerialName("crate_types") val crateTypes: List<CrateType>,
        @SerialName("src_path") val srcPath: String,
        @SerialName("edition") val edition: String,
        @SerialName("doctest") val doctest: Boolean = false,
        @SerialName("test") val test: Boolean = true,
    )

    @Serializable
    data class ArtifactProfile(
        @SerialName("opt_level") val optLevel: String,
        @SerialName("debuginfo") val debuginfo: Int? = null,
        @SerialName("debug_assertions") val debugAssertions: Boolean,
        @SerialName("overflow_checks") val overflowChecks: Boolean,
        @SerialName("test") val test: Boolean,
    )

    @Serializable
    data class Diagnostic(
        @SerialName("message") val message: String,
        @SerialName("code") val code: DiagnosticCode?,
        @SerialName("level") val level: String,
        @SerialName("spans") val spans: List<DiagnosticSpan>,
        @SerialName("children") val children: List<Diagnostic>,
        @SerialName("rendered") val rendered: String?,
    )

    @Serializable
    data class DiagnosticCode(
        @SerialName("code") val code: String,
        @SerialName("explanation") val explanation: String?,
    )

    @Serializable
    data class DiagnosticSpan(
        @SerialName("file_name") val fileName: String,
        @SerialName("byte_start") val byteStart: Int,
        @SerialName("byte_end") val byteEnd: Int,
        @SerialName("line_start") val lineStart: Int,
        @SerialName("line_end") val lineEnd: Int,
        @SerialName("column_start") val columnStart: Int,
        @SerialName("column_end") val columnEnd: Int,
        @SerialName("is_primary") val isPrimary: Boolean,
        @SerialName("text") val text: List<DiagnosticSpanLine>,
        @SerialName("label") val label: String?,
        @SerialName("suggested_replacement") val suggestedReplacement: String?,
        @SerialName("suggestion_applicability") val suggestionApplicability: String?,
        @SerialName("expansion") val expansion: DiagnosticSpanExpansion?,
    )

    @Serializable
    data class DiagnosticSpanLine(
        @SerialName("text") val text: String,
        @SerialName("highlight_start") val highlightStart: Int,
        @SerialName("highlight_end") val highlightEnd: Int,
    )

    @Serializable
    data class DiagnosticSpanExpansion(
        @SerialName("span") val span: DiagnosticSpan,
        @SerialName("macro_decl_name") val macroDeclName: String,
        @SerialName("def_site_span") val defSiteSpan: DiagnosticSpan?,
    )
}

internal fun CargoMessage(message: String): CargoMessage {
    return cargoMessageJson.decodeFromString(message)
}

private val cargoMessageJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "reason"
}