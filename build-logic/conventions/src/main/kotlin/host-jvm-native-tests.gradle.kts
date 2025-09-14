import gobley.gradle.GobleyHost
import gobley.gradle.rust.dsl.hostNativeTarget
import gobley.gradle.rust.dsl.useRustUpLinker
import org.gradle.accessors.dm.*

plugins {
    id("dev.gobley.rust")
    kotlin("multiplatform")
    kotlin("plugin.atomicfu")
}

// https://github.com/gradle/gradle/issues/15383
apply<VersionCatalogPlugin>()
val libs = extensions.getByName("libs") as LibrariesForLibs

kotlin {
    jvmToolchain(17)
    jvm()
    hostNativeTarget {
        if (GobleyHost.Platform.Windows.isCurrent) {
            compilations.getByName("test") {
                useRustUpLinker()
            }
        }
    }
    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    reports {
        junitXml.required.set(true)
    }
}
