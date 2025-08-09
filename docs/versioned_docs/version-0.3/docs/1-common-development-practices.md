---
slug: /common-development-practices
---

# Common development practices

## Development environment

There are only one option to code both in Kotlin and Rust in a single IDE: IntelliJ IDEA Ultimate,
which needs a paid
subscription. Fleet was another option, but JetBrains announced that
[they are dropping the KMP support in Fleet](https://blog.jetbrains.com/kotlin/2025/02/kotlin-multiplatform-tooling-shifting-gears/).

Therefore, most users may use different IDEs for Kotlin and Rust when developing with this project.
For Kotlin, you can use Android Studio or IntelliJ IDEA, and for Rust, you can use `rust-analyzer`
with Visual Studio Code or RustRover. In normal cases, Kotlin handles the part interacting with
users such as UI while Rust handles the core business logic, so using two IDEs won't harm the
developer experience that much.

## Separate the Rust part for shorter build time and separation of concerns

Since building Rust takes much more time than compiling Kotlin, try separating the Kotlin part that
uses
Rust directly as a core library. You can build and publish the core library using the
`maven-publish` plugin and the other Kotlin part can download it from a Maven repository.

To learn about how to consume the published Rust library, please refer
to [Publishing JAR artifacts containing the Rust dynamic libraries](./2-gradle-plugins/1-cargo-plugin.md#publishing-jar-artifacts-containing-the-rust-dynamic-libraries).
If you are managing your project using multi-project builds, please refer
to [Use the Rust plugin to configure multi-project builds properly](./2-gradle-plugins/3-rust-plugin.md#use-the-rust-plugin-to-configure-multi-project-builds-properly).

## Watch out for excessive disk usage

The more platforms you target, the larger the build result will be. Ensure your CI has enough space
to build your project. Gradle caches files from the build in `~/.gradle/caches`. If you encounter
much more `No space left on device` errors after using this project, try removing
`~/.gradle/caches`. Since Gradle still tries to find cached files in `~/.gradle/caches` after you
remove it, remove all `.gradle` and `build` directories in your project as well. On macOS & Linux:

```shell
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

When you build iOS apps, Xcode generates files in `/private/var/folders/zz`, which are removed
automatically after every reboot. Try restart your Mac if you still have the disk space issue after
removing the Gradle caches.

## Fix the Rust version you use for better reproducibility

In your Cargo.toml file, you can specify the minimum supported Rust version (MSRV) for your package
using the `rust-version` field in the `[package]` section. For example:

```toml
[package]
rust-version = "1.82"
```

While the `rust-version` field defines the required MSRV to compile your crate, it does not enforce
the use of that specific version. You (or CI systems) can still use newer versions of Rust to build
the package. This can lead to situations where your code is compiled with a Rust version that hasn't
been explicitly tested, resulting in unexpected linter warnings that varies depending on the CI
pipeline's environment.

You can set a specific Rust version for your entire system using `rustup`, but this can be
problematic if you have other Rust projects on your system that rely on different Rust versions.

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

The most environment-agnostic and project-specific way to fix the Rust version is by creating a
`rust-toolchain.toml` file in the root directory of your project. When `rustup` detects a
`rust-toolchain.toml` file in the current directory or any parent directory, it automatically uses
the toolchain specified in that file for that project, overriding the global default. For more
details about `rustup` overrides, please refer
to [the official documentation](https://rust-lang.github.io/rustup/overrides.html).

```toml
[toolchain]
channel = "1.82" # A specific version, "stable", "nighlty", etc.
```

You can also set the toolchain directory via the `toolchainDirectory` property in the `rust {}`
block, so consider using this if you are using a custom toolchain installed in a non-standard
directory.
