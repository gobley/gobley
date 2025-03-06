import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("uniffi-tests-from-library")
}

kotlin {
    js {
        nodejs()
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        browser()
    }
    // Not supported by io.kotest:kotest-assertions-core:5.9.1
    // @OptIn(ExperimentalWasmDsl::class)
    // wasmWasi {
    //     nodejs()
    // }
}