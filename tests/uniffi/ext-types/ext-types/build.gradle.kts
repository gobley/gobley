plugins {
    id("uniffi-tests")
}

dependencies {
    add("uniFfiImplementation", project(":examples:custom-types"))
    add("uniFfiImplementation", project(":tests:uniffi:ext-types:custom-types"))
    add("uniFfiImplementation", project(":tests:uniffi:ext-types:uniffi-one"))
    add("uniFfiImplementation", project(":tests:uniffi:ext-types:sub-lib"))
}

uniffi {
    generateFromLibrary {
        namespace = "imported_types_lib"
    }
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.http)
                api(project(":examples:custom-types"))
                api(project(":tests:uniffi:ext-types:custom-types"))
                api(project(":tests:uniffi:ext-types:uniffi-one"))
                api(project(":tests:uniffi:ext-types:sub-lib"))
            }
        }
    }
}

tasks.withType<Test> {
    val dependencyNamespaceNames = arrayOf(
        "gobley_example_custom_types",
        "kmm_ext_types_custom",
        "kmm_uniffi_one",
        "sub_lib",
    )
    for (dependencyNamespace in dependencyNamespaceNames) {
        systemProperties["uniffi.component.$dependencyNamespace.libraryOverride"] = "gobley_fixture_ext_types"
    }
}