import org.gradle.api.Project

plugins {
    id("uniffi-tests")
}

fun Project.propertyIsTrue(propertyName: String, default: Boolean = true): Boolean {
    if (!hasProperty(propertyName)) return default
    val propertyValue = findProperty(propertyName)?.toString()?.lowercase() ?: return default
    return propertyValue == "true" || propertyValue == "1"
}

uniffi {
    generateFromLibrary {
        namespace = name.replace('-', '_')
        generateImmutableRecords =
            propertyIsTrue("gobley.projects.uniffiTests.generateImmutableRecords", false)
        omitChecksums =
            propertyIsTrue("gobley.projects.uniffiTests.omitChecksums", false)
    }
}
