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
    id("dev.gobley.cargo") version "0.3.5"
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

### Controlling the targets to build

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
the MinGW build for JVM on Windows since Visual C++ is available. To override this behavior, use the
`embedRustLibrary` property like the following. Note that MinGW Windows on ARM is not supported by
Gobley.

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

### Publishing JAR artifacts containing the Rust dynamic libraries

Since the dynamic libraries built with Cargo are packaged as separate JAR files with different
classifiers, you can publish the library for each platform on a different build machine. For
example, you can configure the CI to build and publish for Windows and Linux on Windows and macOS on
macOS. The Java part is platform-agnostic. You can publish it on any platform where you can use
Java.

To override the JAR classifier used by each platform, use the `jarTaskProvider` property.
The `archiveClassifier` defaults to `rustTarget.jnaResourcePrefix + "-debug"` for debug builds and
`rustTarget.jnaResourcePrefix` for release builds.

```kotlin
cargo {
    builds {
        macos {
            debug.jarTaskProvider.configure {
                // Set the JAR classifier to darwin-<arch>-unoptimized
                archiveClassifier = rustTarget.jnaResourcePrefix + "-unoptimized"
            }
        }
    }
}
```

When you're developing a Kotlin Multiplatform project and have applied the `maven-publish` Gradle
plugin, The JAR tasks are automatically added to the publication. For more details about using
`maven-publish` with Kotlin Multiplatform, please
refer [here](https://kotlinlang.org/docs/multiplatform-publish-lib.html). To disable this behavior,
use the `publishJvmArtifacts` property.

```kotlin
cargo {
    publishJvmArtifacts = false
}
```

To use the published Rust dynamic library JAR artifacts, you have to specify the classifiers.

```kotlin
kotlin {
    sourceSets {
        val jvmMain by creating {
            dependencies {
                runtimeOnly(dependencies.variantOf("com.example.foo:foo-jvm:0.1.0") {
                    classifier("darwin-aarch64")
                })
                // You can use the above with version catalogs as well
                runtimeOnly(dependencies.variantOf(libs.example.foo) {
                    classifier("darwin-aarch64")
                })
                // Add for the other platforms you're targeting as well
                runtimeOnly(dependencies.variantOf(libs.example.foo) {
                    classifier("win32-x86-64")
                })
            }
        }
    }
}
```

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
another build, you also have to reference the JAR artifact containing the dynamic library built with
Cargo. To find the JAR task generating such artifacts,
see [Publishing JAR artifacts containing the Rust dynamic libraries](#publishing-jar-artifacts-containing-the-rust-dynamic-libraries).

When you want to build the Rust dynamic library JAR locally, you can reference the JAR file using
the `files` or the `fileTree` functions.

```kotlin
kotlin {
    sourceSets {
        getByName("androidUnitTest") {
            dependencies {
                // runtimeOnly(files("<project name>-<JVM target name>-<version>-<classifier>.jar"))
                runtimeOnly(files("foo-jvm-0.1.0-darwin-aarch64.jar"))
                // You can add multiple invocations of runtimeOnly(...)
                runtimeOnly(files("foo-jvm-0.1.0-win32-x86-64.jar"))
                runtimeOnly("net.java.dev.jna:jna:5.18.1") // required to run if you're using UniFFI
            }
        }
    }
}
```

If you want to automate this process, you can publish the JVM version of your library and use it
from the local unit test. For example, To publish your library to the local Maven repository on your
system, run the `publishToMavenLocal` task.

```shell
./gradlew :your:project:publishToMavenLocal
```

In the local repository which is located in `~/.m2`, you will see that multiple artifacts including
`<project name>` and `<project name>-<JVM target name>` are generated. To reference it, register the
`mavenLocal()` repository and put the artifact name to `runtimeOnly()`.

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
                runtimeOnly(dependencies.variantOf("com.example.foo:foo-jvm:0.1.0") {
                    // The archive classifier, which defaults to `rustTarget.jnaResourcePrefix`.
                    classifier("darwin-aarch64")
                })
                runtimeOnly("net.java.dev.jna:jna:5.18.1") // required to run if you're using UniFFI
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

The Cargo plugin will automatically configure several directories as the paths to locate the dynamic
libraries.

- The Cargo build output directory (e.g., `target/<triplet>/<profile>`).
- The Cargo build script output directory (e.g.,
  `target/<triplet>/<profile>/build/<crate>-<hash>/out`). Use the [
  `OUT_DIR`](https://doc.rust-lang.org/cargo/reference/environment-variables.html) environment
  variable to determine the value of this path in your build script.
- (Android only) The NDK library directories (e.g.,
  `<NDK ROOT>/toolchains/llvm/prebuilt/<host>/sysroot/usr/lib/<triplet>[/<API Level>]`).
  These directories contain C++ runtime libraries like `libc++_shared.so` and NDK libraries like
  `libaaudio.so`.

If your build system uses other directories, add their paths to `dynamicLibrarySearchPaths`.

```kotlin
cargo {
    builds.jvm {
        dynamicLibrarySearchPaths.add(File("/path/to/libraries"))
        dynamicLibraries.add("foo")
    }
}
```

## Configuring Cargo to use different Cargo features or build profiles

While it is unusual to use separate configurations for debugging and releasing on Java, you should
care about the build variant when you're publishing an application or a library written in Rust.
Gobley handles this discrepancy using **profiles** and **variants**. If you want to use customized
[Cargo profiles](https://doc.rust-lang.org/cargo/reference/profiles.html) or different Cargo
features for different Cargo profiles, you can configure them using these APIs.

Gobley provides two variants: `Variant.Debug` and `Variant.Release`. You can then specify the Cargo
profile to use for each variant in the `cargo {}` block.

```kotlin
import gobley.gradle.cargo.profiles.CargoProfile

cargo {
    debug.profile = CargoProfile("my-debug")
    release.profile = CargoProfile.Bench
}
```

If you want to use different Cargo features, you can configure them in the `cargo {}`, the
`debug {}`, or the `release {}` blocks.

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

## Be careful of the build variant used during publishing

> :bulb: The Android Gradle plugin supports multiple build variants by default, and Gobley will
> automatically invoke the debug build for the debug variant and the release build for the release
> variant. Custom build variants for Android are not supported yet.

For scenarios where the variant to use can't be chosen automatically, Gobley provides `jvmVariant`,
`jvmPublishingVariant`, and `nativeVariant`. These properties can be configured inside the
`cargo {}` or the `builds {}` blocks.

```kotlin
import gobley.gradle.Variant
import gobley.gradle.cargo.dsl.*

cargo {
    jvmVariant = Variant.Release
    jvmPublishingVariant = Variant.Release
    nativeVariant = Variant.Debug
    builds {
        jvm {
            jvmVariant = Variant.Release
            jvmPublishingVariant = Variant.Release
        }
        native {
            nativeVariant = Variant.Debug
        }
    }
}
```

`jvmVariant` designates the variant to use when you hit the run button inside the IDE. It defaults
to `Variant.Debug`. If you're using Gobley in an application project or a library project directly
referenced by an application project, `jvmVariant` is used, which means you might be building and
releasing the debug version of your application. On the other hand, `jvmPublishingVariant` is used
when you publish a library. It defaults to `Variant.Release`. The publishing task depends on the JAR
task selected by `jvmPublishingVariant`.

Unlike the JVM variant properties, native targets only have `nativeVariant`. It defaults to
`Variant.Debug`. When you invoke Gradle from Xcode, Gobley will read environment variables set by
Xcode and automatically determine the value for `nativeVariant`. When you build for Windows or
Linux, where such environment variables are not available, you should manually determine the value
for `nativeVariant`. Even when you build for Apple platforms, if you're just publishing a library,
you should be careful of the value of `nativeVariant`, as it doesn't use Xcode.

You can use Gradle properties to control these values.

```kotlin
import gobley.gradle.Variant

cargo {
    // When you're using Gobley in an application project
    jvmVariant = Variant(findProperty("my.project.jvm.variant") ?: "debug")
    // When you build for native targets without Xcode
    nativeVariant = Variant(findProperty("my.project.native.variant") ?: "debug")
}
```

## Disabling automatic installation of pre-built standard libraries

By default, the Cargo plugin installs the pre-built standard library of the specified target via the
`rustup target add` command before initiating a build. For example, if your library targets AArch64
Android, the plugin will run `rustup target add aarch64-linux-android` before building.

While this behavior is convenient in most scenarios, some users using a custom target may have
trouble setting their build. To address such cases, the Cargo plugin offers the
`installTargetBeforeBuild` property, which defaults to `true`. If you set it to `false`, the plugin
will skip the `rustup target add` step before building. You can configure this property globally or
on a per-target basis, as shown below:

```kotlin
cargo {
    // Disable `rustup target add` for all targets
    installTargetBeforeBuild = false
    // Alternatively, disable `rustup target add` for a specific target (e.g. Android)
    builds.android {
        // installTargetBeforeBuild is inherited from the one in the `cargo {}` block 
        installTargetBeforeBuild = false
    }
}
```

## Enabling the nightly mode and building tier 3 Rust targets

Some targets like tvOS and watchOS are tier 3 in the Rust world (they are tier 2 on the Kotlin
side). Pre-built standard libraries are not available for these targets, and the Cargo plugin does
not invoke the `rustup target add` command for them. To use the standard library, you must pass the
`-Zbuild-std` flag to the `cargo build` command (
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
                // You can configure for the check task as well
                checkTaskProvider.configure {
                    nightly = true
                    extraArguments.add("-Zbuild-std")
                }
            }
        }
    }
}
```