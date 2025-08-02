import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.jvm
import gobley.gradle.cargo.dsl.native
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("host-jvm-native-tests")
    id("dev.gobley.cargo")
}

cargo {
    builds.jvm {
        embedRustLibrary = (rustTarget == GobleyHost.current.rustTarget)
        resourcePrefix = "jvm"
    }
    wasmTransformerFromPath(
        rootProject.layout.projectDirectory.dir("crates/gobley-wasm-transformer")
    )
}

kotlin.targets.withType(KotlinNativeTarget::class) {
    val kotlinTarget = this
    compilations.getByName("main") {
        cinterops.register("rustBindings") {
            includeDirs(layout.buildDirectory.dir("generated"))
            tasks.named(interopProcessingTaskName) {
                cargo.builds.native.configureEach {
                    if (kotlinTargets.contains(kotlinTarget)) {
                        dependsOn(variant(nativeVariant.get()).buildTaskProvider)
                    }
                }
            }
        }
    }
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }
}
