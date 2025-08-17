# Changelog

## [Unreleased](https://github.com/gobley/gobley/compare/v0.3.2...HEAD)

## [0.3.2](https://github.com/gobley/gobley/releases/tag/v0.3.2) - 2025-08-17

### New Features

- Android NDK Kotlin/Native target support ([#177](https://github.com/gobley/gobley/pull/177)).

### Fixes

- Made `jarJvmRustRuntime` tasks use `jnaResourcePrefix` if `resourcePreix` is empty ([#178](https://github.com/gobley/gobley/pull/178)).
- Made `CargoBuildTask` print human-readable error messages ([#180](https://github.com/gobley/gobley/pull/180)).
- Made `CargoBuildTask` replace backslashes in paths with slashes when rendering `nativeStaticLibsDefFile` on Windows ([#183](https://github.com/gobley/gobley/pull/183)).

### Dependencies

- Deleted the dependency on Okio ([#176](https://github.com/gobley/gobley/pull/176)).

## [0.3.1](https://github.com/gobley/gobley/releases/tag/v0.3.1) - 2025-08-10

### Fixes

- Prioritized shared libraries over static libraries when generating UniFFI bindings ([#165](https://github.com/gobley/gobley/pull/165)).
- Explicit visibility modifier in UniFFI bindings ([#169](https://github.com/gobley/gobley/pull/169)).

### Dependencies

- Upgraded UniFFI from 0.29.3 to 0.29.4 ([#167](https://github.com/gobley/gobley/pull/167)).

## [0.3.0](https://github.com/gobley/gobley/releases/tag/v0.3.0) - 2025-08-04

### New Features

- `CargoCheckTask.extraArguments` ([#110](https://github.com/gobley/gobley/pull/110)).
- `CargoExtension.installTargetBeforeBuild` to prevent `RustUpTargetAddTasks` from running ([#136](https://github.com/gobley/gobley/pull/136)).
- ProGuard rule file generation for UniFFI ([#140](https://github.com/gobley/gobley/pull/140)).
- UniFFI 0.29.3 support ([#143](https://github.com/gobley/gobley/pull/143) & [#147](https://github.com/gobley/gobley/pull/147)).
- Bindgen option to bypass checksum checks ([#144](https://github.com/gobley/gobley/pull/144)).
- WASM embedding into Kotlin/JS ([#151](https://github.com/gobley/gobley/pull/151)).

### Fixes

- Fixed the build script library search path parsing logic using `--message-format json` ([#113](https://github.com/gobley/gobley/pull/113)).
- Fixed MinGW DLL files not being included in the JAR files ([#137](https://github.com/gobley/gobley/pull/137)).
- Implemented workaround for `MethodTooLargeException` thrown from JNA ([#145](https://github.com/gobley/gobley/pull/145)).
- Implemented proper handling of artifact resolution ([#158](https://github.com/gobley/gobley/pull/158)).
- Fixed the Android API level conflict due to redundant C/CXX environment variables ([#159](https://github.com/gobley/gobley/pull/159)).

## [0.2.0](https://github.com/gobley/gobley/releases/tag/v0.2.0) - 2025-04-06

### Breaking Changes

- Separate artifacts for JVM runtime Rust dynamic
  libraries ([#81](https://github.com/gobley/gobley/pull/81) & [#102](https://github.com/gobley/gobley/pull/102)).

### New Features

- Stub generation for unsupported JS/WASM targets ([#29](https://github.com/gobley/gobley/pull/29)).
- Kotlin/Native tvOS & watchOS support ([#35](https://github.com/gobley/gobley/pull/35)).
- Kotlin JVM and Kotlin Android Gradle plugins
  support ([#44](https://github.com/gobley/gobley/pull/44), [#47](https://github.com/gobley/gobley/pull/47), [#87](https://github.com/gobley/gobley/pull/87), [#94](https://github.com/gobley/gobley/pull/94), & [#97](https://github.com/gobley/gobley/pull/97)).
- Config option for using PascalCase for enums ([#54](https://github.com/gobley/gobley/pull/54)).
- `CargoCheckTask` for cross-platform linting ([#55](https://github.com/gobley/gobley/pull/55)).
- `UniFfiPlugin` can be used without a UniFFI config
  file ([#63](https://github.com/gobley/gobley/pull/63)).
- UniFFI bindings generation during IDE sync ([#101](https://github.com/gobley/gobley/pull/101)).

### Fixes

- Ensured interface instances in lists and maps are
  destroyed ([#53](https://github.com/gobley/gobley/pull/53)).
- Prevented `UniFfiPlugin` from selecting a build excluded by
  `embedRustLibrary` ([#64](https://github.com/gobley/gobley/pull/64)).
- Prevented NPE occurring when functions returning RustArcPtr throw
  errors ([#75](https://github.com/gobley/gobley/pull/75)).

## Dependencies

- Upgraded JNA from 5.16.0 to 5.17.0 ([#65](https://github.com/gobley/gobley/pull/65)).
- Kotlin library version requirements are eased
  off ([#89](https://github.com/gobley/gobley/pull/89)).

## [0.1.0](https://github.com/gobley/gobley/releases/tag/v0.1.0) - 2025-03-03

### New Features

- Support for `uniffi@0.28.3`.
- Support for Kotlin 2.x.
- Gradle plugins for cross-compilation automation.
  - Android
  - JVM
  - Kotlin/Native (MinGW, macOS, Linux, & iOS)

## [UniFFI Kotlin Multiplatform Bindings 0.1.0](https://gitlab.com/trixnity/uniffi-kotlin-multiplatform-bindings/-/tags/v0.1.0) - 2023-11-25

### Added

- Support for `uniffi@0.25.2`
