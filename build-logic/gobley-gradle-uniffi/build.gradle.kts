plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `kotlin-dsl`
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    id("gobley-gradle-build")
}

apiValidation {
    nonPublicMarkers += "gobley.gradle.InternalGobleyGradleApi"
}

gobleyGradleBuild {
    configureGobleyGradleProject(
        description = "A Gradle plugin for generating UniFFI Kotlin Multiplatform bindings for Rust libraries.",
        gradlePlugin = true,
    )
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.kotlin.android))
    compileOnly(plugin(libs.plugins.kotlin.jvm))
    compileOnly(plugin(libs.plugins.kotlin.multiplatform))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tomlkt)

    api(project(":gobley-gradle"))
    api(project(":gobley-gradle-rust"))
    api(project(":gobley-gradle-cargo"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.core)
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    vcsUrl.set("https://github.com/gobley/gobley")
    website.set(vcsUrl)

    plugins {
        create("gobley-gradle-uniffi") {
            id = "dev.gobley.uniffi"
            displayName = "Gobley UniFFI Gradle Plugin"
            implementationClass = "gobley.gradle.uniffi.UniFfiPlugin"
            description = project.description
            tags.addAll("uniffi", "rust", "kotlin", "kotlin-multiplatform")
        }
    }
}
