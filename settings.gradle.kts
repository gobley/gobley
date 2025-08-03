pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "gobley"

fun ExtraPropertiesExtension.propertyIsTrue(propertyName: String, default: Boolean = true): Boolean {
    if (!has(propertyName)) return default
    val propertyValue = this[propertyName]?.toString()?.lowercase() ?: return default
    return propertyValue == "true" || propertyValue == "1"
}

if (ext.propertyIsTrue("gobley.projects.gradleTests")) {
    include(":tests:gradle:android-linking")
    include(":tests:gradle:cargo-only")
    include(":tests:gradle:js-only")
    include(":tests:gradle:jvm-only")
}

if (ext.propertyIsTrue("gobley.projects.uniffiTests")) {
    include(":tests:uniffi:callbacks")
    include(":tests:uniffi:chronological")
    include(":tests:uniffi:coverall")
    include(":tests:uniffi:coverall-android")
    include(":tests:uniffi:coverall-jvm")
    include(":tests:uniffi:docstring")
    include(":tests:uniffi:docstring-proc-macro")
    include(":tests:uniffi:enum-types")
    include(":tests:uniffi:error-types")
    // Required by ext-types and ext-types-proc-macro
    include(":examples:custom-types")
    include(":tests:uniffi:ext-types:custom-types")
    include(":tests:uniffi:ext-types:ext-types")
    include(":tests:uniffi:ext-types:ext-types-proc-macro")
    include(":tests:uniffi:ext-types:http-headermap")
    include(":tests:uniffi:ext-types:sub-lib")
    include(":tests:uniffi:ext-types:uniffi-one")
    include(":tests:uniffi:futures")
    include(":tests:uniffi:keywords")
    include(":tests:uniffi:large-enum")
    include(":tests:uniffi:large-error")
    include(":tests:uniffi:proc-macro")
    include(":tests:uniffi:simple-fns")
    include(":tests:uniffi:simple-iface")
    include(":tests:uniffi:struct-default-values")
    include(":tests:uniffi:trait-methods")
    include(":tests:uniffi:type-limits")
}

if (ext.propertyIsTrue("gobley.projects.examples")) {
    include(":examples:app")
    include(":examples:arithmetic-procmacro")
    include(":examples:audio-cpp-app")
    include(":examples:custom-types")
    include(":examples:todolist")
    include(":examples:tokio-blake3-app")
}
