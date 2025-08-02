import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.jvm
import gobley.gradle.uniffi.tasks.InstallUniffiBindgenTask

plugins {
    id("host-jvm-native-tests")
    id("dev.gobley.cargo")
    id("dev.gobley.uniffi")
}

cargo {
    builds.jvm {
        embedRustLibrary.set(rustTarget == GobleyHost.current.rustTarget)
    }
    wasmTransformerFromPath(
        rootProject.layout.projectDirectory.dir("crates/gobley-wasm-transformer")
    )
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/gobley-uniffi-bindgen"))
}

tasks.withType<InstallUniffiBindgenTask> {
    quiet = false
}
