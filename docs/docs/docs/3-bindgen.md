---
slug: /bindgen
---

# The Bindgen

The bindings generator (the "bindgen") is the program that generates Kotlin source codes connecting
your Kotlin code to your Rust code. In most cases, [the UniFFI Gradle plugin](#the-uniffi-plugin)
handles the bindings generation, so you don't have to know all the details of the bindgen. Still,
you can directly use this bindgen if you have more complicated build system.

The minimum Rust version required to install `gobley-uniffi-bindgen` is `1.72`. Newer Rust versions
should also work fine. The source code of the bindgen for Kotlin Multiplatform is in [
`bindgen`](https://github.com/gobley/gobley/tree/main/bindgen).
See comments in [
`bindgen/src/main.rs`](https://github.com/gobley/gobley/tree/main/bindgen/src/main.rs) or
[
`BuildBindingsTask.kt`](https://github.com/gobley/gobley/tree/main/build-logic/gobley-gradle-uniffi/src/main/kotlin/tasks/BuildBindingsTask.kt)
to see how to use the bindgen from the command line.

To install the bindgen, run:

```shell
cargo install --bin gobley-uniffi-bindgen gobley-uniffi-bindgen@0.1.0
```

to invoke the bindgen, run:

```shell
gobley-uniffi-bindgen --lib-file <path-to-library-file> --out-dir <output-directory> --crate <crate-name> <path-to-udl-file>
```

If you want to use the bindgen in your own Crago build script, please read the
["Generating foreign-language bindings" part](https://mozilla.github.io/uniffi-rs/tutorial/foreign_language_bindings.html)
in the official UniFFI documentation.

When the bindings are generated correctly, it has a directory structure like the following.

```
<output directory>
├── androidMain
│   └── kotlin
│       └── <namespace name>
│           └── <namespace name>.android.kt
├── commonMain
│   └── kotlin
│       └── <namespace name>
│           └── <namespace name>.common.kt
├── jvmMain
│   └── kotlin
│       └── <namespace name>
│           └── <namespace name>.jvm.kt
├── nativeInterop
│   └── headers
│       └── <namespace name>
│           └── <namespace name>.h
├── nativeMain
│   └── kotlin
│       └── <namespace name>
│           └── <namespace name>.native.kt
└── stubMain
    └── kotlin
        └── <namespace name>
            └── <namespace name>.stub.kt
```

## Bindgen configuration

Various settings used by the bindgen can be configured in `<manifest dir>/uniffi.toml`, or the
`uniffi {}` block in Gradle scripts. To learn more about configuring UniFFI settings using Gradle,
see [Configuring Bindgen settings using Gradle DSL](./2-gradle-plugins/2-uniffi-plugin.md#configuring-bindgen-settings-using-gradle-dsl).
For more details about each configuration, see [
`bindgen/src/gen_kotlin_multiplatform/mod.rs`](https://github.com/gobley/gobley/tree/main/bindgen/src/gen_kotlin_multiplatform/mod.rs)
or [
`Config.kt`](https://github.com/gobley/gobley/tree/main/build-logic/gobley-gradle-uniffi/src/main/kotlin/Config.kt).

| Configuration Name                     | Type         | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
|----------------------------------------|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `package_name`                         | String       | The Kotlin package name to use. Defaults to `uniffi.<namespace name>`.                                                                                                                                                                                                                                                                                                                                                                                           |
| `cdylib_name`                          | String       | The name of the resulting dynamic library without the prefix (e.g. `lib`) and the file extension. When the bindings are generated from a dynamic library, the value of this property defaults to the library's name. When a static library or a UDL file is used, it is set to `uniffi_<namespace>`. When the `crate-type` field of the Cargo manifest contains `"cdylib"`, the UniFFI plugin will give priority to the dynamic library over the static library. |
| `kotlin_multiplatform`                 | Boolean      | When `false`, expect/actual declarations are not used.                                                                                                                                                                                                                                                                                                                                                                                                           |
| `kotlin_targets`                       | String Array | The list of names of Kotlin targets of the bindings to generate. Possible values are: `jvm`, `android`, `native`, and `stub`.                                                                                                                                                                                                                                                                                                                                    |
| `generate_immutable_records`           | Boolean      | When `true`, generated data classes has `val` properties instead of `var`.                                                                                                                                                                                                                                                                                                                                                                                       |
| `omit_checksums`                       | Boolean      | When `true`, the library checksums are not checked during initialization, making the process slightly faster. This may be problematic if there is a mismatch between libraries used during binding generation and runtime.                                                                                                                                                                                                                                       |
| `custom_types`                         |              | See [the documentation](https://mozilla.github.io/uniffi-rs/0.29/types/custom_types.html#custom-types-in-the-bindings-code)                                                                                                                                                                                                                                                                                                                                      |
| `kotlin_target_version`                | String       | The Kotlin version used by your project. Newer syntax will be used (e.g. `data object` or `Enum.entries`) when the compiler of the specified version supports. This is automatically set to the Kotlin Gradle plugin version by the UniFFI Gradle plugin.                                                                                                                                                                                                        |
| `disable_java_cleaner`                 | Boolean      | When `true`, `com.sun.jna.internal.Cleaner` will be used instead of `android.system.SystemCleaner` or `java.lang.ref.Cleaner`. Defaults to `false`. Consider changing this option when your project targets JVM 1.8.                                                                                                                                                                                                                                             |
| `generate_serializable_types`          | Boolean      | When `true`, data classes will be annotated with `@kotlinx.serialization.Serializable` when possible. This is automatically set to `true` by the UniFFI Gradle plugin when your Kotlin project uses KotlinX Serialization.                                                                                                                                                                                                                                       |
| `use_pascal_case_enum_class`           | Boolean      | When `true`, enum classes will use PascalCase instead of UPPER_SNAKE_CASE.                                                                                                                                                                                                                                                                                                                                                                                       |
| `jvm_dynamic_library_dependencies`     | String Array | The list of dynamic libraries required by your Rust library on Desktop JVM targets without the prefix and the file extension. Use this if your project depends on an external dynamic library.                                                                                                                                                                                                                                                                   |
| `android_dynamic_library_dependencies` | String Array | The list of dynamic libraries required by your Rust library on Android without the prefix and the file extension.                                                                                                                                                                                                                                                                                                                                                |
| `dynamic_library_dependencies`         | String Array | The list of dynamic libraries required by your Rust library on both Desktop JVM targets and Android targets.                                                                                                                                                                                                                                                                                                                                                     |

## Versioning

The Gobley bindgen is versioned separately from UniFFI. UniFFI follows the
[SemVer rules from the Cargo Book](https://doc.rust-lang.org/cargo/reference/resolver.html#semver-compatibility)
which states "Versions are considered compatible if their left-most non-zero major/minor/patch
component is the same". A breaking change is any modification to the Kotlin Multiplatform bindings
that demands the consumer of the bindings to make corresponding changes to their code to ensure that
the bindings continue to function properly. `gobley-uniffi-bindgen` is young, and it's
unclear how stable the generated bindings are going to be between versions. For this reason, major
version is currently 0, and most changes are probably going to bump minor version.

To ensure consistent feature set across external binding generators, `gobley-uniffi-bindgen`
targets a specific `uniffi-rs` version. A consumer using these bindings or any other external
bindings (for example, [Go bindings](https://github.com/NordSecurity/uniffi-bindgen-go/) or
[C# bindings](https://github.com/NordSecurity/uniffi-bindgen-cs)) expects the same features to be
available across multiple bindings generators. This means that the consumer should choose external
binding generator versions such that each generator targets the same `uniffi-rs` version.

Here is how `gobley-uniffi-bindgen` versions are tied to `uniffi-rs` are tied:

| Gobley version | UniFFI version |
|----------------|----------------|
| v0.1.0         | v0.28.3        |
| v0.2.0         | v0.28.3        |
| v0.3.0         | v0.29.3        |
| v0.3.1         | v0.29.4        |
| v0.3.2         | v0.29.4        |
