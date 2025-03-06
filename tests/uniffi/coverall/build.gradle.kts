plugins {
    id("uniffi-tests-from-library")
}

kotlin {
    js {
        nodejs()
        browser()
    }
}