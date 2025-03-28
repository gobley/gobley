---
slug: /tutorial-jvm
---

# Getting started (Kotlin/JVM)

Welcome to Gobley! Gobley is a set of libraries and tools that help you mix Rust and Kotlin, so you
can focus on implementing your business logic. In this tutorial, you will learn how to embed Rust
code into your Kotlin project using Gobley. If you have trouble setting up your project, please
create a question in [GitHub Discussions](https://github.com/gobley/gobley/discussions).

## Prerequisites

To develop an Kotlin/JVM program, you need to
install [IntelliJ IDEA](https://www.jetbrains.com/idea/download).
Using [Android Studio](https://developer.android.com/studio) is also available for Kotlin
development.

To develop in Rust, you need:

1. A [Rust toolchain](https://www.rust-lang.org/tools/install).
2. An IDE for Rust. Several options are available:
    - [Visual Studio Code](https://code.visualstudio.com/) with [
      `rust-analyzer`](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer).
    - [RustRover](https://www.jetbrains.com/rust).
    - [IntelliJ IDEA **Ultimate**](https://www.jetbrains.com/idea/download) with
      the [Rust plugin](https://www.jetbrains.com/help/idea/rust-plugin.html).
    - Other editors like Vim. Still, using `rust-analyzer` is recommended.

## Creating an Kotlin/JVM project

Let's first create a new Kotlin/JVM project.