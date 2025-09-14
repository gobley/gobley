---
slug: /gradle-plugins/rust
---

# The Rust plugin

## Configuring the Rust toolchain and the linker to use

The Rust plugin is for configuring the Rust toolchain you want to use or linking your Rust library
to your Kotlin project. By default, the plugins think `cargo` and `rustup` are installed in
`~/.cargo/bin` or a directory registered in the `PATH` environment variable, which is okay for
almost everyone.

However, if you have installed `cargo` or `rustup` in another directory, you can provide that
information to the plugin via the `rust {}` block. The information in the `rust {}` block is
automatically passed to the Cargo or the UniFFI plugins.

```kotlin
plugins {
    id("dev.gobley.rust") version "0.3.5"
}

rust {
    toolchainDirectory = File("/path/to/my/Rust/toolchain")
}
```

The Rust plugin also defines two extension functions `KotlinMultiplatformExtension.hostNativeTarget`
and `KotlinNativeCompilation.useRustUpLinker` and one extension property `Project.rustVersion`.

`hostNativeTarget` can be invoked in `kotlin {}` and adds the Kotlin Native target for the build
host; it invokes `mingwX64` on Windows, `macosX64` or `macosArm64` on macOS, and `linuxX64` or
`linuxArm64` on Linux, though Linux Arm64 build host is not supported by Kotlin/Native yet.

```kotlin
import gobley.gradle.rust.dsl.*

kotlin {
    hostNativeTarget()
}
```

`useRustUpLinker` is for Kotlin Native projects referencing a Rust library but not directly using
Rust. Since Kotlin Native is shipped with an LLVM older than the one shipped with the Rust
toolchain, you may encounter a linker error when building that Kotlin Native project.
`useRustUpLinker` automatically finds the LLVM linker distributed with `rustup`, so you can use this
when your Rust project emits a linker flag that is not supported by the Kotlin Native LLVM linker.

```kotlin
import gobley.gradle.rust.dsl.*

kotlin {
    iosArm64().compilations.getByName("main") {
        useRustUpLinker()
    }
}
```

`rustVersion` retrieves the current Rust version via `rustc --version`.

```kotlin
import gobley.gradle.rust.dsl.*

println(rustVersion.get()) // e.g. 1.81.0
```

## Use the Rust plugin to configure multi-project builds properly

To align with modern
Gradle's [isolated project](https://docs.gradle.org/8.13/userguide/isolated_projects.html)
feature, Gobley
uses [Gradle configurations](https://docs.gradle.org/8.13/userguide/declaring_configurations.html)
to propagate dynamic libraries built with Cargo for JVM targets and UniFFI configurations
for [external types](https://mozilla.github.io/uniffi-rs/0.29/types/remote_ext_types.html). When a project
that uses the Cargo or the UniFFI plugins are referenced by another project, the Rust plugin should
be also applied to that project. If you don't apply the Rust plugin, you might encounter a runtime
error during Android local unit tests. Simply apply the Rust plugin to resolve these issues.

```kotlin
plugins {
    id("dev.gobley.rust") version "0.3.5"
}
```