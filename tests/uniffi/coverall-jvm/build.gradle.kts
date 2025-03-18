import com.android.build.gradle.internal.tasks.factory.dependsOn
import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.jvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.gobley.cargo")
    id("dev.gobley.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
}

cargo {
    packageDirectory = project.layout.projectDirectory.dir("../coverall")
    builds.jvm {
        embedRustLibrary = rustTarget == GobleyHost.current.rustTarget
    }
    builds.configureEach {
        variants {
            buildTaskProvider.dependsOn(
                project(":tests:uniffi:coverall").tasks.named(buildTaskProvider.name)
            )
        }
    }
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("bindgen"))
    generateFromLibrary {
        namespace = name.replace('-', '_')
        packageName = "coverall"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    sourceSets {
        test {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}
