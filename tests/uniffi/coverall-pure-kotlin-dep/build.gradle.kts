// Tests whether projects with Gobley plugins can depend on other pure-Kotlin projects
// without the Rust plugin.

import gobley.gradle.GobleyHost
import gobley.gradle.rust.dsl.hostNativeTarget
import gobley.gradle.rust.dsl.useRustUpLinker
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    // Don't apply the Rust plugin; this is to use GobleyHost below.
    id("dev.gobley.rust") apply false
}

kotlin {
    jvmToolchain(17)
    jvm()
    hostNativeTarget {
        if (GobleyHost.Platform.Windows.isCurrent) {
            compilations.getByName("test") {
                useRustUpLinker()
            }
        }
    }
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