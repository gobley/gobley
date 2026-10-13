plugins {
    `kotlin-dsl`
    id("gobley-gradle-build")
}

dependencies {
    implementation(plugin(libs.plugins.kotlin.multiplatform))
    implementation(plugin(libs.plugins.kotlin.atomicfu))
    implementation(plugin(libs.plugins.android.library))

    implementation(project(":gobley-gradle-uniffi"))

    // https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
