/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.rust.dsl

import gobley.gradle.PluginIds
import gobley.gradle.GobleyHost
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.utils.PluginUtils
import gobley.gradle.utils.RustVersionUtils
import gobley.gradle.utils.command
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

abstract class RustExtension(project: Project) {
    /**
     * The directory where `cargo` and `rustup` are installed. Defaults to `~/.cargo/bin`.
     */
    val toolchainDirectory: Property<File> =
        project.objects.property<File>()
            .convention(GobleyHost.current.platform.defaultToolchainDirectory)
}

fun KotlinMultiplatformExtension.hostNativeTarget(
    configure: KotlinNativeTarget.() -> Unit = {},
): KotlinNativeTarget {
    return when (GobleyHost.Platform.current) {
        GobleyHost.Platform.Windows -> mingwX64(configure)
        GobleyHost.Platform.MacOS -> when (GobleyHost.Arch.current) {
            GobleyHost.Arch.X64 -> macosX64(configure)
            GobleyHost.Arch.Arm64 -> macosArm64(configure)
        }

        GobleyHost.Platform.Linux -> when (GobleyHost.Arch.current) {
            GobleyHost.Arch.X64 -> linuxX64(configure)
            GobleyHost.Arch.Arm64 -> linuxArm64(configure)
        }
    }
}

fun KotlinMultiplatformExtension.hostNativeTarget(
    name: String,
    configure: KotlinNativeTarget.() -> Unit = {},
): KotlinNativeTarget {
    return when (GobleyHost.Platform.current) {
        GobleyHost.Platform.Windows -> mingwX64(name, configure)
        GobleyHost.Platform.MacOS -> when (GobleyHost.Arch.current) {
            GobleyHost.Arch.X64 -> macosX64(name, configure)
            GobleyHost.Arch.Arm64 -> macosArm64(name, configure)
        }

        GobleyHost.Platform.Linux -> when (GobleyHost.Arch.current) {
            GobleyHost.Arch.X64 -> linuxX64(name, configure)
            GobleyHost.Arch.Arm64 -> linuxArm64(name, configure)
        }
    }
}

fun KotlinNativeCompilation.useRustUpLinker() {
    compileTaskProvider.configure {
        @OptIn(InternalGobleyGradleApi::class)
        PluginUtils.ensurePluginIsApplied(
            project,
            PluginUtils.PluginInfo(
                "Rust Kotlin Multiplatform",
                PluginIds.GOBLEY_RUST,
            ),
            PluginUtils.PluginInfo(
                "Cargo Kotlin Multiplatform",
                PluginIds.GOBLEY_CARGO,
            ),
        )
        val rustUpLinker = project.rustUpLinker(project.rustToolchainDirectory.get()).absolutePath
        val konanTarget = this@useRustUpLinker.target.konanTarget
        compilerOptions.freeCompilerArgs.add(
            "-Xoverride-konan-properties=linker.${GobleyHost.current.konanName}-${konanTarget.name}=$rustUpLinker"
        )
        if (GobleyHost.current.konanName == konanTarget.name) {
            compilerOptions.freeCompilerArgs.add(
                "-Xoverride-konan-properties=linker.${GobleyHost.current.konanName}=$rustUpLinker"
            )
        }
    }
}

private val Project.rustToolchainDirectory: Provider<File>
    get() = project.providers.provider {
        project.extensions.findByType<RustExtension>()?.toolchainDirectory?.get()
            ?: GobleyHost.current.platform.defaultToolchainDirectory
    }

@OptIn(InternalGobleyGradleApi::class)
private fun Project.rustUpLinker(toolchainDirectory: File): File {
    val rustUpHome = command("rustup") {
        arguments("show", "home")
        additionalEnvironmentPath(toolchainDirectory)
        captureStandardOutput()
    }.get().apply {
        assertNormalExitValue()
    }.standardOutput!!.trim()

    val activeToolchains = command("rustup") {
        arguments("show", "active-toolchain")
        additionalEnvironmentPath(toolchainDirectory)
        captureStandardOutput()
    }.get().apply {
        assertNormalExitValue()
    }.standardOutput!!.trim().split('\n')

    val toolchain = activeToolchains.firstNotNullOf {
        it.trim().split(' ').getOrNull(0)?.takeUnless(String::isEmpty)
    }
    return GobleyHost.current.platform.chooseExeExtension(
        File(rustUpHome).resolve("toolchains/$toolchain/lib/rustlib")
            .resolve(GobleyHost.current.rustTarget.rustTriple).resolve("bin/gcc-ld/ld.lld")
    )
}

val Project.rustVersion: Provider<String>
    get() = rustToolchainDirectory.map { toolchainDirectory ->
        @OptIn(InternalGobleyGradleApi::class)
        RustVersionUtils.getRustVersion(project, toolchainDirectory).toString()
    }
