import gobley.gradle.cargo.dsl.wasm

plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo")
}

cargo {
    builds.wasm {
        variants {
            transformWasmProvider.configure {
                functionImportsFile = projectLayout.projectDirectory.file("function-imports.txt")
            }
        }
    }
    wasmTransformerFromPath(
        rootProject.layout.projectDirectory.dir("crates/gobley-wasm-transformer")
    )
}

kotlin {
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
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}