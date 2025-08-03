import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("uniffi-tests-from-library")
}

uniffi {
    generateFromLibrary {
        packageName = "coverall"
    }
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
    // Not supported by io.kotest:kotest-assertions-core:5.9.1
    // @OptIn(ExperimentalWasmDsl::class)
    // wasmWasi {
    //     nodejs()
    // }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":tests:uniffi:coverall-pure-kotlin-dep"))
            }
        }
    }
}