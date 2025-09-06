// Tests whether projects with Gobley plugins can depend on other pure-Kotlin projects
// without the Rust plugin.

import gobley.gradle.rust.dsl.hostNativeTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    // Don't apply the Rust plugin; this is to use hostNativeTarget() below.
    id("dev.gobley.rust") apply false
}

kotlin {
    explicitApi()
    jvmToolchain(17)
    jvm()
    hostNativeTarget()
    js {
        nodejs()
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }
}