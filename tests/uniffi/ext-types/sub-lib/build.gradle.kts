plugins {
    id("uniffi-tests-from-library")
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