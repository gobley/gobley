import gobley.gradle.cargo.dsl.jvm

plugins {
    id("uniffi-tests-from-library")
}

cargo {
    builds.jvm {
        dynamicLibraries.add("gobley_fixture_dynamic_library_dependencies_the_dependency")
    }
}
