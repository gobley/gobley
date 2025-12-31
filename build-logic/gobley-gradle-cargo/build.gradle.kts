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
        description = "A Gradle plugin for building Rust libraries and linking them to Kotlin projects.",
        gradlePlugin = true,
    )
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.kotlin.android))
    compileOnly(plugin(libs.plugins.kotlin.jvm))
    compileOnly(plugin(libs.plugins.kotlin.multiplatform))
    compileOnly(plugin(libs.plugins.android.application))
    compileOnly(plugin(libs.plugins.android.library))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.semver)
    compileOnly(libs.jna)

    api(project(":gobley-gradle"))
    api(project(":gobley-gradle-rust"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.core)
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    vcsUrl.set("https://github.com/gobley/gobley")
    website.set(vcsUrl)

    plugins {
        create("gobley-gradle-cargo") {
            id = "dev.gobley.cargo"
            displayName = "Gobley Cargo Gradle Plugin"
            implementationClass = "gobley.gradle.cargo.CargoPlugin"
            description = project.description
            tags.addAll("rust", "kotlin", "kotlin-multiplatform")
        }
    }
}
