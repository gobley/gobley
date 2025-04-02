plugins {
    id("uniffi-tests-from-library")
}

dependencies {
    add("uniFfiImplementation", project(":tests:uniffi:ext-types:uniffi-one"))
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":tests:uniffi:ext-types:uniffi-one"))
            }
        }
    }
}