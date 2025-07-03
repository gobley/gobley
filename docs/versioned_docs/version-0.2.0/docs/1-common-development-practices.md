---
slug: /common-development-practices
---

# Common development practices

There are only one option to code both in Kotlin and Rust in a single IDE: IntelliJ IDEA Ultimate,
which needs a paid
subscription. Fleet was another option, but JetBrains announced that
[they are dropping the KMP support in Fleet](https://blog.jetbrains.com/kotlin/2025/02/kotlin-multiplatform-tooling-shifting-gears/).

Therefore, most users may use different IDEs for Kotlin and Rust when developing with this project.
For Kotlin, you can use Android Studio or IntelliJ IDEA, and for Rust, you can use `rust-analyzer`
with Visual Studio Code or RustRover. In normal cases, Kotlin handles the part interacting with
users such as UI while Rust handles the core business logic, so using two IDEs won't harm the
developer experience that much.

Since building Rust takes much more time than compiling Kotlin, try separating the Kotlin part that uses
Rust directly as a core library. You can build and publish the core library using the
`maven-publish` plugin and the other Kotlin part can download it from a maven repository.

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
