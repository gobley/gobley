---
slug: /gradle-plugins/cargo
---

# The Cargo plugin

## Basic usage

The Cargo plugin is responsible for building and linking the Rust library to your Kotlin project.
You can use it even when you are not using UniFFI. If the `Cargo.toml` is located in the project
root, you can simply apply the `dev.gobley.cargo` the plugin.

```kotlin
plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo") version "0.2.0"
}
```

## Configuring Cargo package not in the project root

If the Cargo package is located in another directory, you can configure the path in the `cargo {}`
block.

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

## Configuring Cargo to use different Cargo features or build profiles

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

If you want to use different features for each variant (debug or release), you can configure them in
the `debug {}` or `release {}` blocks.

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

`features` are inherited from the outer block to the inner block. To override this behavior in the
inner block, use `.set()` or the `=` operator overloading.

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

For Android and Apple platform builds invoked by Xcode, the plugin automatically decides which
profile to use. For other targets, you can configure it with the `jvmVariant` or `nativeVariant`
properties. When undecidable, these values default to `Variant.Debug`.

```kotlin
import gobley.gradle.Variant

cargo {
    jvmVariant = Variant.Release
    nativeVariant = Variant.Debug
}
```

## The Cargo plugin only builds for required platforms

Cargo build tasks are configured as the corresponding Kotlin target is added in the `kotlin {}`
block. For example, if you don't invoke `androidTarget()` in `kotlin {}`, the Cargo plugin won't
configure the Android build task as well.

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

The Cargo plugin scans all the Rust dependencies using [
`cargo metadata`](https://doc.rust-lang.org/cargo/commands/cargo-metadata.html). If you modify Rust
source files including those in dependencies defined in the Cargo manifest, the Cargo plugin will
rebuild the Cargo project.

## Changing the NDK version used to build Android binaries

For Android builds, the Cargo plugin automatically determines the SDK and the NDK to use based on
the property values of the `android {}` block. To use different a NDK version, set `ndkVersion` to
that version.

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

## Changing the environment variables used during the build

The Cargo plugin automatically configures environment variables like `ANDROID_HOME` or `CC_<target>`
for you, but if you need finer control, you can directly configure the properties of the build task.
The build task is accessible in the `builds {}` block.

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

## Configuring the platforms used by the JVM target

For JVM builds, the Cargo plugin tries to build all the targets, whether the required toolchains are
installed on the current system or not. The list of such targets by the build host is as follows.

| Targets      | Windows | macOS | Linux |
|--------------|---------|-------|-------|
| Android      | ✅       | ✅     | ✅     |
| Apple Mobile | ❌       | ✅     | ❌     |
| MinGW        | ✅       | ✅     | ✅     |
| macOS        | ❌       | ✅     | ❌     |
| Linux        | ✅       | ✅     | ✅     |
| Visual C++   | ✅       | ❌     | ❌     |

To build for specific targets only, you can configure that using the `embedRustLibrary` property.
For example, to build a shared library for the current build host only, set this property to
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

On Windows, both MinGW and Visual C++ can generate DLLs. By default, the Cargo plugin doesn't invoke
the MinGW build for JVM when Visual C++ is available. To override this behavior, use the
`embedRustLibrary` property like the following. Note that Windows on ARM is not available with
MinGW.

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

`embedRustLibrary` is also used when you
use [the external types feature](https://mozilla.github.io/uniffi-rs/udl/ext_types.html)
in your project. Rust statically links all the crates unless you specify the library crate's kind as
`dylib`. So, the final Kotlin library does not have to include shared libraries built from every
crate. Suppose you have two crates, `foo`, and `bar`, where `foo` exposes the external types and
`bar` uses types in `foo`. Since when building `bar.dll`, `libbar.dylib`, or `libbar.so`, the `foo`
crate is also included in `bar`, you don't have to put `foo.dll`, `libfoo.dylib`, or `libfoo.so`
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

The JVM `loadIndirect()` function in the bindings allow users to override the `cdylib_name` value
using the `uniffi.component.<namespace name>.libraryOverride` system property as well. See the
[
`:tests:uniffi:ext-types:ext-types`](https://github.com/gobley/gobley/tree/main/tests/uniffi/ext-types/ext-types)
test to see how this works.

## Configuring the platforms used by Android local unit tests

Android local unit tests requires JVM targets to be built, as they run in the host machine's JVM.
The Cargo plugin automatically copies the Rust shared library targeting the host machine into
Android local unit tests. It also finds projects that depend on the project using the Cargo plugin,
and the Rust library will be copied to all projects that directly or indirectly use the Cargo
project. If you want to include shared library built for a different platform, you can control that
using the `androidUnitTest` property.

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

Local unit tests are successfully built even if there are no builds with `androidUnitTest` enabled,
but you will encounter a runtime error when you invoke a Rust function from Kotlin.

When you build or publish your Rust Android library separately and run Android local unit tests in
another build, you also have to reference the JVM version of your library from the Android unit
tests.

To build the JVM version, run the `<JVM target name>Jar` task. The name of the JVM target can be
configured with the `jvm()` function, which defaults to `"jvm"`. For example, when the name of the
JVM target is `"desktop"`:

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

The build output will be located in `build/libs/<project name>-<JVM target name>.jar`. In the above
case, the name of the JAR file will be `<project name>-desktop.jar`. The JAR file then can be
referenced using the `files` or the `fileTree` functions.

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

The above process can be automated using the `maven-publish` Gradle plugin. It publishes the JVM
version of your library separately. For more details about using `maven-publish` with Kotlin
Multiplatform, please refer [here](https://kotlinlang.org/docs/multiplatform-publish-lib.html).

To publish your library to the local Maven repository on your system, run the `publishToMavenLocal`
task.

```shell
./gradlew :your:project:publishToMavenLocal
```

In the local repository which is located in `~/.m2`, you will see that multiple artifacts including
`<project name>` and `<project name>-<JVM target name>` are generated. To reference it, register the
`mavenLocal()` repository and put the artifact name to `implementation()`.

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

## Configuring external dynamic libraries your Rust code depends on

If your Rust library is dependent on other shared libraries, you have to ensure that they are also
available during runtime. For JVM and Android builds, you can use the `dynamicLibraries` and the
`dynamicLibrarySearchPaths` properties. The specified libraries will be embedded into the resulting
JAR or the Android bundle.

```kotlin
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

Some directories like the NDK installation directory or the Cargo build output directory are already
registered in `dynamicLibrarySearchPaths`. If your build system uses another directory, add that to
this property.

## Enabling the nightly mode and building tier 3 Rust targets

Some targets like tvOS and watchOS are tier 3 in the Rust world (they are tier 2 on the Kotlin
side). Pre-built standard libraries are not available for these targets. To use the standard
library, you must pass the `-Zbuild-std` flag to the `cargo build` command (
See [here](https://doc.rust-lang.org/cargo/reference/unstable.html#build-std) for the official
documentation). Since this flag is available only on the nightly channel, you should tell the Cargo
plugin to use the nightly compiler to compile the standard library.

First, download the source code of the standard library using the following command.

```
rustup component add rust-src --toolchain nightly
```

To get the tier of a `RustTarget`, you can use the `fun RustTarget.tier(version: String): Int`
function. We can instruct Cargo to build the standard library for tier 3 targets only with it.

```kotlin
cargo {
    builds.appleMobile {
        variants {
            if (rustTarget.tier(project.rustVersion.get()) >= 3) {
                buildTaskProvider.configure {
                    // Pass +nightly to `cargo rustc` (or `cargo build`) to use `-Zbuild-std`.
                    nightly = true
                    // Make Cargo build the standard library
                    extraArguments.add("-Zbuild-std")
                }
            }
        }
    }
}
```