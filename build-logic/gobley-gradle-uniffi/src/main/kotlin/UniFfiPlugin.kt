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
import gobley.gradle.android.GobleyAndroidExtensionDelegate
import gobley.gradle.cargo.dsl.CargoExtension
import gobley.gradle.cargo.dsl.CargoJvmBuild
import gobley.gradle.cargo.dsl.CargoNativeBuild
import gobley.gradle.kotlin.GobleyKotlinExtensionDelegate
import gobley.gradle.rust.CrateType
import gobley.gradle.rust.targets.RustTarget
import gobley.gradle.rust.targets.RustWasmTarget
import gobley.gradle.uniffi.dsl.BindingsGeneration
import gobley.gradle.uniffi.dsl.BindingsGenerationFromLibrary
import gobley.gradle.uniffi.dsl.BindingsGenerationFromUdl
import gobley.gradle.uniffi.dsl.UniFfiExtension
import gobley.gradle.uniffi.tasks.BuildUniffiBindingsTask
import gobley.gradle.uniffi.tasks.GenerateUniffiProguardRulesTask
import gobley.gradle.uniffi.tasks.InstallUniffiBindgenTask
import gobley.gradle.uniffi.tasks.MergeUniffiConfigTask
import gobley.gradle.utils.DependencyUtils
import gobley.gradle.utils.GradleUtils
import gobley.gradle.utils.PluginUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.com.intellij.util.containers.ContainerUtil.mapNotNull
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.io.File

private const val TASK_GROUP = "uniffi"

class UniFfiPlugin : Plugin<Project> {
    private lateinit var uniFfiExtension: UniFfiExtension
    private lateinit var bindingsGeneration: BindingsGeneration
    private lateinit var cargoExtension: CargoExtension

    @OptIn(InternalGobleyGradleApi::class)
    private var kotlinExtensionDelegate: GobleyKotlinExtensionDelegate? = null

    @OptIn(InternalGobleyGradleApi::class)
    private var androidDelegate: GobleyAndroidExtensionDelegate? = null

    override fun apply(target: Project) {
        @OptIn(InternalGobleyGradleApi::class)
        if (!target.plugins.hasPlugin(PluginIds.GOBLEY_RUST)) {
            DependencyUtils.createUniFfiConfigurations(target)
        }
        uniFfiExtension = target.extensions.create<UniFfiExtension>(TASK_GROUP, target)
        target.afterEvaluate {
            applyAfterEvaluate(this)
        }
    }

    private fun applyAfterEvaluate(target: Project): Unit = with(target) {
        findRequiredExtensions()
        checkKotlinTargets()

        // Pass the implicitly wired task down the chain!
        val buildBindingsTask = configureBindingTasks()
        configureKotlin(buildBindingsTask)

        configureCleanTasks()

        @OptIn(InternalGobleyGradleApi::class)
        DependencyUtils.resolveUniFfiDependencies(target)
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
                "Kotlin JVM",
                PluginIds.KOTLIN_JVM,
            ),
            PluginUtils.PluginInfo(
                "Android Application",
                PluginIds.ANDROID_APPLICATION,
            ),
            PluginUtils.PluginInfo(
                "Android Library",
                PluginIds.ANDROID_LIBRARY,
            ),
            PluginUtils.PluginInfo(
                "Android Kotlin Multiplatform Library",
                PluginIds.ANDROID_KOTLIN_MULTIPLATFORM_LIBRARY,
            ),
        )
        PluginUtils.ensurePluginIsApplied(project, "Kotlin AtomicFU", PluginIds.KOTLIN_ATOMIC_FU)
        PluginUtils.ensurePluginIsApplied(
            project,
            "Cargo Kotlin Multiplatform",
            PluginIds.GOBLEY_CARGO
        )

        cargoExtension = extensions.getByType()

        PluginUtils.withKotlinPlugin(this) { delegate ->
            kotlinExtensionDelegate = delegate
        }
        PluginUtils.withAndroidPlugin(this) { delegate ->
            androidDelegate = delegate
        }

        bindingsGeneration.namespace.convention(cargoExtension.cargoPackage.map { it.libraryCrateName })
        (bindingsGeneration as? BindingsGenerationFromUdl)?.udlFile?.convention(
            cargoExtension.cargoPackage.map {
                it.root.file("src/${it.libraryCrateName}.udl")
            }
        )
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.checkKotlinTargets() {
        val hasJsTargets = kotlinExtensionDelegate?.targets.orEmpty().any { it.platformType == KotlinPlatformType.js }
        if (hasJsTargets) {
            project.logger.warn("JS targets are added, but the UniFFI plugin does not support JS targets yet.")
        }

        val hasWasmTargets = kotlinExtensionDelegate?.targets.orEmpty().any { it.platformType == KotlinPlatformType.wasm }
        if (hasWasmTargets) {
            project.logger.warn("WASM targets are added, but the UniFFI plugin does not support WASM targets yet.")
        }
    }

    private fun Project.configureBindingTasks(): TaskProvider<BuildUniffiBindingsTask> {
        val bindingsGeneration = bindingsGeneration

        val buildRustTarget = bindingsGeneration.build.orNull ?: run {
            @OptIn(InternalGobleyGradleApi::class)
            val androidTargetsToBuild = cargoExtension.androidTargetsToBuild.get().toList()

            @OptIn(InternalGobleyGradleApi::class)
            val hasJvmTarget = kotlinExtensionDelegate?.targets.orEmpty().any {
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
            val nativeTargetsToBuild = kotlinExtensionDelegate?.targets.orEmpty().mapNotNull { target ->
                val nativeTarget = target as? KotlinNativeTarget ?: return@mapNotNull null
                RustTarget(nativeTarget.konanTarget)
            }

            (androidTargetsToBuild + jvmTargetsToBuild + nativeTargetsToBuild).first()
        }

        if (buildRustTarget is RustWasmTarget) {
            throw GradleException("$buildRustTarget not available for UniFFI. Try building with other targets.")
        }

        val build = cargoExtension.builds.findByRustTarget(buildRustTarget)
            ?: throw GradleException("Cargo build for $buildRustTarget not available")

        val availableVariants = build.kotlinTargets.flatMap {
            when (it) {
                is KotlinJvmTarget, is KotlinWithJavaTarget<*, *> -> listOf((build as CargoJvmBuild<*>).jvmVariant.get())
                is KotlinAndroidTarget -> Variant.entries
                is KotlinNativeTarget -> listOf((build as CargoNativeBuild<*>).nativeVariant.get())
                else -> listOf(Variant.Debug)
            }
        }.distinct().ifEmpty {
            @OptIn(InternalGobleyGradleApi::class)
            if (androidDelegate != null) Variant.entries else emptyList()
        }

        val variant = bindingsGeneration.variant.orNull
            ?: availableVariants.firstOrNull()
            ?: throw GradleException("Cargo build $buildRustTarget has no available variants")

        if (!availableVariants.contains(variant))
            throw GradleException("Variant $variant is not available in Cargo build $buildRustTarget")

        val buildVariantForBindings = build.variant(variant)
        val cargoBuildTaskForBindings = buildVariantForBindings.buildTaskProvider
        val bindingsOutputFile = cargoBuildTaskForBindings.flatMap { task ->
            task.libraryFileByCrateType.map {
                it[CrateType.SystemDynamicLibrary] ?: it.values.first()
            }
        }

        val installBindgen = tasks.register<InstallUniffiBindgenTask>("installUniffiBindgen") {
            group = TASK_GROUP
            binaryCrateSource.set(uniFfiExtension.bindgenSource)
            installDirectory.set(layout.buildDirectory.dir("gobley-tools-install/uniffi-bindgen"))
        }

        @OptIn(InternalGobleyGradleApi::class)
        val externalPackageUniFfiConfigurations = DependencyUtils.getExternalPackageUniFfiConfigurations(this)

        val mergeUniffiConfig = tasks.register<MergeUniffiConfigTask>("mergeUniffiConfig") {
            group = TASK_GROUP
            originalConfig.set(
                bindingsGeneration.config.orElse(
                    cargoExtension.packageDirectory.file("uniffi.toml"),
                ).map { regularFile ->
                    regularFile.takeIf { it.asFile.exists() }
                }
            )

            crateName.set(cargoExtension.cargoPackage.map { it.libraryCrateName })
            packageRoot.set(cargoExtension.cargoPackage.map { it.root.asFile.path })
            packageName.set(bindingsGeneration.packageName)
            cdylibName.set(bindingsGeneration.cdylibName)
            generateImmutableRecords.set(bindingsGeneration.generateImmutableRecords)
            omitChecksums.set(bindingsGeneration.omitChecksums)
            customTypes.set(bindingsGeneration.customTypes)
            disableJavaCleaner.set(bindingsGeneration.disableJavaCleaner)
            usePascalCaseEnumClass.set(bindingsGeneration.usePascalCaseEnumClass)
            @OptIn(InternalGobleyGradleApi::class)
            enableJnaInterfaceMapping.set(bindingsGeneration.enableJnaInterfaceMapping)

            @OptIn(InternalGobleyGradleApi::class)
            kotlinMultiplatform.set(kotlinExtensionDelegate?.pluginId == PluginIds.KOTLIN_MULTIPLATFORM)

            @OptIn(InternalGobleyGradleApi::class)
            kotlinTargets.set(project.provider { // THE FIX: Wrap in a lazy provider!
                if (kotlinExtensionDelegate != null) {
                    kotlinExtensionDelegate!!.targets.mapNotNull { target ->
                        when (target.platformType.name) {
                            "common" -> null
                            "jvm" -> "jvm"
                            "androidJvm" -> "android" // Safely catches the new KMP Android target
                            "native" -> "native"
                            else -> "stub"
                        }
                    }
                } else if (androidDelegate != null) {
                    listOf("android")
                } else {
                    emptyList()
                }
            })

            if (externalPackageUniFfiConfigurations != null) {
                externalPackageConfigs.addAll(externalPackageUniFfiConfigurations)
            }

            @OptIn(InternalGobleyGradleApi::class)
            val kotlinVersionFromExtension = kotlinExtensionDelegate?.implementationVersion
            if (kotlinVersionFromExtension != null) {
                kotlinVersion.set(kotlinVersionFromExtension)
            }

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

        @OptIn(InternalGobleyGradleApi::class)
        DependencyUtils.addMergedUniffiConfigArtifact(this, mergeUniffiConfig)

        val buildBindings = tasks.register<BuildUniffiBindingsTask>("buildUniffiBindings") {
            group = TASK_GROUP

            cargoPackage.set(cargoExtension.cargoPackage)
            bindgen.set(installBindgen.get().bindgen)

            rawOutputDirectory.set(layout.buildDirectory.dir("intermediates/uniffi/raw"))

            commonMainOutputDir.set(layout.buildDirectory.dir("generated/uniffi/commonMain"))
            mainOutputDir.set(layout.buildDirectory.dir("generated/uniffi/main"))
            jvmMainOutputDir.set(layout.buildDirectory.dir("generated/uniffi/jvmMain"))
            androidMainOutputDir.set(layout.buildDirectory.dir("generated/uniffi/androidMain"))
            nativeMainOutputDir.set(layout.buildDirectory.dir("generated/uniffi/nativeMain"))
            stubMainOutputDir.set(layout.buildDirectory.dir("generated/uniffi/stubMain"))

            cinteropOutputDir.set(layout.buildDirectory.dir("generated/uniffi/cinterop"))

            @OptIn(InternalGobleyGradleApi::class)
            multiplatformMode.set(kotlinExtensionDelegate?.pluginId == PluginIds.KOTLIN_MULTIPLATFORM)

            if (uniFfiExtension.formatCode.isPresent)
                formatCode.set(uniFfiExtension.formatCode.get())

            config.set(mergeUniffiConfig.flatMap { it.outputConfig })

            if (externalPackageUniFfiConfigurations != null) {
                externalPackageConfigs.addAll(externalPackageUniFfiConfigurations)
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

        if (uniFfiExtension.generateDuringSync.get()) {
            @OptIn(InternalGobleyGradleApi::class)
            GradleUtils.runTaskDuringSync(this, buildBindings)
        }

        @OptIn(InternalGobleyGradleApi::class)
        if (androidDelegate != null) {
            val generateUniffiProguardRulesTask =
                tasks.register<GenerateUniffiProguardRulesTask>("generateUniffiProguardRules") {
                    outputFile.set(androidGeneratedProguardFile)
                }

            if (uniFfiExtension.generateProguardRules.get()) {
                androidDelegate!!.addProguardFiles(
                    project,
                    androidGeneratedProguardFile,
                    generateUniffiProguardRulesTask,
                )
            }
        }

        return buildBindings
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

    private fun Project.configureKotlin(buildBindingsTask: TaskProvider<BuildUniffiBindingsTask>) {
        // Explicit Java classes and configureEach for configuration cache safety
        tasks.withType(KotlinCompilationTask::class.java).configureEach {
            compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
        }

        val dummyDefFile = nativeBindingsCInteropDef("dummy")
        val generateDummyDefFileTask = tasks.register("generateDummyDefFile") {
            doLast {
                dummyDefFile.get().asFile.run {
                    parentFile.mkdirs()
                    writeBytes(byteArrayOf())
                }
            }
            mustRunAfter(buildBindingsTask)
        }

        @OptIn(InternalGobleyGradleApi::class)
        if (kotlinExtensionDelegate != null) {
            // === KMP OR PURE JVM (JETBRAINS KGP) ===
            kotlinExtensionDelegate!!.targets.configureEach {
                // THE FIX: Check platformType FIRST to catch the new AGP 9 KMP targets!
                when {
                    platformType.name == "common" -> configureKotlinCommonTarget(buildBindingsTask)
                    platformType.name == "androidJvm" -> {
                        // This safely catches BOTH the classic JetBrains KotlinAndroidTarget
                        // AND the new Google AGP 9 Android Target!
                        configureKotlinAndroidTarget(buildBindingsTask)
                    }
                    this is KotlinJvmTarget || this is KotlinWithJavaTarget<*, *> -> {
                        if (kotlinExtensionDelegate!!.pluginId == PluginIds.KOTLIN_JVM) {
                            configureKotlinCommonTarget(buildBindingsTask)
                        }
                        configureKotlinJvmTarget(buildBindingsTask)
                    }
                    this is KotlinNativeTarget -> configureKotlinNativeTarget(
                        this,
                        dummyDefFile,
                        generateDummyDefFileTask,
                        buildBindingsTask
                    )
                    else -> configureUnsupportedTarget(this, buildBindingsTask)
                }
            }
        } else if (androidDelegate != null) {
            // === PURE ANDROID (AGP 9 BUILT-IN KOTLIN) ===
            // Manually trigger the configurations since JetBrains KGP isn't here to loop!
            configureKotlinCommonTarget(buildBindingsTask)
            configureKotlinAndroidTarget(buildBindingsTask)
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinCommonTarget(buildBindings: TaskProvider<BuildUniffiBindingsTask>) {
        if (kotlinExtensionDelegate != null) {
            val targetSourceCollection = project.files(buildBindings.flatMap { it.commonMainOutputDir }).builtBy(buildBindings)
            with(kotlinExtensionDelegate!!.sourceSets.commonMain) {
                kotlin.srcDir(targetSourceCollection)
            }
        } else if (androidDelegate != null) {
            androidDelegate!!.addGeneratedBindingsDirectory(project, buildBindings) { task ->
                task.mainOutputDir // Pure Android uses the 'main' output!
            }
        }

        if (uniFfiExtension.addDependencies.get()) {
            if (kotlinExtensionDelegate != null) {
                with(kotlinExtensionDelegate!!.sourceSets.commonMain) {
                    dependencies {
                        implementation("org.jetbrains.kotlinx:atomicfu") {
                            version { prefer(DependencyVersions.KOTLINX_ATOMICFU) }
                        }
                        implementation("org.jetbrains.kotlinx:kotlinx-datetime") {
                            version { prefer(DependencyVersions.KOTLINX_DATETIME) }
                        }
                        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core") {
                            version { prefer(DependencyVersions.KOTLINX_COROUTINES) }
                        }
                    }
                }
            } else {
                addNativeDependency("implementation", "org.jetbrains.kotlinx:atomicfu", DependencyVersions.KOTLINX_ATOMICFU)
                addNativeDependency("implementation", "org.jetbrains.kotlinx:kotlinx-datetime", DependencyVersions.KOTLINX_DATETIME)
                addNativeDependency("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core", DependencyVersions.KOTLINX_COROUTINES)
            }
        }
    }

    private fun Project.addNativeDependency(
        configurationName: String,
        moduleNotation: String,
        preferredVersion: String
    ) {
        val dependency = dependencies.add(configurationName, moduleNotation)
        if (dependency is ExternalModuleDependency) {
            dependency.version {
                prefer(preferredVersion)
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinJvmTarget(buildBindings: TaskProvider<BuildUniffiBindingsTask>) {
        val delegate = kotlinExtensionDelegate ?: return

        if (delegate.pluginId != PluginIds.KOTLIN_MULTIPLATFORM && delegate.pluginId != PluginIds.KOTLIN_JVM) {
            return
        }

        with(delegate.sourceSets.jvmMain) {
            if (delegate.pluginId == PluginIds.KOTLIN_MULTIPLATFORM) {
                kotlin.srcDir(buildBindings.flatMap { it.jvmMainOutputDir })
            }

            if (uniFfiExtension.addDependencies.get()) {
                dependencies {
                    implementation("net.java.dev.jna:jna") {
                        version { prefer(DependencyVersions.JNA) }
                    }
                }
            }
        }
    }

    private fun Project.getNativeConflictingDependencyVersion(
        configurationName: String,
        group: String,
        name: String
    ): String? {
        val config = configurations.findByName(configurationName) ?: return null
        val dep = config.dependencies.find { it.group == group && it.name == name }
        return dep?.version
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinAndroidTarget(buildBindings: TaskProvider<BuildUniffiBindingsTask>) {
        val isKmp = kotlinExtensionDelegate?.pluginId == PluginIds.KOTLIN_MULTIPLATFORM
        val hasAndroid = androidDelegate != null || isKmp

        if (!hasAndroid) return

        if (isKmp) {
            val kgpDelegate = requireNotNull(kotlinExtensionDelegate)

            with(kgpDelegate.sourceSets.androidMain) {
                kotlin.srcDir(buildBindings.flatMap { it.androidMainOutputDir })

                if (uniFfiExtension.addDependencies.get()) {
                    dependencies {
                        implementation("net.java.dev.jna:jna@aar") {
                            version { prefer(DependencyVersions.JNA) }
                        }
                        implementation("androidx.annotation:annotation") {
                            version { prefer(DependencyVersions.KOTLINX_COROUTINES) }
                        }
                    }
                }
            }

            val jnaDependency = kgpDelegate.sourceSets.androidMain.getConflictingDependency(
                "net.java.dev.jna:jna:${DependencyVersions.JNA}"
            )
            val jnaVersion = jnaDependency?.version ?: DependencyVersions.JNA
            val composePreviewVariant = GradleUtils.getComposePreviewVariant(gradle)

            if (composePreviewVariant != null) {
                with(kgpDelegate.sourceSets.androidMain(composePreviewVariant)) {
                    if (uniFfiExtension.addDependencies.get()) {
                        dependencies {
                            implementation("net.java.dev.jna:jna") {
                                version { prefer(jnaVersion) }
                            }
                        }
                    }
                }
            }

            with(kgpDelegate.sourceSets.androidUnitTest) {
                if (uniFfiExtension.addDependencies.get()) {
                    dependencies {
                        implementation("net.java.dev.jna:jna") {
                            version { prefer(jnaVersion) }
                        }
                    }
                }
            }

        } else {
            if (uniFfiExtension.addDependencies.get()) {
                addNativeDependency("implementation", "net.java.dev.jna:jna@aar", DependencyVersions.JNA)
                addNativeDependency("implementation", "androidx.annotation:annotation", DependencyVersions.KOTLINX_COROUTINES)

                val jnaVersion = getNativeConflictingDependencyVersion(
                    configurationName = "implementation",
                    group = "net.java.dev.jna",
                    name = "jna"
                ) ?: DependencyVersions.JNA

                val composePreviewVariant = GradleUtils.getComposePreviewVariant(gradle)
                if (composePreviewVariant != null) {
                    val variantPrefix = when (composePreviewVariant) {
                        Variant.Debug -> "debug"
                        Variant.Release -> "release"
                        null -> ""
                    }

                    val configName = if (variantPrefix.isEmpty()) "implementation" else "${variantPrefix}Implementation"
                    addNativeDependency(configName, "net.java.dev.jna:jna", jnaVersion)
                }

                addNativeDependency("testImplementation", "net.java.dev.jna:jna", jnaVersion)
            }

            // === THE FIX for Pure Android Local Unit Tests ===

            // 1. Pure Android modules only build .so files natively.
            // We MUST force Cargo to compile the host .dylib for macOS JVM testing!
            val crateRootFile = cargoExtension.cargoPackage.get().root.asFile
            val buildHostLibraryForTests = tasks.register("buildHostLibraryForAndroidTests", org.gradle.api.tasks.Exec::class.java) {
                workingDir = crateRootFile
                commandLine("cargo", "build")
            }

            // 2. Safely resolve the Cargo workspace target directory (usually at the root of the repo)
            // We extract absolutePath as a string first to completely avoid capturing `Project` for the Configuration Cache!
            val rootDirPath = project.rootProject.layout.projectDirectory.asFile.absolutePath
            val jnaPathProvider = project.provider {
                java.io.File(rootDirPath, "target/debug").absolutePath
            }

            // 3. Pass the completely insulated Provider class to the test tasks AND make them wait for Cargo to finish
            tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
                dependsOn(buildHostLibraryForTests)
                jvmArgumentProviders.add(JnaLibraryPathProvider(jnaPathProvider))
            }
        }
    }

    private fun Project.configureKotlinNativeTarget(
        kotlinNativeTarget: KotlinNativeTarget,
        dummyDefFile: Provider<RegularFile>,
        generateDummyDefFileTask: TaskProvider<Task>,
        buildBindings: TaskProvider<BuildUniffiBindingsTask>
    ) {
        val namespace = bindingsGeneration.namespace.get()
        kotlinNativeTarget.compilations.getByName("main") {
            cinterops.register(TASK_GROUP) {
                packageName("$namespace.cinterop")

                // 1. Map straight into the new isolated C-Interop directory!
                // Since we flattened the output, 'headers' is right at the root of the output dir.
                val headerFile = buildBindings.flatMap {
                    it.cinteropOutputDir.file("headers/$namespace/$namespace.h")
                }
                header(headerFile)

                defFile(dummyDefFile)

                tasks.named(interopProcessingTaskName) {
                    inputs.file(dummyDefFile)
                    dependsOn(generateDummyDefFileTask)

                    // 2. Force the hard task dependency!
                    // The KMP C-Interop DSL is too old to understand implicit Provider dependencies.
                    dependsOn(buildBindings)
                }
            }
            defaultSourceSet {
                // 3. Point directly to the new isolated Native Kotlin output!
                kotlin.srcDir(buildBindings.flatMap { it.nativeMainOutputDir })
            }
            compileTaskProvider.configure {
                compilerOptions.optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
    }

    private fun Project.configureUnsupportedTarget(kotlinTarget: KotlinTarget, buildBindings: TaskProvider<BuildUniffiBindingsTask>) {
        kotlinTarget.compilations.getByName("main").defaultSourceSet {
            // Point directly to the new isolated Stub Kotlin output
            kotlin.srcDir(buildBindings.flatMap { it.stubMainOutputDir })
        }
    }
}

private val Project.bindingsDirectory: Provider<Directory>
    get() = layout.buildDirectory.dir("generated/uniffi")

private val Project.androidGeneratedProguardFile: Provider<RegularFile>
    get() = bindingsDirectory.map { it.file("androidMain/generated-proguard-rules.txt") }

private val Project.mergedConfig: Provider<RegularFile>
    get() = layout.buildDirectory.file("intermediates/merged_uniffi_config/uniffi.toml")

private fun Project.nativeBindingsCInteropDef(libraryCrateName: String): Provider<RegularFile> =
    bindingsDirectory.map { it.file("nativeInterop/cinterop/$libraryCrateName.def") }

private fun KotlinSourceSet.getConflictingDependency(
    dependencyNotation: String,
): ExternalModuleDependency? {
    val dependencyToAdd =
        project.dependencies.create(dependencyNotation) as ExternalModuleDependency
    val configuration = project.configurations.getByName(implementationConfigurationName)
    return configuration.dependencies.firstOrNull { dependency ->
        dependency is ExternalModuleDependency
                && dependency.module.group == dependencyToAdd.module.group
                && dependency.module.name == dependencyToAdd.module.name
    } as? ExternalModuleDependency
}

// This class is explicitly declared outside of the Project scope
// so it cannot accidentally capture illegal Gradle objects!
class JnaLibraryPathProvider(
    @get:Input
    val libraryPath: Provider<String>
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        val path = libraryPath.get()
        return listOf(
            "-Djna.library.path=$path",
            "-Djava.library.path=$path"
        )
    }
}