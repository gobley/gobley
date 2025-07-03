---
slug: /
---

# Overview

We recommend to first read the [UniFFI user guide](https://mozilla.github.io/uniffi-rs/). Then, please read this documentation in the following order.

- [Development environment and common development practices](#development-environment-and-common-development-practices)
- [Using the Gradle plugin](#using-the-gradle-plugin)
- [The Bindgen](#the-bindgen)
- [Bindgen configuration](#bindgen-configuration)
- [Cross-compilation tips](#cross-compilation-tips)

## Development environment and common development practices

There are only one option to code both in Kotlin and Rust in a single IDE: IntelliJ IDEA Ultimate, which needs a paid
subscription. Fleet was another option, but JetBrains announced that
[they are dropping the KMP support in Fleet](https://blog.jetbrains.com/kotlin/2025/02/kotlin-multiplatform-tooling-shifting-gears/).

Therefore, most users may use different IDEs for Kotlin and Rust when developing with this project. For Kotlin, you
can use Android Studio or IntelliJ IDEA, and for Rust, you can use `rust-analyzer` with Visual Studio Code or RustRover.
In normal cases, Kotlin handles the part interacting with users such as UI while Rust handles the core business logic,
so using two IDEs won't harm the developer experience that much.

Since building Rust takes much more time than compiling Kotlin, try separating the Kotlin part that uses Rust directly as a
core library. You can build and publish the core library using the `maven-publish` plugin and the other Kotlin part can
download it from a maven repository.

The more platforms you target, the larger the build result will be. Ensure your CI has enough space to build your project.
Gradle caches files from the build in `~/.gradle/caches`. If you encounter much more `No space left on device` errors after
using this project, try removing `~/.gradle/caches`. Since Gradle still tries to find cached files in `~/.gradle/caches`
after you remove it, remove all `.gradle` and `build` directories in your project as well. On macOS & Linux:

```
find . -name .gradle | xargs rm -rf
find . -name "build" | grep -v '^./target' | xargs -r rm -rf
```

In PowerShell on Windows:

```powershell
Get-ChildItem . -Attributes Directory -Recurse |
    Where-Object { $_.Name -eq ".gradle" } |
    ForEach-Object { Remove-Item -Recurse -Force $_ }
Get-ChildItem . -Attributes Directory -Recurse |
    Where-Object { $_.Name -eq "build" } |
    Where-Object { -not $_.FullName.Contains("\target\") } |
    ForEach-Object { Remove-Item -Recurse -Force $_ }
```

When you build iOS apps, Xcode generates files in `/private/var/folders/zz`, which are removed automatically after every
reboot. Try restart your Mac if you still have the disk space issue after removing the Gradle caches.

## Using the Gradle plugin

This project contains three Gradle plugins:

- The Cargo plugin (`dev.gobley.cargo`)
- The UniFFI plugin (`dev.gobley.uniffi`)
- The Rust plugin (`dev.gobley.rust`)

### The Cargo plugin

The Cargo plugin is responsible for building and linking the Rust library to your Kotlin project. You can use it even
when you are not using UniFFI. If the `Cargo.toml` is located in the project root, you can simply apply the
`dev.gobley.cargo` the plugin.

```kotlin
plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo") version "0.1.0"
}
```

If the Cargo package is located in another directory, you can configure the path in the `cargo {}` block.

```kotlin
cargo {
    // The Cargo package is located in a `rust` subdirectory.
    packageDirectory = layout.projectDirectory.dir("rust")
}
```

Since searching `Cargo.toml` is done
by [`cargo locate-project`](https://doc.rust-lang.org/cargo/commands/cargo-locate-project.html),
it still works even if you set `packageDirectory` to a subdirectory, but it is not recommended.

```kotlin
cargo {
    // This works
    packageDirectory = layout.projectDirectory.dir("rust/src")
}
```

If you want to use Cargo features or
customized [Cargo profiles](https://doc.rust-lang.org/cargo/reference/profiles.html),
you can configure them in the `cargo {}` block as well.

```kotlin
import gobley.gradle.cargo.profiles.CargoProfile

cargo {
    features.addAll("foo", "bar")
    debug.profile = CargoProfile("my-debug")
    release.profile = CargoProfile.Bench
}
```

If you want to use different features for each variant (debug or release), you can configure them in the `debug {}` or
`release {}` blocks.

```kotlin
cargo {
    features.addAll("foo")
    debug {
        // Use "foo", "logging" for debug builds
        features.addAll("logging")
    }
    release {
        // Use "foo", "app-integrity-checks" for release builds
        features.addAll("app-integrity-checks")
    }
}
```

`features` are inherited from the outer block to the inner block. To override this behavior in the inner block,
use `.set()` or the `=` operator overloading.

```kotlin
cargo {
    features.addAll("foo")
    debug {
        // Use "foo", "logging" for debug builds
        features.addAll("logging")
    }
    release {
        // Use "app-integrity-checks" (not "foo"!) for release builds
        features.set(setOf("app-integrity-checks"))
    }
}
```

For configurations applied to all variants, you can use the `variants {}` block.

```kotlin
cargo {
    variants {
        features.addAll("another-feature")
    }
}
```

For Android and Apple platform builds invoked by Xcode, the plugin automatically decides which profile to use. For other
targets, you can configure it with the `jvmVariant` or `nativeVariant` properties. When undecidable, these values
default to `Variant.Debug`.

```kotlin
import gobley.gradle.Variant

cargo {
    jvmVariant = Variant.Release
    nativeVariant = Variant.Debug
}
```

Cargo build tasks are configured as the corresponding Kotlin target is added in the `kotlin {}` block. For example, if
you don't invoke `androidTarget()` in `kotlin {}`, the Cargo plugin won't configure the Android build task as well.

```kotlin
cargo {
    builds.android {
        println("foo") // not executed
    }
}

kotlin {
    // The plugin will react to the targets definition
    jvm()
    linuxX64()
}
```

The Cargo plugin scans all the Rust dependencies
using [`cargo metadata`](https://doc.rust-lang.org/cargo/commands/cargo-metadata.html). If you modify Rust source files
including those in dependencies defined in the Cargo manifest, the Cargo plugin will rebuild the Cargo project.

For Android builds, the Cargo plugin automatically determines the SDK and the NDK to use based on the property values of
the `android {}` block. To use different a NDK version, set `ndkVersion` to that version.

```kotlin
android {
    ndkVersion = "26.2.11394342"
}
```

The Cargo plugin also automatically determines the ABI to build based on the value
of `android.defaultConfig.ndk.abiFilters`. If you don't want to build for x86 or x86_64, set this
to `["arm64-v8a", "armeabi-v7a"]`.

```kotlin
android {
    defaultConfig {
        ndk.abiFilters += setOf("arm64-v8a", "armeabi-v7a")
    }
}
```

The Cargo plugin automatically configures environment variables like `ANDROID_HOME` or `CC_<target>` for you, but if you
need finer control, you can directly configure the properties of the build task. The build task is accessible in the
`builds {}` block.

```kotlin
import gobley.gradle.cargo.dsl.*

cargo {
    builds {
        // Configure Android builds
        android {
            debug.buildTaskProvider.configure {
                additionalEnvironment.put("CLANG", "/path/to/clang")
            }
        }
        // You can configure for other targets as well
        appleMobile {}
        desktop {}
        jvm {}
        mobile {}
        native {}
        posix {}
        mingw {}
        linux {}
        macos {}
        windows {}
    }
}
```

For JVM builds, the Cargo plugin tries to build all the targets, whether the required toolchains are installed on the
current system or not. The list of such targets by the build host is as follows.

| Targets      | Windows | macOS | Linux |
| ------------ | ------- | ----- | ----- |
| Android      | ✅      | ✅    | ✅    |
| Apple Mobile | ❌      | ✅    | ❌    |
| MinGW        | ✅      | ✅    | ✅    |
| macOS        | ❌      | ✅    | ❌    |
| Linux        | ✅      | ✅    | ✅    |
| Visual C++   | ✅      | ❌    | ❌    |

To build for specific targets only, you can configure that using the `embedRustLibrary` property. For example, to
build a shared library for the current build host only, set this property to
`rustTarget == GobleyHost.current.rustTarget`.

```kotlin
import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.*

cargo {
    builds.jvm {
        embedRustLibrary = (rustTarget == GobleyHost.current.rustTarget)
    }
}
```

On Windows, both MinGW and Visual C++ can generate DLLs. By default, the Cargo plugin doesn't invoke the MinGW build
for JVM when Visual C++ is available. To override this behavior, use the `embedRustLibrary` property like the
following. Note that Windows on ARM is not available with MinGW.

```kotlin
import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.*
import gobley.gradle.rust.targets.RustWindowsTarget

cargo {
    builds.jvm {
        if (GobleyHost.Platform.Windows.isCurrent) {
            when (rustTarget) {
                RustWindowsTarget.X64 -> embedRustLibrary = false
                RustPosixTarget.MinGWX64 -> embedRustLibrary = true
                else -> {}
            }
        }
    }
}
```

`embedRustLibrary` is also used when you use [the external types feature](https://mozilla.github.io/uniffi-rs/udl/ext_types.html)
in your project. Rust statically links all the crates unless you specify the library crate's kind as `dylib`. So, the
final Kotlin library does not have to include shared libraries built from every crate. Suppose you have two crates, `foo`, and
`bar`, where `foo` exposes the external types and `bar` uses types in `foo`. Since when building `bar.dll`, `libbar.dylib`, or
`libbar.so`, the `foo` crate is also included in `bar`, you don't have to put `foo.dll`, `libfoo.dylib`, or `libfoo.so`
inside your Kotlin library. So, to configure that, put the followings in `foo/build.gradle.kts`:

```
cargo {
    builds.android {
        embedRustLibrary = false
    }
    builds.jvm {
        embedRustLibrary = false
    }
}
```

and in `foo/uniffi.toml`:

```
# The cdylib_name used in `bar/uniffi.toml`
cdylib_name = "bar"
```

The JVM `loadIndirect()` function in the bindings allow users to override the `cdylib_name` value using the
`uniffi.component.<namespace name>.libraryOverride` system property as well. See the
[`:tests:uniffi:ext-types:ext-types`](https://github.com/gobley/gobley/tree/v0.1.0/tests/uniffi/ext-types/ext-types)
test to see how this works.

Android local unit tests requires JVM targets to be built, as they run in the host machine's JVM. The Cargo plugin
automatically copies the Rust shared library targeting the host machine into Android local unit tests. It also finds
projects that depend on the project using the Cargo plugin, and the Rust library will be copied to all projects that
directly or indirectly use the Cargo project. If you want to include shared library built for a different platform, you
can control that using the `androidUnitTest` property.

```kotlin
import gobley.gradle.cargo.dsl.*
import gobley.gradle.rust.targets.RustWindowsTarget

cargo {
    builds.jvm {
        // Use Visual C++ X64 for Android local unit tests
        androidUnitTest = (rustTarget == RustWindowsTarget.X64)
    }
}

kotlin {
    jvm()
    androidTarget()
}
```

Local unit tests are successfully built even if there are no builds with `androidUnitTest` enabled, but you will
encounter a runtime error when you invoke a Rust function from Kotlin.

When you build or publish your Rust Android library separately and run Android local unit tests in another build, you
also have to reference the JVM version of your library from the Android unit tests.

To build the JVM version, run the `<JVM target name>Jar` task. The name of the JVM target can be configured with the
`jvm()` function, which defaults to `"jvm"`. For example, when the name of the JVM target is `"desktop"`:

```kotlin
kotlin {
    jvm("desktop")
}
```

the name of the task will be `desktopJar`.

```shell
# ./gradlew :your:library:<JVM target name>Jar
./gradlew :your:library:desktopJar
```

The build output will be located in `build/libs/<project name>-<JVM target name>.jar`. In the above case, the name of
the JAR file will be `<project name>-desktop.jar`. The JAR file then can be referenced using the `files` or the
`fileTree` functions.

```kotlin
kotlin {
    sourceSets {
        getByName("androidUnitTest") {
            dependencies {
                // implementation(files("<project name>-<JVM target name>.jar"))
                implementation(files("library-desktop.jar"))
                implementation("net.java.dev.jna:jna:5.13.0") // required to run
            }
        }
    }
}
```

The above process can be automated using the `maven-publish` Gradle plugin. It publishes the JVM version of your library
separately. For more details about using `maven-publish` with Kotlin Multiplatform, please refer
[here](https://kotlinlang.org/docs/multiplatform-publish-lib.html).

To publish your library to the local Maven repository on your system, run the `publishToMavenLocal` task.

```shell
./gradlew :your:project:publishToMavenLocal
```

In the local repository which is located in `~/.m2`, you will see that multiple artifacts including `<project name>` and
`<project name>-<JVM target name>` are generated. To reference it, register the `mavenLocal()` repository and put the
artifact name to `implementation()`.

```kotlin
repositories {
    mavenLocal()
    // ...
}

kotlin {
    sourceSets {
        getByName("androidUnitTest") {
            dependencies {
                // implementation("<group name>:<project name>-<JVM target name>:<version>")
                implementation("your.library:library-desktop:0.1.0")
                implementation("net.java.dev.jna:jna:5.13.0") // required to run
            }
        }
    }
}
```

If your Rust library is dependent on other shared libraries, you have to ensure that they are also available during
runtime. For JVM and Android builds, you can use the `dynamicLibraries` and the `dynamicLibrarySearchPaths` properties.
The specified libraries will be embedded into the resulting JAR or the Android bundle.

```
cargo {
    builds.android {
        // Copies libaaudio.so and libc++_shared.so from NDK
        dynamicLibraries.addAll("aaudio", "c++_shared")
    }
    builds.jvm {
        // Copies libmyaudio.so or myaudio.dll
        dynamicLibraries.addAll("myaudio")
    }
}
```

Some directories like the NDK installation directory or the Cargo build output directory are already registered in
`dynamicLibrarySearchPaths`. If your build system uses another directory, add that to this property.

### The UniFFI plugin

The UniFFI plugin is responsible for generating Kotlin bindings from your Rust package. Here is an example of using the
UniFFI plugin to build bindings from the resulting library binary.

```kotlin
import gobley.gradle.Variant
import gobley.gradle.rust.targets.RustAndroidTarget

plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo") version "0.1.0"
    id("dev.gobley.uniffi") version "0.1.0"
}

uniffi {
    // Generate the bindings using library mode.
    generateFromLibrary {
        // The UDL namespace as in the UDL file. Defaults to the library crate name.
        namespace = "my_crate"
        // The Rust target of the build to use to generate the bindings. If unspecified, one of the available builds
        // will be automatically selected.
        build = RustAndroidTarget.Arm64
        // The variant of the build that makes the library to use. If unspecified, the UniFFI plugin automatically picks
        // one.
        variant = Variant.Debug
    }
}
```

If you want to generate bindings from a UDL file as well, you can specify the path using the `generateFromUdl {}` block.

```kotlin
uniffi {
    generateFromUdl {
        namespace = "..."
        build = ...
        variant = Variant.Debug
        // The UDL file. Defaults to "${crateDirectory}/src/${crateName}.udl".
        udlFile = layout.projectDirectory.file("rust/src/my_crate.udl")
    }
}
```

If you want to run `ktlint` on the generated bindings set `formatCode` to `true`.

```kotlin
uniffi {
    formatCode = true
}
```

### The Rust plugin

The Rust plugin is for configuring the Rust toolchain you want to use or linking your Rust library to your Kotlin
project. By default, the plugins think `cargo` and `rustup` are installed in `~/.cargo/bin` or a directory registered in
the `PATH` environment variable, which is okay for almost everyone.

However, if you have installed `cargo` or `rustup` in another directory, you can provide that information to the plugin
via the `rust {}` block. The information in the `rust {}` block is automatically passed to the Cargo or the UniFFI
plugins.

```kotlin
plugins {
    id("dev.gobley.rust") version "0.1.0"
}

rust {
    toolchainDirectory = File("/path/to/my/Rust/toolchain")
}
```

The Rust plugin also defines two extension functions `KotlinMultiplatformExtension.hostNativeTarget`
and `KotlinNativeCompilation.useRustUpLinker`.

`hostNativeTarget` can be invoked in `kotlin {}` and adds the Kotlin Native target for the build host; it invokes
`mingwX64` on Windows, `macosX64` or `macosArm64` on macOS, and `linuxX64` or `linuxArm64` on Linux, though Linux Arm64
build host is not supported yet.

```kotlin
import gobley.gradle.rust.dsl.*

kotlin {
    hostNativeTarget()
}
```

`useRustUpLinker` is for Kotlin Native projects referencing a Rust library but not directly using Rust. Since Kotlin
Native is shipped with an LLVM older than the one shipped with the Rust toolchain, you may encounter a linker error
when building that Kotlin Native project. `useRustUpLinker` automatically finds the LLVM linker distributed
with `rustup`, so you can use this when your Rust project emits a linker flag that is not supported by the Kotlin Native
LLVM linker.

```kotlin
import gobley.gradle.rust.dsl.*

kotlin {
    iosArm64().compilations.getByName("main") {
        useRustUpLinker()
    }
}
```

## The Bindgen

The bindings generator (the "bindgen") is the program that generates Kotlin source codes connecting your Kotlin code
to your Rust code. In most cases, [the UniFFI Gradle plugin](#the-uniffi-plugin) handles the bindings generation, so you
don't have to know all the details of the bindgen. Still, you can directly use this bindgen if you have more
complicated build system.

The minimum Rust version required to install `gobley-uniffi-bindgen` is `1.72`. Newer Rust versions should
also work fine. The source code of the bindgen for Kotlin Multiplatform is in
[`bindgen`](https://github.com/gobley/gobley/tree/v0.1.0/bindgen). See comments in
[`bindgen/src/main.rs`](https://github.com/gobley/gobley/tree/v0.1.0/bindgen/src/main.rs) or
[`BuildBindingsTask.kt`](https://github.com/gobley/gobley/tree/v0.1.0/build-logic/gradle-plugin/src/main/kotlin/io/gitlab/trixnity/gradle/uniffi/tasks/BuildBindingsTask.kt)
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
└── nativeMain
    └── kotlin
        └── <namespace name>
            └── <namespace name>.native.kt
```

### Bindgen configuration

Various settings used by the bindgen can be configured in `<manifest dir>/uniffi.toml`. For more
details, see [`bindgen/src/gen_kotlin_multiplatform/mod.rs`](https://github.com/gobley/gobley/tree/v0.1.0/bindgen/src/gen_kotlin_multiplatform/mod.rs)
or [`Config.kt`](https://github.com/gobley/gobley/tree/v0.1.0/build-logic/gradle-plugin/src/main/kotlin/io/gitlab/trixnity/gradle/uniffi/Config.kt).

| Configuration Name                     | Type         | Description                                                                                                                                                                                                                                               |
| -------------------------------------- | ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `package_name`                         | String       | The Kotlin package name to use. Defaults to `uniffi.<namespace name>`.                                                                                                                                                                                    |
| `cdylib_name`                          | String       | The name of the resulting dynamic library without the prefix (e.g. `lib`) and the file extension. Defaults to the library's name when bindings are generated from it, or `uniffi_<namespace>` when generated from a UDL file.                             |
| `generate_immutable_records`           | Boolean      | When `true`, generated data classes has `val` properties instead of `var`.                                                                                                                                                                                |
| `custom_types`                         |              | See [the documentation](https://mozilla.github.io/uniffi-rs/0.28/udl/custom_types.html#custom-types-in-the-bindings-code)                                                                                                                                 |
| `kotlin_target_version`                | String       | The Kotlin version used by your project. Newer syntax will be used (e.g. `data object` or `Enum.entries`) when the compiler of the specified version supports. This is automatically set to the Kotlin Gradle plugin version by the UniFFI Gradle plugin. |
| `disable_java_cleaner`                 | Boolean      | When `true`, `com.sun.jna.internal.Cleaner` will be used instead of `android.system.SystemCleaner` or `java.lang.ref.Cleaner`. Defaults to `false`. Consider changing this option when your project targets JVM 1.8.                                      |
| `generate_serializable_types`          | Boolean      | When `true`, data classes will be annotated with `@kotlinx.serialization.Serializable` when possible. This is automatically set to `true` by the UniFFI Gradle plugin when your Kotlin project uses KotlinX Serialization.                                |
| `jvm_dynamic_library_dependencies`     | String Array | The list of dynamic libraries required by your Rust library on Desktop JVM targets without the prefix and the file extension. Use this if your project depends on an external dynamic library.                                                            |
| `android_dynamic_library_dependencies` | String Array | The list of dynamic libraries required by your Rust library on Android without the prefix and the file extension.                                                                                                                                         |
| `dynamic_library_dependencies`         | String Array | The list of dynamic libraries required by your Rust library on both Desktop JVM targets and Android targets.                                                                                                                                              |

## Cross-compilation tips

### Linux cross-compilation on Windows or macOS

If this is your first time cross-compiling a Linux binary on macOS, you may encounter a linker
error reporting that the linker has received some unknown command-line arguments. That happens
because you tried to link a Linux binary using the linker for Apple platforms.

```
error linking with `cc` failed: exit status 1
  |
  = note: LC_ALL="C" PATH="..." "<linker path>" <arguments ...>
  = note: ld: unknown options: --version-script=... --no-undefined-version ...
          clang: error: linker command failed with exit code 1 (use -v to see invocation)
```

Similarly, if you try to cross-compile a Linux binary on Windows, you may encounter a different
error by Cargo that it couldn't find `cc`.

```
error: linker `cc` not found
  |
  = note: program not found
```

#### Install Zig

When you want to build your application or library for Linux on Windows or macOS, you have to use a
dedicated cross-compilation linker for Linux. There are two available options: GCC and Zig. The
latter is much easier to install.

You can manually download Zig [here](https://ziglang.org/download/). If you're using a package
manager, you can also install Zig as follows:

| Package Manager      | Command                          | Zig Installation Path                                                   |
| -------------------- | -------------------------------- | ----------------------------------------------------------------------- |
| WinGet (Windows)     | `winget install -e --id zig.zig` | `%LOCALAPPDATA%\Microsoft\WinGet\Links\zig.exe`                         |
| Chocolatey (Windows) | `choco install zig`              | `C:\ProgramData\chocolatey\bin\zig.exe`                                 |
| Homebrew (macOS)     | `brew install zig`               | `/opt/homebrew/bin/zig` (Apple Silicon) or `/usr/local/bin/zig` (Intel) |

Next, we have to make Cargo use Zig when building libraries for Linux. First, find where `zig` is
installed. If you installed Zig using a package manager, the installation path is mentioned above.
Make sure Zig is installed correctly.

On Windows using PowerShell:

```
> & "${env:LOCALAPPDATA}\Microsoft\WinGet\Links\zig.exe" version
0.13.0
> C:\ProgramData\chocolatey\bin\zig.exe version
0.13.0
```

or CMD:

```
> %LOCALAPPDATA%\Microsoft\WinGet\Links\zig.exe version
0.13.0
```

On macOS:

```
> /opt/homebrew/bin/zig version
0.13.0
```

#### Make Cargo use Zig (Windows)

We make two batch scripts that uses the `zig` command. You can use any name, but we'll use the
following names.

> `%USERPROFILE` is `C:\Users\<user name>`.

- `%USERPROFILE%\.cargo\x86_64-unknown-linux-gnu-cc.bat`
- `%USERPROFILE%\.cargo\aarch64-unknown-linux-gnu-cc.bat`

In `x86_64-unknown-linux-gnu-cc.bat`, put the following:

```batch
@echo off
<zig path> cc -target x86_64-linux-gnu %*
```

If you installed Zig with WinGet, the content is:

```batch
@echo off
%LOCALAPPDATA%\Microsoft\WinGet\Links\zig.exe cc -target x86_64-linux-gnu %*
```

Similarly, in `aarch64-unknown-linux-gnu-cc.sh`, put as follows:

```batch
@echo off
<zig path> cc -target aarch64-linux-gnu %*
```

This is the final step. Put the paths to the script files in
[the Cargo configuration file](https://doc.rust-lang.org/cargo/reference/config.html). The easiest
option is to modify `%USERPROFILE%\.cargo\config.toml` as follows.

```toml
[target.x86_64-unknown-linux-gnu]
linker = "C:\\Users\\<user name>\\.cargo\\x86_64-unknown-linux-gnu.bat"

[target.aarch64-unknown-linux-gnu]
linker = "C:\\Users\\<user name>\\.cargo\\aarch64-unknown-linux-gnu.bat"
```

You're ready to start building your library and application for Linux.

#### Make Cargo use Zig (macOS)

We make two shell scripts that uses the `zig` command. You can use any name, but we'll use the
following names.

- `~/.cargo/x86_64-unknown-linux-gnu-cc.sh`
- `~/.cargo/aarch64-unknown-linux-gnu-cc.sh`

In `x86_64-unknown-linux-gnu-cc.sh`, put the following:

```shell
#! /bin/sh
<zig path> cc -target x86_64-linux-gnu "$@"
```

If you installed Zig with Homebrew on a Apple Silicon Mac, the content is:

```shell
#! /bin/sh
/opt/homebrew/bin/zig -target x86_64-linux-gnu "$@"
```

Similarly, in `aarch64-unknown-linux-gnu-cc.sh`, put as follows:

```shell
#! /bin/sh
<zig path> cc -target aarch64-linux-gnu "$@"
```

After making two script files, ensure that these files are executable.

```
chmod 555 ~/.cargo/x86_64-unknown-linux-gnu-cc.sh
chmod 555 ~/.cargo/aarch64-unknown-linux-gnu-cc.sh
```

This is the final step. Put the paths to the script files in
[the Cargo configuration file](https://doc.rust-lang.org/cargo/reference/config.html). The easiest
option is to modify `~/.cargo/config.toml` as follows.

```toml
[target.x86_64-unknown-linux-gnu]
linker = "/Users/<user name>/.cargo/x86_64-unknown-linux-gnu-cc.sh"

[target.aarch64-unknown-linux-gnu]
linker = "/Users/<user name>/.cargo/aarch64-unknown-linux-gnu-cc.sh"
```

You're ready to start building your library and application for Linux.

### LLVM version compatibility on Apple Platforms

If you encounter an undefined symbols linker error like the following when building your Rust library that has
a dependency on a C library for iOS, you may have an LLVM version compatibility issue.

```
Undefined symbols for architecture arm64:
  "___chkstk_darwin", referenced from:
      <function name> in <library file name or object file name>
ld: symbol(s) not found for architecture arm64
clang: error: linker command failed with exit code 1 (use -v to see invocation)
```

To check your Rust toolchain's LLVM version, use `rustc --version --verbose`. For example,

```
> rustc --version --verbose
rustc 1.82.0 (f6e511eec 2024-10-15)
binary: rustc
commit-hash: f6e511eec7342f59a25f7c0534f1dbea00d01b14
commit-date: 2024-10-15
host: aarch64-apple-darwin
release: 1.82.0
LLVM version: 19.1.1
```

You can see that Rust 1.82 uses LLVM 19. To check the LLVM version used by Xcode, use
`/usr/bin/gcc --version` (Yeah, Apple puts Clang in that path.)

```
> /usr/bin/xcodebuild -version
Xcode 16.2
Build version 16C5032a
> /usr/bin/gcc --version
Apple clang version 16.0.0 (clang-1600.0.26.6)
Target: arm64-apple-darwin24.3.0
Thread model: posix
InstalledDir: /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin
```

You can see Xcode 16.2 uses LLVM 16. So, the linker in Apple LLVM 16 tried to link object files that
targets LLVM 19, which resulted in a linker error.

To resolve this issue, try downgrading your Rust toolchain to a version that uses lower LLVM version.
For example, Rust 1.81 uses LLVM 18, so downgrading to 1.81 might help.

```
> rustup target add 1.81
> rustup default 1.81
> rustc --version --verbose
rustc 1.81.0 (eeb90cda1 2024-09-04)
binary: rustc
commit-hash: eeb90cda1969383f56a2637cbd3037bdf598841c
commit-date: 2024-09-04
host: aarch64-apple-darwin
release: 1.81.0
LLVM version: 18.1.7
```

You can also set the toolchain directory via the `toolchainDirectory` property in the `rust {}` block, so
consider using this if you don't want to `rustup default 1.81`.

To see which Rust version uses which LLVM version, see the Rust compiler
[CHANGELOG](https://github.com/rust-lang/rust/blob/master/RELEASES.md#version-1820-2024-10-17). You can see
LLVM version upgrade notes in `Internal Changes` sections.

### C++ Runtime on Android NDK

Android NDK has multiple kinds of C++ runtime libraries, so it is important to check which one you are using now.
If you encounter a linker error (whether it's dynamic or static) mentioning functions like `__cxa_pure_virtual`,
you may have not linked C++ runtime to your library properly.

Such error can be a dynamic library load error in runtime like the following:

```
dlopen failed: cannot locate symbol "__cxa_pure_virtual" referenced by "/data/app/.../libyourlibrary.so"
```

or a linking error occurred during build as below.

```
<file name>: undefined reference to `__cxa_pure_virtual'
```

Currently, two C++ runtime libraries are available: `libc++_static.a` and `libc++_shared.so`. The criteria
for choosing which one to use is explained in detail in
[the official documentation](https://developer.android.com/ndk/guides/cpp-support#ic). If you are
embedding your Rust library to an application, use `libc++_shared.so`.

To link C++ runtime, use Cargo [build scripts](https://doc.rust-lang.org/cargo/reference/build-scripts.html).
Configure the path to the directory containing the runtime libraries via the `cargo::rustc-link-search`
command. Use `cargo::rustc-link-lib` to control which runtime library to link. For example in
`<manifest dir>/build.rs`:

```rust
use std::env;
use std::path::PathBuf;

fn main() {
    // The ANDROID_NDK_ROOT variable is automatically set to <SDK root>/ndk/<NDK version> by the
    // Cargo Gradle plugin. You may want to implement your own logic finding the path to NDK if
    // you are not invoking Cargo from Gradle.
    let android_ndk_root = env::var("ANDROID_NDK_ROOT").unwrap();

    // set this to false if you want to use libc++_static.a.
    let use_shared = true;

    let host = if cfg!(target_os = "windows") {
        "windows-x86_64"
    } else if cfg!(target_os = "macos") {
        // Apple Sillion Macs also use x86_64.
        "darwin-x86_64"
    } else if cfg!(target_os = "linux") {
        "linux-x86_64"
    } else {
        panic!("unsupported host")
    };

    let ndk_triplet = match env::var("CARGO_CFG_TARGET_ARCH").unwrap().as_str() {
        "aarch64" => "aarch64-linux-android",
        "arm" => "arm-linux-androideabi",
        "x86_64" => "x86_64-linux-android",
        "x86" => "i686-linux-android",
        /* RISC-V is not supported by this project yet */
        _ => panic!("unsupported architecture"),
    };

    // `libc++_shared.so` and `libc++_static.a` are in
    // toolchains/llvm/prebuilt/<host>/sysroot/usr/lib/<NDK triplet>.
    let library_dir = PathBuf::from(android_ndk_root)
        .join("toolchains")
        .join("llvm")
        .join("prebuilt")
        .join(host)
        .join("sysroot")
        .join("usr")
        .join("lib")
        .join(ndk_triplet);

    // Configure the library directory path.
    println!("cargo::rustc-link-search={}", library_dir.display());

    // Configure the library name.
    println!(
        "cargo::rustc-link-lib={}={}",
        if use_shared { "dylib" } else { "static" },
        if use_shared { "c++_shared" } else { "c++_static" },
    );
}
```

Some Rust libraries automatically find `libc++_static.a` or `libc++_shared.so` from NDK, and they usually
allow users to control this using Cargo features. If some of your dependency uses `libc++_static.a` while
others use `libc++_shared.so`, you may encounter another linker error like the following.

```
ld: error: <HOME>/.rustup/toolchains/.../lib/rustlib/armv7-linux-androideabi/lib/libcompiler_builtins-...(compiler_builtins-... .o): symbol __aeabi_memcpy8@@LIBC_N has undefined version LIBC_N
```

Before such error is printed, Cargo shows the entire linker invocation arguments. For example, you may
be able to see something like:

```
error: linking with `<linker path>` failed: exit status: 1
  |
  = note: LC_ALL="C" PATH="..." "<linker path>" <arguments ...>
```

Copy this error and see if both `-lc++_static` and `-lc++_shared` are in the invocation. If this is the
case, inspect the output emitted by build scripts of dependencies. You can read it from
`target[/<Cargo triplet>]/<profile>/build/<package name>-<hash>/output`. For example in
`target/aarch64-linux-android/debug/build/blake3-<hash>/output`, you can see something like the following.

```
cargo:rerun-if-env-changed=CARGO_FEATURE_PURE
cargo:rerun-if-env-changed=CARGO_FEATURE_NO_NEON
cargo:rerun-if-env-changed=CARGO_FEATURE_NEON
cargo:rerun-if-env-changed=CARGO_FEATURE_NEON
cargo:rerun-if-env-changed=CARGO_FEATURE_NO_NEON
cargo:rerun-if-env-changed=CARGO_FEATURE_PURE
cargo:rustc-cfg=blake3_neon
TARGET = Some("aarch64-linux-android")
OPT_LEVEL = Some("0")
HOST = Some("aarch64-apple-darwin")
...
```

Check whether there is something like `cargo::rustc-link-lib=c++_static` in it.

When you use `libc++_shared.so`, it should be embedded into the application. Use the `dynamicLibraries`
property in the `builds.android {}` block to ensure `libc++_shared.so` is included in the resulting
Android application/library.

```
cargo {
    builds.android {
        dynamicLibraries.addAll("c++_shared")
    }
}
```

### Building for Windows on ARM

By default on an x64 machine, Visual Studio installs MSVC for x64/x86 only. If you try to link a
program for ARM64 without the MSVC ARM64 toolchain, you may see an error that Cargo couldn't find
`link.exe`.

```
> cargo build --target aarch64-pc-windows-msvc

error: linker `link.exe` not found
  |
  = note: program not found

note: the msvc targets depend on the msvc linker but `link.exe` was not found

note: please ensure that Visual Studio 2017 or later, or Build Tools for Visual Studio were installed with the Visual C++ option.

note: VS Code is a different product, and is not sufficient.
```

Make sure you installed the ARM64/ARM64EC compilers and linkers via Visual Studio Installer.
Double-check whether you installed the ARM64 toolchain instead of the 32-bit ARM toolchain.
This project does not support building for 32-bit ARM Windows.

## Versioning

`gobley-uniffi-bindgen` is versioned separately from `uniffi-rs`. UniFFI follows the
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

| gobley-uniffi-bindgen version | uniffi-rs version |
| ----------------------------- | ----------------- |
| v0.1.0                        | v0.28.3           |

## Build and use locally

If you want to work on the bindgen or the Gradle plugin locally, you will have to do some additional Gradle
configuration in order to use these local versions in your projects. Since this project contains many
unit tests and examples, you may want to opt out of building them. Use the following Gradle properties to
choose tests and examples to turn on and off.

| Gradle property name          | Projects                                   |
| ----------------------------- | ------------------------------------------ |
| `gobley.projects.gradleTests` | `:tests:gradle`                            |
| `gobley.projects.uniffiTests` | `:tests:uniffi` & `:examples:custom-types` |
| `gobley.projects.examples`    | `:examples`                                |

These following properties are already in `gradle.properties`. Simply replace `=true` to `=false` to turn
them off.

If you want to run projects in `:tests:gradle` or `:examples`, install the following dependencies.

- Rust
- Zig 0.13
- MinGW (GCC 13)
- OpenJDK 17
- Android SDK 35 with CMake (CMake is used by `:tests:gradle:android-linking`)
- Android NDK
- Perl (Used to build OpenSSL by `:examples:tokio-blake3-app`)
- Visual C++ x64 & ARM64 (Windows)
- Xcode (macOS)

See [`.meta/build-image/Dockerfile`](https://github.com/gobley/gobley/tree/v0.1.0/.meta/build-image/Dockerfile) for more
details.

### Option 1 - Dynamically include the plugins in your project

Clone this repository and reference it from your project. Configure `dependencySubstitution` to use the local plugin
version.

```kotlin
// settings.gradle.kts
pluginManagement {
    // ..
    includeBuild("../uniffi-kotlin-multiplatform-bindings/build-logic")
    // ...
    plugins {
        // comment out id("dev.gobley.uniffi") if you have it here
    }
}
// ...
includeBuild("../uniffi-kotlin-multiplatform-bindings/build-logic") {
    dependencySubstitution {
        substitute(module("dev.gobley.uniffi:gradle-plugin"))
            .using(project(":gradle-plugin"))
    }
}
```

Add the Gradle plugin to the Gradle build file.

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("dev.gobley.uniffi")
    // ...
}
```

Optionally, configure the `uniffi` extension with the exact path to the bindgen of this repository.

```kotlin
uniffi {
    // ...
    bindgenFromPath("<path-to-our-bindgen>")
}
```

### Option 2 - Publish the plugins locally

Clone the repository and build it.

Run the following to publish the plugins:

```shell
./gradlew :build-logic:gobley-gradle:publishToMavenLocal
./gradlew :build-logic:gobley-gradle-cargo:publishToMavenLocal
./gradlew :build-logic:gobley-gradle-rust:publishToMavenLocal
./gradlew :build-logic:gobley-gradle-uniffi:publishToMavenLocal
```

Add the local repository in your project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        // ...
    }
}
```

Optionally, configure the `uniffi` extension with the exact path to the bindgen of this repository.

```kotlin
uniffi {
    // ...
    bindgenFromPath("<path-to-our-bindgen>")
}
```

You can also install the bindgen from a git remote as well. Use this method if you don't want to keep the source code of
this repository on your computer.

```kotlin
uniffi {
    bindgenFromGitTag("https://github.com/gobley/gobley", "v0.1.0")
}
```
