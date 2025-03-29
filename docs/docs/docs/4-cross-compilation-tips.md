---
slug: /cross-compilation-tips
---

# Cross-compilation tips

## Linux cross-compilation on Windows or macOS

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

### Install Zig

When you want to build your application or library for Linux on Windows or macOS, you have to use a
dedicated cross-compilation linker for Linux. There are two available options: GCC and Zig. The
latter is much easier to install.

You can manually download Zig [here](https://ziglang.org/download/). If you're using a package
manager, you can also install Zig as follows:

| Package Manager      | Command                          | Zig Installation Path                                                   |
|----------------------|----------------------------------|-------------------------------------------------------------------------|
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

### Make Cargo use Zig (Windows)

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

### Make Cargo use Zig (macOS)

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

## LLVM version compatibility on Apple Platforms

If you encounter an undefined symbols linker error like the following when building your Rust
library that has a dependency on a C library for iOS, you may have an LLVM version compatibility
issue.

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

To resolve this issue, try downgrading your Rust toolchain to a version that uses lower LLVM
version. For example, Rust 1.81 uses LLVM 18, so downgrading to 1.81 might help.

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

You can also set the toolchain directory via the `toolchainDirectory` property in the `rust {}`
block, so consider using this if you don't want to `rustup default 1.81`.

To see which Rust version uses which LLVM version, see the Rust
compiler [CHANGELOG](https://github.com/rust-lang/rust/blob/master/RELEASES.md#version-1820-2024-10-17).
You can see LLVM version upgrade notes in `Internal Changes` sections.

## C++ Runtime on Android NDK

Android NDK has multiple kinds of C++ runtime libraries, so it is important to check which one you
are using now. If you encounter a linker error (whether it's dynamic or static) mentioning functions
like `__cxa_pure_virtual`, you may have not linked C++ runtime to your library properly.

Such error can be a dynamic library load error in runtime like the following:

```
dlopen failed: cannot locate symbol "__cxa_pure_virtual" referenced by "/data/app/.../libyourlibrary.so"
```

or a linking error occurred during build as below.

```
<file name>: undefined reference to `__cxa_pure_virtual'
```

Currently, two C++ runtime libraries are available: `libc++_static.a` and `libc++_shared.so`. The
criteria for choosing which one to use is explained in detail in
[the official documentation](https://developer.android.com/ndk/guides/cpp-support#ic). If you are
embedding your Rust library to an application, use `libc++_shared.so`.

To link C++ runtime, use
Cargo [build scripts](https://doc.rust-lang.org/cargo/reference/build-scripts.html).
Configure the path to the directory containing the runtime libraries via the
`cargo::rustc-link-search` command. Use `cargo::rustc-link-lib` to control which runtime library to
link. For example in `<manifest dir>/build.rs`:

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

Some Rust libraries automatically find `libc++_static.a` or `libc++_shared.so` from NDK, and they
usually allow users to control this using Cargo features. If some of your dependency uses
`libc++_static.a` while others use `libc++_shared.so`, you may encounter another linker error like
the following.

```
ld: error: <HOME>/.rustup/toolchains/.../lib/rustlib/armv7-linux-androideabi/lib/libcompiler_builtins-...(compiler_builtins-... .o): symbol __aeabi_memcpy8@@LIBC_N has undefined version LIBC_N
```

Before such error is printed, Cargo shows the entire linker invocation arguments. For example, you
may be able to see something like:

```
error: linking with `<linker path>` failed: exit status: 1
  |
  = note: LC_ALL="C" PATH="..." "<linker path>" <arguments ...>
```

Copy this error and see if both `-lc++_static` and `-lc++_shared` are in the invocation. If this is
the case, inspect the output emitted by build scripts of dependencies. You can read it from
`target[/<Cargo triplet>]/<profile>/build/<package name>-<hash>/output`. For example in
`target/aarch64-linux-android/debug/build/blake3-<hash>/output`, you can see something like the
following.

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

When you use `libc++_shared.so`, it should be embedded into the application. Use the
`dynamicLibraries` property in the `builds.android {}` block to ensure `libc++_shared.so` is
included in the resulting Android application/library.

```
cargo {
    builds.android {
        dynamicLibraries.addAll("c++_shared")
    }
}
```

## Building for Windows on ARM

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
