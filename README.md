# Gobley

[![License](https://img.shields.io/github/license/gobley/gobley)](https://github.com/gobley/gobley/blob/main/LICENSE)
[![Crates.io](https://img.shields.io/crates/v/gobley-uniffi-bindgen)](https://crates.io/crates/gobley-uniffi-bindgen)
[![Gradle Plugin Portal](https://img.shields.io/maven-central/v/dev.gobley.gradle/gobley-gradle)](https://central.sonatype.com/artifact/dev.gobley.gradle/gobley-gradle)
[![GitHub Actions Workflow Status (All Platforms)](https://img.shields.io/github/actions/workflow/status/gobley/gobley/pr-build-test.yml?branch=main&label=tests:%20Windows%20•%20macOS%20•%20Linux)](https://github.com/gobley/gobley/actions/workflows/pr-build-test.yml?query=branch%3Amain)
[![GitHub Actions Workflow Status (Docker)](https://img.shields.io/github/actions/workflow/status/gobley/gobley/pr-build-test-docker.yml?branch=main&label=tests:%20Docker)](https://github.com/gobley/gobley/actions/workflows/pr-build-test-docker.yml?query=branch%3Amain)

<img align="right" src=".idea/icon.svg" width="20%">

A set of libraries and tools that help you mix Rust and Kotlin
using [UniFFI](https://github.com/mozilla/uniffi-rs). This project was forked
from [UniFFI Kotlin Multiplatform bindings](https://gitlab.com/trixnity/uniffi-kotlin-multiplatform-bindings).
Since the original project is no longer maintained, active development now continues here.

Currently, Android, Kotlin/JVM, and Kotlin/Native are supported. WASM is not supported yet.

## Features

- UniFFI Bindings Generation for Kotlin Multiplatform (Android, JVM, Kotlin/Native)
- KotlinX Serialization Support
- Automatic building and linking of Rust libraries to Kotlin projects

## Getting started

Please read the [tutorial](https://gobley.dev/docs/tutorial) and
the [documentation](https://gobley.dev/docs) to get started. If you have trouble setting up your
project, please create a question
in [GitHub Discussions](https://github.com/gobley/gobley/discussions). For the contribution guide,
please refer to [here](./CONTRIBUTING.md).
