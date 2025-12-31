plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    alias(libs.plugins.kotlinx.binary.compatibility.validator) apply false
    id("gobley-gradle-build") apply false
}