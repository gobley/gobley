plugins {
    id("uniffi-tests-from-library")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.http)
                api(project(":examples:custom-types"))
                api(project(":tests:uniffi:ext-types:custom-types"))
                api(project(":tests:uniffi:ext-types:uniffi-one"))
            }
        }
    }
}

tasks.withType<Test> {
    val dependencyNamespaceNames = arrayOf(
        "gobley_example_custom_types",
        "kmm_ext_types_custom",
        "kmm_uniffi_one",
    )
    for (dependencyNamespace in dependencyNamespaceNames) {
        systemProperties["uniffi.component.$dependencyNamespace.libraryOverride"] = "gobley_fixture_ext_types_proc_macro"
    }
}