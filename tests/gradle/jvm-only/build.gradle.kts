import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.jvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.gobley.cargo")
}

cargo {
    builds.jvm {
        resourcePrefix = ""
        embedRustLibrary = rustTarget == GobleyHost.current.rustTarget
        dynamicLibraries.add("gobley-fixture-gradle-jvm-only-cpp")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    sourceSets {
        main {
            dependencies {
                implementation(libs.jna)
            }
        }
        test {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}
