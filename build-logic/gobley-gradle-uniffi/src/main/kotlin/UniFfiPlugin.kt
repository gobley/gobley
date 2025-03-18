/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi

import gobley.gradle.DependencyVersions
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.PluginIds
import gobley.gradle.Variant
import gobley.gradle.cargo.dsl.CargoExtension
import gobley.gradle.cargo.dsl.CargoJvmBuild
import gobley.gradle.cargo.dsl.CargoNativeBuild
import gobley.gradle.kotlin.GobleyKotlinAndroidExtensionDelegate
import gobley.gradle.kotlin.GobleyKotlinExtensionDelegate
import gobley.gradle.kotlin.GobleyKotlinJvmExtensionDelegate
import gobley.gradle.kotlin.GobleyKotlinMultiplatformExtensionDelegate
import gobley.gradle.rust.targets.RustTarget
import gobley.gradle.uniffi.dsl.BindingsGeneration
import gobley.gradle.uniffi.dsl.BindingsGenerationFromLibrary
import gobley.gradle.uniffi.dsl.BindingsGenerationFromUdl
import gobley.gradle.uniffi.dsl.UniFfiExtension
import gobley.gradle.uniffi.tasks.BuildBindingsTask
import gobley.gradle.uniffi.tasks.InstallBindgenTask
import gobley.gradle.uniffi.tasks.MergeUniffiConfigTask
import gobley.gradle.utils.DependencyUtils
import gobley.gradle.utils.PluginUtils
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private const val TASK_GROUP = "uniffi"

class UniFfiPlugin : Plugin<Project> {
    private lateinit var uniFfiExtension: UniFfiExtension
    private lateinit var bindingsGeneration: BindingsGeneration
    private lateinit var cargoExtension: CargoExtension

    @OptIn(InternalGobleyGradleApi::class)
    private lateinit var kotlinExtensionDelegate: GobleyKotlinExtensionDelegate

    override fun apply(target: Project) {
        uniFfiExtension = target.extensions.create<UniFfiExtension>(TASK_GROUP, target)
        target.afterEvaluate {
            applyAfterEvaluate(this)
        }
    }

    private fun applyAfterEvaluate(target: Project): Unit = with(target) {
        findRequiredExtensions()
        configureBindingTasks()
        configureKotlin()
        configureCleanTasks()
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.findRequiredExtensions() {
        bindingsGeneration = uniFfiExtension.bindingsGeneration.get()

        PluginUtils.ensurePluginIsApplied(
            this,
            PluginUtils.PluginInfo(
                "Kotlin Multiplatform",
                PluginIds.KOTLIN_MULTIPLATFORM
            ),
            PluginUtils.PluginInfo(
                "Kotlin Android",
                PluginIds.KOTLIN_ANDROID,
            ),
            PluginUtils.PluginInfo(
                "Kotlin JVM",
                PluginIds.KOTLIN_JVM,
            ),
        )
        PluginUtils.ensurePluginIsApplied(project, "Kotlin AtomicFU", PluginIds.KOTLIN_ATOMIC_FU)
        PluginUtils.ensurePluginIsApplied(
            project,
            "Cargo Kotlin Multiplatform",
            PluginIds.GOBLEY_CARGO
        )

        // Since the Cargo Kotlin Multiplatform plugin is present, `CargoExtension` must be present.
        cargoExtension = extensions.getByType()

        plugins.withId(PluginIds.KOTLIN_MULTIPLATFORM) {
            kotlinExtensionDelegate = GobleyKotlinMultiplatformExtensionDelegate(project)
        }
        plugins.withId(PluginIds.KOTLIN_ANDROID) {
            kotlinExtensionDelegate = GobleyKotlinAndroidExtensionDelegate(project)
        }
        plugins.withId(PluginIds.KOTLIN_JVM) {
            kotlinExtensionDelegate = GobleyKotlinJvmExtensionDelegate(project)
        }

        bindingsGeneration.namespace.convention(cargoExtension.cargoPackage.map { it.libraryCrateName })
        (bindingsGeneration as? BindingsGenerationFromUdl)?.udlFile?.convention(
            cargoExtension.cargoPackage.map {
                it.root.file("src/${it.libraryCrateName}.udl")
            }
        )
    }

    private fun Project.configureBindingTasks() {
        val bindingsGeneration = bindingsGeneration

        val buildRustTarget = bindingsGeneration.build.orNull ?: run {
            @OptIn(InternalGobleyGradleApi::class)
            val androidTargetsToBuild = cargoExtension.androidTargetsToBuild.get().toList()

            @OptIn(InternalGobleyGradleApi::class)
            val hasJvmTarget = kotlinExtensionDelegate.targets.any {
                it is KotlinJvmTarget || it is KotlinWithJavaTarget<*, *>
            }

            val jvmTargetsToBuild = when {
                hasJvmTarget -> cargoExtension.builds.mapNotNull { build ->
                    build.rustTarget.takeIf {
                        build is CargoJvmBuild<*> && build.variants.any { variant ->
                            variant.embedRustLibrary.get()
                        }
                    }
                }

                else -> emptyList()
            }

            @OptIn(InternalGobleyGradleApi::class)
            val nativeTargetsToBuild = kotlinExtensionDelegate.targets.mapNotNull { target ->
                val nativeTarget = target as? KotlinNativeTarget ?: return@mapNotNull null
                RustTarget(nativeTarget.konanTarget)
            }

            (androidTargetsToBuild + jvmTargetsToBuild + nativeTargetsToBuild).first()
        }
        val build = cargoExtension.builds.findByRustTarget(buildRustTarget)
            ?: throw GradleException("Cargo build for $buildRustTarget not available")

        val availableVariants = build.kotlinTargets.flatMap {
            when (it) {
                is KotlinJvmTarget, is KotlinWithJavaTarget<*, *> -> listOf((build as CargoJvmBuild<*>).jvmVariant.get())
                is KotlinAndroidTarget -> Variant.values().toList()
                is KotlinNativeTarget -> listOf((build as CargoNativeBuild<*>).nativeVariant.get())
                else -> emptyList<Variant>()
            }
        }.distinct()

        val variant = bindingsGeneration.variant.orNull
            ?: availableVariants.firstOrNull()
            ?: throw GradleException("Cargo build $buildRustTarget has no available variants")

        if (!availableVariants.contains(variant))
            throw GradleException("Variant $variant is not available in Cargo build $buildRustTarget")

        val buildVariantForBindings = build.variant(variant)
        val cargoBuildTaskForBindings = buildVariantForBindings.buildTaskProvider
        val bindingsOutputFile = cargoBuildTaskForBindings.flatMap { task ->
            task.libraryFileByCrateType.map { it.toList().first().second }
        }

        val installBindgen = tasks.register<InstallBindgenTask>("installBindgen") {
            group = TASK_GROUP
            bindgenSource.set(uniFfiExtension.bindgenSource)
            installDirectory.set(layout.buildDirectory.dir("bindgen-install"))
        }

        val mergeUniffiConfig = tasks.register<MergeUniffiConfigTask>("mergeUniffiConfig") {
            group = TASK_GROUP
            originalConfig.set(
                bindingsGeneration.config.orElse(
                    cargoExtension.packageDirectory.file("uniffi.toml"),
                ).map { regularFile ->
                    // TODO: This compiles well, but Android Studio shows an error
                    regularFile.takeIf { it.asFile.exists() }
                }
            )

            packageName.set(bindingsGeneration.packageName)
            cdylibName.set(bindingsGeneration.cdylibName)
            generateImmutableRecords.set(bindingsGeneration.generateImmutableRecords)
            customTypes.set(bindingsGeneration.customTypes)
            disableJavaCleaner.set(bindingsGeneration.disableJavaCleaner)
            usePascalCaseEnumClass.set(bindingsGeneration.usePascalCaseEnumClass)

            @OptIn(InternalGobleyGradleApi::class)
            kotlinMultiplatform.set(kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_MULTIPLATFORM)

            @OptIn(InternalGobleyGradleApi::class)
            kotlinTargets.set(
                kotlinExtensionDelegate.targets.mapNotNull {
                    when (it) {
                        is KotlinMetadataTarget -> null
                        is KotlinJvmTarget, is KotlinWithJavaTarget<*, *> -> "jvm"
                        is KotlinAndroidTarget -> "android"
                        is KotlinNativeTarget -> "native"
                        else -> "stub"
                    }
                }
            )

            @OptIn(InternalGobleyGradleApi::class)
            DependencyUtils.configureEachCommonProjectDependencies(configurations) { dependencyProject ->
                if (dependencyProject.plugins.hasPlugin(PluginIds.GOBLEY_UNIFFI)
                    && dependencyProject.plugins.hasPlugin(PluginIds.GOBLEY_CARGO)
                ) {

                    // Different projects use different class loaders; we need to access those projects'
                    // cargoPackage using reflection.
                    val cargoExtension = dependencyProject.extensions.getByName("cargo")
                    val getCargoPackage = cargoExtension::class.java.getMethod("getCargoPackage")

                    val cargoPackageProvider = getCargoPackage.invoke(cargoExtension) as Provider<*>
                    val cargoPackage = cargoPackageProvider.get()

                    val getLibraryCrateName =
                        cargoPackage::class.java.getMethod("getLibraryCrateName")
                    val libraryCrateName = getLibraryCrateName.invoke(cargoPackage) as String

                    externalPackageConfigByCrateName.put(
                        libraryCrateName,
                        dependencyProject.mergedConfig.map { it.asFile },
                    )
                    dependsOn(dependencyProject.tasks.named("mergeUniffiConfig"))
                }
            }

            @OptIn(InternalGobleyGradleApi::class)
            val kotlinVersionFromExtension = kotlinExtensionDelegate.implementationVersion
            if (kotlinVersionFromExtension != null) {
                kotlinVersion.set(kotlinVersionFromExtension)
            }

            // If the serialization plugin is applied and the runtime is added to the dependency
            // list, set `generateSerializableTypes` to true so the bindgen renders @Serialization
            // where applicable.
            @OptIn(InternalGobleyGradleApi::class)
            if (plugins.hasPlugin(PluginIds.KOTLIN_SERIALIZATION)) {
                @OptIn(InternalGobleyGradleApi::class)
                DependencyUtils.configureEachCommonDependencies(configurations) { dependency ->
                    if (dependency.group == "org.jetbrains.kotlinx"
                        && dependency.name.startsWith("kotlinx-serialization-")
                    ) {
                        useKotlinXSerialization.set(true)
                    }
                }
            }
            outputConfig.set(mergedConfig)
        }

        val buildBindings = tasks.register<BuildBindingsTask>("buildBindings") {
            group = TASK_GROUP

            cargoPackage.set(cargoExtension.cargoPackage)
            bindgen.set(installBindgen.get().bindgen)
            outputDirectory.set(bindingsDirectory)
            if (uniFfiExtension.formatCode.isPresent)
                formatCode.set(uniFfiExtension.formatCode.get())

            config.set(mergeUniffiConfig.flatMap { it.outputConfig })

            configByCrateName.put(
                cargoExtension.cargoPackage.get().libraryCrateName,
                mergedConfig.map { it.asFile },
            )
            rootByCrateName.put(
                cargoExtension.cargoPackage.get().libraryCrateName,
                cargoExtension.cargoPackage.get().root.asFile,
            )
            @OptIn(InternalGobleyGradleApi::class)
            DependencyUtils.configureEachCommonProjectDependencies(configurations) { dependencyProject ->
                if (dependencyProject.plugins.hasPlugin(PluginIds.GOBLEY_UNIFFI)
                    && dependencyProject.plugins.hasPlugin(PluginIds.GOBLEY_CARGO)
                ) {

                    // Different projects use different class loaders; we need to access those projects'
                    // cargoPackage using reflection.
                    val cargoExtension = dependencyProject.extensions.getByName("cargo")
                    val getCargoPackage = cargoExtension::class.java.getMethod("getCargoPackage")

                    val cargoPackageProvider = getCargoPackage.invoke(cargoExtension) as Provider<*>
                    val cargoPackage = cargoPackageProvider.get()

                    val getRoot = cargoPackage::class.java.getMethod("getRoot")
                    val root = getRoot.invoke(cargoPackage) as Directory

                    val getLibraryCrateName =
                        cargoPackage::class.java.getMethod("getLibraryCrateName")
                    val libraryCrateName = getLibraryCrateName.invoke(cargoPackage) as String

                    configByCrateName.put(
                        libraryCrateName,
                        dependencyProject.mergedConfig.map { it.asFile })
                    rootByCrateName.put(libraryCrateName, root.asFile)
                    dependsOn(dependencyProject.tasks.named("mergeUniffiConfig"))
                }
            }

            when (bindingsGeneration) {
                is BindingsGenerationFromUdl -> {
                    libraryMode.set(false)
                    source.set(bindingsGeneration.udlFile)
                }

                is BindingsGenerationFromLibrary -> {
                    libraryMode.set(true)
                    source.set(bindingsOutputFile)
                }
            }
            dependsOn(cargoBuildTaskForBindings, installBindgen, mergeUniffiConfig)
        }

        tasks.withType<KotlinCompilationTask<*>> {
            dependsOn(buildBindings)
        }

        tasks.withType<Jar> {
            dependsOn(buildBindings)
        }

        tasks.withType<CInteropProcess> {
            dependsOn(buildBindings)
        }
    }

    private fun Project.configureCleanTasks() {
        val cleanBindings = tasks.register<Delete>("cleanBindings") {
            group = TASK_GROUP
            delete(bindingsDirectory)
        }

        tasks.named<Delete>("clean") {
            dependsOn(cleanBindings)
        }
    }

    private fun Project.configureKotlin() {
        tasks.withType<KotlinCompilationTask<*>> {
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }

        val dummyDefFile = nativeBindingsCInteropDef("dummy")
        val generateDummyDefFileTask = tasks.register("generateDummyDefFile") {
            doLast {
                dummyDefFile.get().asFile.run {
                    parentFile.mkdirs()
                    writeBytes(byteArrayOf())
                }
            }
            mustRunAfter(tasks.named("buildBindings"))
        }

        @OptIn(InternalGobleyGradleApi::class)
        kotlinExtensionDelegate.targets.configureEach {
            when (this) {
                is KotlinMetadataTarget -> configureKotlinCommonTarget(this)
                is KotlinJvmTarget, is KotlinWithJavaTarget<*, *> -> {
                    if (kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_JVM) {
                        configureKotlinCommonTarget(this)
                    }
                    configureKotlinJvmTarget(this)
                }

                is KotlinAndroidTarget -> {
                    if (kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_ANDROID) {
                        configureKotlinCommonTarget(this)
                    }
                    configureKotlinAndroidTarget(this)
                }

                is KotlinNativeTarget -> configureKotlinNativeTarget(
                    this,
                    dummyDefFile,
                    generateDummyDefFileTask,
                )

                else -> configureUnsupportedTarget(this)
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinCommonTarget(kotlinCommonTarget: KotlinTarget) {
        val mainSourceSet = if (
            kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_ANDROID
            || kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_JVM
        ) {
            kotlinExtensionDelegate.sourceSets.getByName("main").apply {
                kotlin.srcDir(mainBindingsDirectory)
            }
        } else {
            kotlinCommonTarget.compilations.getByName("main").defaultSourceSet.apply {
                kotlin.srcDir(commonBindingsDirectory)
            }
        }

        with(mainSourceSet) {
            dependencies {
                implementation("com.squareup.okio:okio:${DependencyVersions.OKIO}")
                implementation("org.jetbrains.kotlinx:atomicfu:${DependencyVersions.KOTLINX_ATOMICFU}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${DependencyVersions.KOTLINX_DATETIME}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${DependencyVersions.KOTLINX_COROUTINES}")
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinJvmTarget(kotlinJvmTarget: KotlinTarget) {
        val mainSourceSet = if (kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_JVM) {
            kotlinExtensionDelegate.sourceSets.getByName("main")
        } else {
            kotlinJvmTarget.compilations.getByName("main").defaultSourceSet.apply {
                kotlin.srcDir(jvmBindingsDirectory)
            }
        }

        with(mainSourceSet) {
            dependencies {
                implementation("net.java.dev.jna:jna:${DependencyVersions.JNA}")
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinAndroidTarget(kotlinAndroidTarget: KotlinAndroidTarget) {
        @OptIn(InternalGobleyGradleApi::class)
        val mainSourceSet = if (kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_ANDROID) {
            kotlinExtensionDelegate.sourceSets.getByName("main")
        } else {
            kotlinExtensionDelegate.sourceSets.getByName("${kotlinAndroidTarget.name}Main").apply {
                kotlin.srcDir(androidBindingsDirectory)
            }
        }

        with(mainSourceSet) {
            dependencies {
                implementation("net.java.dev.jna:jna:${DependencyVersions.JNA}@aar")
                implementation("androidx.annotation:annotation:${DependencyVersions.ANDROIDX_ANNOTATION}")
            }
        }

        // Use the desktop version of JNA in android local unit tests.
        configureKotlinAndroidUnitTestJna()
        // Make android unit tests in dependent projects also use the desktop version of JNA.
        @OptIn(InternalGobleyGradleApi::class)
        DependencyUtils.configureEachDependentProjects(project) { dependentProject ->
            dependentProject.configureKotlinAndroidUnitTestJna()
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinAndroidUnitTestJna() {
        val androidPluginAction = Action<Plugin<*>> {
            dependencies {
                add("testImplementation", "net.java.dev.jna:jna:${DependencyVersions.JNA}")
            }
        }
        plugins.withId(PluginIds.ANDROID_APPLICATION, androidPluginAction)
        plugins.withId(PluginIds.ANDROID_LIBRARY, androidPluginAction)
    }

    private fun Project.configureKotlinNativeTarget(
        kotlinNativeTarget: KotlinNativeTarget,
        dummyDefFile: Provider<RegularFile>,
        generateDummyDefFileTask: TaskProvider<Task>,
    ) {
        val namespace = bindingsGeneration.namespace.get()
        kotlinNativeTarget.compilations.getByName("main") {
            cinterops.register(TASK_GROUP) {
                packageName("$namespace.cinterop")
                header(project.nativeBindingsCInteropHeader(namespace))
                // Since linking is handled by CargoPlugin and header is fed above, we don't need the defFile.
                defFile(dummyDefFile)
                tasks.named(interopProcessingTaskName) {
                    inputs.file(dummyDefFile)
                    dependsOn(generateDummyDefFileTask)
                }
            }
            defaultSourceSet {
                kotlin.srcDir(nativeBindingsDirectory)
            }
            compileTaskProvider.configure {
                compilerOptions.optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
    }

    private fun Project.configureUnsupportedTarget(kotlinTarget: KotlinTarget) {
        kotlinTarget.compilations.getByName("main").defaultSourceSet {
            kotlin.srcDir(stubBindingsDirectory)
        }
    }
}

private val Project.bindingsDirectory: Provider<Directory>
    get() = layout.buildDirectory.dir("generated/uniffi")

private val Project.mainBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("main/kotlin") }

private val Project.commonBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("commonMain/kotlin") }

private val Project.jvmBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("jvmMain/kotlin") }

private val Project.androidBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("androidMain/kotlin") }

private val Project.nativeBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("nativeMain/kotlin") }

private val Project.stubBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("stubMain/kotlin") }

private val Project.nativeBindingsCInteropDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("nativeInterop/cinterop") }

private val Project.mergedConfig: Provider<RegularFile>
    get() = bindingsDirectory.map { it.file("uniffi.toml") }

private fun Project.nativeBindingsCInteropDef(libraryCrateName: String): Provider<RegularFile> =
    nativeBindingsCInteropDirectory.map { it.file("$libraryCrateName.def") }

private fun Project.nativeBindingsCInteropHeader(namespace: String): Provider<RegularFile> =
    nativeBindingsCInteropDirectory.map { it.file("headers/$namespace/$namespace.h") }
