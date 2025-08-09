---
slug: /gradle-plugins
---

# Using the Gradle plugins

This project contains three Gradle plugins:

- [The Cargo plugin (`dev.gobley.cargo`)](./1-cargo-plugin.md)
- [The UniFFI plugin (`dev.gobley.uniffi`)](./2-uniffi-plugin.md)
- [The Rust plugin (`dev.gobley.rust`)](./3-rust-plugin.md)

These plugins are published in Maven Central. In your `settings.gradle.kts`, put `mavenCentral()` in
the `pluginManagement {}` block.

```
pluginManagement {
    repositories {
        mavenCentral()
    }
}
```

If you're using multi-project builds, please
read [The Rust plugin](./3-rust-plugin.md).