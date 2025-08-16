import com.github.gmazzo.buildconfig.generators.BuildConfigKotlinGenerator
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildconfig)
    `maven-publish`
    alias(libs.plugins.vanniktech.maven.publish)
    id("gobley-gradle-build")
}

gobleyGradleBuild {
    configureGobleyGradleProject(
        description = "Common types used by Gobley Gradle plugins.",
    )
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.kotlin.android))
    compileOnly(plugin(libs.plugins.kotlin.jvm))
    compileOnly(plugin(libs.plugins.kotlin.multiplatform))
    compileOnly(plugin(libs.plugins.android.application))
    compileOnly(plugin(libs.plugins.android.library))

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.semver)
}

buildConfig {
    packageName = "gobley.gradle"
    generator = object : BuildConfigKotlinGenerator() {
        override fun adaptSpec(spec: TypeSpec): TypeSpec {
            val builder = TypeSpec.objectBuilder(spec.name!!)
            builder.modifiers += KModifier.PUBLIC
            builder.addAnnotation(ClassName("gobley.gradle", "InternalGobleyGradleApi"))
            for (property in spec.propertySpecs) {
                builder.addProperty(
                    property.toBuilder()
                        .apply {
                            modifiers.clear()
                            modifiers += KModifier.PUBLIC
                        }
                        .build()
                )
            }
            return builder.build()
        }
    }

    val uniffiBindgenManifest = gobleyGradleBuild.uniffiBindgenManifest
    buildConfigField("String", "UNIFFI_BINDGEN_VERSION", "\"${uniffiBindgenManifest.version}\"")
    buildConfigField("String", "UNIFFI_BINDGEN_CRATE", "\"${uniffiBindgenManifest.name}\"")
    buildConfigField("String", "UNIFFI_BINDGEN_BIN", "\"${uniffiBindgenManifest.firstBinaryName}\"")

    val wasmTransformerManifest = gobleyGradleBuild.wasmTransformerManifest
    buildConfigField("String", "WASM_TRANSFORMER_VERSION" ,"\"${wasmTransformerManifest.version}\"")
    buildConfigField("String", "WASM_TRANSFORMER_CRATE", "\"${wasmTransformerManifest.name}\"")
    buildConfigField("String", "WASM_TRANSFORMER_BIN", "\"${wasmTransformerManifest.firstBinaryName}\"")

    forClass("DependencyVersions") {
        buildConfigField("String", "KOTLINX_ATOMICFU", "\"${libs.versions.kotlinx.atomicfu.get()}\"")
        buildConfigField("String", "KOTLINX_DATETIME", "\"${libs.versions.kotlinx.datetime.get()}\"")
        buildConfigField("String", "KOTLINX_COROUTINES", "\"${libs.versions.kotlinx.coroutines.get()}\"")
        buildConfigField("String", "JNA", "\"${libs.versions.jna.get()}\"")
        buildConfigField("String", "ANDROIDX_ANNOTATION", "\"${libs.versions.androidx.annotation.get()}\"")
    }

    forClass("PluginIds") {
        buildConfigField("String", "KOTLIN_ANDROID", "\"${libs.plugins.kotlin.android.get().pluginId}\"")
        buildConfigField("String", "KOTLIN_JVM", "\"${libs.plugins.kotlin.jvm.get().pluginId}\"")
        buildConfigField("String", "KOTLIN_MULTIPLATFORM", "\"${libs.plugins.kotlin.multiplatform.get().pluginId}\"")
        buildConfigField("String", "KOTLIN_ATOMIC_FU", "\"${libs.plugins.kotlin.atomicfu.get().pluginId}\"")
        buildConfigField("String", "KOTLIN_SERIALIZATION", "\"${libs.plugins.kotlin.serialization.get().pluginId}\"")
        buildConfigField("String", "ANDROID_APPLICATION", "\"${libs.plugins.android.application.get().pluginId}\"")
        buildConfigField("String", "ANDROID_LIBRARY", "\"${libs.plugins.android.library.get().pluginId}\"")
        buildConfigField("String", "GOBLEY_RUST", "\"dev.gobley.rust\"")
        buildConfigField("String", "GOBLEY_CARGO", "\"dev.gobley.cargo\"")
        buildConfigField("String", "GOBLEY_UNIFFI", "\"dev.gobley.uniffi\"")
    }
}
