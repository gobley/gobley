import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

fun Project.propertyIsTrue(propertyName: String, default: Boolean = true): Boolean {
    if (!hasProperty(propertyName)) return default
    val propertyValue = findProperty(propertyName)?.toString()?.lowercase() ?: return default
    return propertyValue == "true" || propertyValue == "1"
}

yarn.apply {
    val gradleTestsEnabled = propertyIsTrue("gobley.projects.gradleTests")
    val uniffiTestsEnabled = propertyIsTrue("gobley.projects.uniffiTests")
    val examplesEnabled = propertyIsTrue("gobley.projects.examples")
    val postfix = when {
        gradleTestsEnabled && uniffiTestsEnabled && examplesEnabled -> ""
        !gradleTestsEnabled && !uniffiTestsEnabled && !examplesEnabled -> "-none"
        else -> StringBuilder().apply {
            if (gradleTestsEnabled) {
                append("-gradle")
            }
            if (uniffiTestsEnabled) {
                append("-uniffi")
            }
            if (examplesEnabled) {
                append("-examples")
            }
        }.toString()
    }
    lockFileName = "yarn${postfix}.lock"
}