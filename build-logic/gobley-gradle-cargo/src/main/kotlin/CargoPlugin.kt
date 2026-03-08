/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.gradle.internal.tasks.factory.dependsOn
import gobley.gradle.AppleSdk
import gobley.gradle.GobleyHost
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.PluginIds
import gobley.gradle.Variant
import gobley.gradle.android.GobleyAndroidExtensionDelegate
import gobley.gradle.tasks.InjectJniLibsTask
import gobley.gradle.cargo.dsl.CargoAndroidBuild
import gobley.gradle.cargo.dsl.CargoAndroidBuildVariant
import gobley.gradle.cargo.dsl.CargoExtension
import gobley.gradle.cargo.dsl.CargoJvmBuild
import gobley.gradle.cargo.dsl.CargoJvmBuildVariant
import gobley.gradle.cargo.dsl.CargoNativeBuild
import gobley.gradle.cargo.dsl.CargoNativeBuildVariant
import gobley.gradle.cargo.dsl.CargoWasmBuild
import gobley.gradle.cargo.dsl.CargoWasmBuildVariant
import gobley.gradle.cargo.dsl.jvm
import gobley.gradle.cargo.dsl.native
import gobley.gradle.cargo.dsl.wasm
import gobley.gradle.cargo.tasks.CargoCleanTask
import gobley.gradle.cargo.tasks.CargoTask
import gobley.gradle.cargo.tasks.InstallWasmTransformerTask
import gobley.gradle.cargo.tasks.RustUpTargetAddTask
import gobley.gradle.cargo.tasks.RustUpTask
import gobley.gradle.cargo.utils.register
import gobley.gradle.kotlin.GobleyKotlinExtensionDelegate
import gobley.gradle.rust.CrateType
import gobley.gradle.rust.targets.RustAndroidTarget
import gobley.gradle.rust.targets.RustJvmTarget
import gobley.gradle.rust.targets.RustTarget
import gobley.gradle.rust.targets.RustWasmTarget
import gobley.gradle.tasks.useGlobalLock
import gobley.gradle.utils.DependencyUtils
import gobley.gradle.utils.GradleUtils
import gobley.gradle.utils.PluginUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import kotlin.reflect.full.superclasses


class CargoPlugin : Plugin<Project> {
    companion object {
        internal const val TASK_GROUP = "cargo"
    }

    private lateinit var cargoExtension: CargoExtension

    @OptIn(InternalGobleyGradleApi::class)
    private var kotlinExtensionDelegate: GobleyKotlinExtensionDelegate? = null

    @OptIn(InternalGobleyGradleApi::class)
    private var androidDelegate: GobleyAndroidExtensionDelegate? = null

    override fun apply(target: Project) {
        @OptIn(InternalGobleyGradleApi::class)
        if (!target.plugins.hasPlugin(PluginIds.GOBLEY_RUST)) {
            DependencyUtils.createCargoConfigurations(target)
        }
        cargoExtension = target.extensions.create<CargoExtension>(TASK_GROUP, target)
        cargoExtension.jvmVariant.convention(Variant.Debug)
        cargoExtension.jvmPublishingVariant.convention(Variant.Release)
        cargoExtension.nativeVariant.convention(Variant.Debug)
        cargoExtension.wasmVariant.convention(Variant.Debug)
        readVariantsFromXcode()
        cargoExtension.builds.native {
            nativeVariant.convention(
                cargoExtension.nativeTargetVariantOverride.getting(rustTarget)
                    .orElse(cargoExtension.nativeVariant)
            )
        }
        cargoExtension.builds.jvm {
            jvmVariant.convention(cargoExtension.jvmVariant)
            jvmPublishingVariant.convention(cargoExtension.jvmPublishingVariant)
        }
        cargoExtension.builds.wasm {
            wasmVariant.convention(cargoExtension.wasmVariant)
        }
        @OptIn(InternalGobleyGradleApi::class)
        target.useGlobalLock()
        target.tasks.withType<CargoTask>().configureEach {
            additionalEnvironmentPath.add(cargoExtension.toolchainDirectory)
        }
        target.tasks.withType<RustUpTask>().configureEach {
            additionalEnvironmentPath.add(cargoExtension.toolchainDirectory)
        }
        target.watchPluginChanges()
        target.afterEvaluate {
            target.checkRequiredPlugins()
            target.checkKotlinTargets()
            applyAfterEvaluate(this)
        }
    }

    private fun applyAfterEvaluate(target: Project): Unit = with(target) {
        checkRequiredCrateTypes()
        if (cargoExtension.builds.isEmpty()) {
            logger.warn("No Kotlin targets detected.")
            return
        }

        configureBuildTasks()
        configureCleanTasks()

        @OptIn(InternalGobleyGradleApi::class)
        DependencyUtils.resolveCargoDependencies(target)
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.watchPluginChanges() {
        PluginUtils.withKotlinPlugin(this) { delegate ->
            kotlinExtensionDelegate = delegate
            delegate.targets.configureEach { planBuilds() }
        }
        PluginUtils.withAndroidPlugin(this) { delegate ->
            androidDelegate = delegate
            val abiFilters = androidDelegate?.abiFilters
            val targets = if (!abiFilters.isNullOrEmpty()) {
                abiFilters.map(::RustAndroidTarget)
            } else {
                RustAndroidTarget.entries
            }

            cargoExtension.androidTargetsToBuild.convention(project.provider { targets })

            targets.forEach { rustTarget ->
                cargoExtension.createOrGetBuild(rustTarget)
            }

            cargoExtension.builds.configureEach {
                val currentCargoBuild = this
                val currentRustTarget = currentCargoBuild.rustTarget

                if (currentRustTarget is RustAndroidTarget) {
                    // Manually cast to Android Build so Gradle can't ignore us
                    val androidBuild = currentCargoBuild as CargoAndroidBuild

                    androidBuild.dynamicLibrarySearchPaths.addAll(
                        @OptIn(InternalGobleyGradleApi::class)
                        currentRustTarget.ndkLibraryDirectories(
                            sdkRoot = androidDelegate!!.androidSdkRoot,
                            apiLevel = androidDelegate!!.androidMinSdk,
                            ndkVersion = androidDelegate!!.androidNdkVersion,
                            ndkRoot = androidDelegate!!.androidNdkRoot,
                        ),
                    )
                    Variant.entries.forEach { variant ->
                        configureAndroidPostBuildTasks(androidBuild.variant(variant))
                    }
                }
            }

            // 3. THE MODERN AGP 9.0 VARIANT API INTEGRATION
            // Our delegate entirely shields the JVM from verifying missing AGP classes!
            androidDelegate!!.onVariants(project) { agpVariantName, cargoVariantName, onMainTask, onTestTask ->
                registerCargoSyncTasks(
                    cargoExtension = cargoExtension,
                    agpVariantName = agpVariantName,
                    cargoVariantName = cargoVariantName,
                    onMainTask = onMainTask,
                    onTestTask = onTestTask
                )
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.registerCargoSyncTasks(
        cargoExtension: CargoExtension,
        agpVariantName: String,
        cargoVariantName: String,
        onMainTask: (TaskProvider<InjectJniLibsTask>) -> Unit,
        onTestTask: ((TaskProvider<InjectJniLibsTask>) -> Unit)?
    ) {
        // --- THE FIX: Intercept "androidmain" and map it to a valid Cargo Variant ---
        val safeCargoVariant = when (cargoVariantName.lowercase()) {
            "release" -> "release"
            else -> "debug" // Safely fallback for 'androidmain' or other non-variant strings
        }

        cargoExtension.builds.configureEach {
            val currentCargoBuild = this
            val currentRustTarget = currentCargoBuild.rustTarget

            if (currentRustTarget is RustAndroidTarget) {
                val androidBuild = currentCargoBuild as CargoAndroidBuild

                // Use our sanitized variant name
                val cargoBuildVariant = androidBuild.variant(Variant(safeCargoVariant))

                val isTargetEnabled = cargoExtension.androidTargetsToBuild.map { it.contains(currentRustTarget) }
                val embedRustLibrary = cargoBuildVariant.embedRustLibrary

                // --- TASK 1: MAIN AAR / APK ---
                val syncMain = project.tasks.register<InjectJniLibsTask>(
                    "copyCargoJniMain${currentRustTarget.friendlyName}${agpVariantName.replaceFirstChar { it.uppercase() }}"
                ) {
                    group = TASK_GROUP
                    onlyIf { isTargetEnabled.get() && embedRustLibrary.get() }

                    rustLibs.from(cargoBuildVariant.buildTaskProvider.flatMap { task ->
                        task.libraryFileByCrateType.map { it[CrateType.SystemDynamicLibrary]!! }
                    })
                    otherLibs.from(cargoBuildVariant.findDynamicLibrariesTaskProvider.flatMap { it.libraryPaths })
                    abiName.set(currentRustTarget.androidAbiName)
                }

                // Fire the callback to let the caller inject the task!
                onMainTask(syncMain)

                // --- TASK 2: ANDROID TEST APK ---
                if (onTestTask != null) {
                    val syncTest = project.tasks.register<InjectJniLibsTask>(
                        "copyCargoJniTest${currentRustTarget.friendlyName}${agpVariantName.replaceFirstChar { it.uppercase() }}"
                    ) {
                        group = TASK_GROUP
                        onlyIf { isTargetEnabled.get() && embedRustLibrary.get() }

                        rustLibs.from(cargoBuildVariant.buildTaskProvider.flatMap { task ->
                            task.libraryFileByCrateType.map { it[CrateType.SystemDynamicLibrary]!! }
                        })
                        otherLibs.from(cargoBuildVariant.findDynamicLibrariesTaskProvider.flatMap { it.libraryPaths })
                        abiName.set(currentRustTarget.androidAbiName)
                    }

                    // Fire the callback for the test task
                    onTestTask(syncTest)
                }
            }
        }
    }

    private fun Project.checkRequiredPlugins() {
        @OptIn(InternalGobleyGradleApi::class)
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
    }

    private fun KotlinTarget.planBuilds() {
        for (rustTarget in requiredRustTargets()) {
            cargoExtension.createOrGetBuild(rustTarget).kotlinTargets.add(this)
        }
    }

    private val KotlinTarget.androidKmpLibraryCompatiblePlatformType: KotlinPlatformType
        get() {
            // 1. Check for the legacy KMP Android Target (AGP 8)
            val isOldKmp = this::class.superclasses.any { type ->
                type.qualifiedName == "com.android.build.api.dsl.KotlinMultiplatformAndroidTarget"
            }

            // 2. Check for the new AGP 9 KMP Library Target
            val isNewKmp = this.javaClass.interfaces.any {
                it.name.contains("KotlinMultiplatformAndroidLibraryTarget")
            }

            if (isOldKmp || isNewKmp) {
                return KotlinPlatformType.androidJvm
            }
            return platformType
        }

    private fun KotlinTarget.requiredRustTargets(): List<RustTarget> {
        return when (androidKmpLibraryCompatiblePlatformType) {
            KotlinPlatformType.jvm -> {
                GobleyHost.current.platform.supportedTargets.filterIsInstance<RustJvmTarget>()
            }

            KotlinPlatformType.androidJvm -> {
                // listOf(GobleyHost.current.rustTarget) is for Android local unit tests.
                listOf(GobleyHost.current.rustTarget) + RustAndroidTarget.entries.toTypedArray()
            }

            KotlinPlatformType.native -> {
                listOf(RustTarget((this as KotlinNativeTarget).konanTarget))
            }

            KotlinPlatformType.js -> {
                RustWasmTarget.entries
            }

            else -> listOf()
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.checkKotlinTargets() {
        val hasWasmTargets = kotlinExtensionDelegate?.targets.orEmpty().any {
            it.androidKmpLibraryCompatiblePlatformType == KotlinPlatformType.wasm
        }
        if (hasWasmTargets) {
            project.logger.warn("WASM targets are added, but Gobley does not support WASM targets yet.")
        }

        val hasAndroidJvmTargets = kotlinExtensionDelegate?.targets.orEmpty().any {
            it.androidKmpLibraryCompatiblePlatformType == KotlinPlatformType.androidJvm
        }
        if (hasAndroidJvmTargets && androidDelegate == null) {
            throw GradleException("Android JVM targets are added, but Android Gradle Plugin is not found.")
        }
    }

    private fun checkRequiredCrateTypes() {
        val requiredCrateTypes = cargoExtension
            .builds
            .flatMap { it.kotlinTargets }
            .map { it.androidKmpLibraryCompatiblePlatformType.requiredCrateType() }
            .distinct()
        val actualCrateTypes = cargoExtension.cargoPackage.get().libraryCrateTypes
        if (!actualCrateTypes.containsAll(requiredCrateTypes)) {
            throw GradleException(
                "Crate does not have required crate types. Required: $requiredCrateTypes, actual: $actualCrateTypes"
            )
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun readVariantsFromXcode() {
        val sdkName = System.getenv("SDK_NAME") ?: return
        val sdk = AppleSdk(sdkName)

        val configuration = System.getenv("CONFIGURATION") ?: return
        val variant = Variant(configuration)

        val archs = System.getenv("ARCHS")?.split(' ')?.map(AppleSdk::Arch) ?: return
        cargoExtension.nativeTargetVariantOverride.putAll(
            archs.mapNotNull(sdk::rustTarget).associateWith { variant })
    }

    private fun Project.configureBuildTasks() {
        val androidTarget = cargoExtension.builds.firstNotNullOfOrNull { build ->
            build.kotlinTargets.firstNotNullOfOrNull { it as? KotlinAndroidTarget }
        }
        val jvmTarget = cargoExtension.builds.firstNotNullOfOrNull { build ->
            build.kotlinTargets.firstOrNull {
                it is KotlinJvmTarget || it is KotlinWithJavaTarget<*, *>
            }
        }
        val wasmBindgenInstallTask = tasks.register<InstallWasmTransformerTask>("installWasmTransformer") {
            group = TASK_GROUP
            binaryCrateSource.set(cargoExtension.wasmTransformerSource)
            installDirectory.set(layout.buildDirectory.dir("gobley-tools-install/wasm-transformer"))
        }

        for (cargoBuild in cargoExtension.builds) {

            val rustUpTargetAddTask = tasks.register<RustUpTargetAddTask>({ +cargoBuild.rustTarget }) {
                group = TASK_GROUP
                this.rustTarget.set(cargoBuild.rustTarget)
                this.rustVersion.set(cargoExtension.rustVersion)
            }

            for (cargoBuildVariant in cargoBuild.variants) {
                val projectLayout = layout
                cargoBuildVariant.buildTaskProvider.configure {
                    nativeStaticLibsDefFile.set(
                        projectLayout.outputCacheFile(
                            this,
                            "nativeStaticLibsDefFile",
                        )
                    )
                    buildScriptOutputDirectoriesFile.set(
                        projectLayout.outputCacheFile(
                            this,
                            "buildScriptOutputDirectoriesFile",
                        )
                    )
                    if (cargoBuild.installTargetBeforeBuild.get()) {
                        dependsOn(rustUpTargetAddTask)
                    }
                    if (cargoBuildVariant is CargoAndroidBuildVariant) {
                        @OptIn(InternalGobleyGradleApi::class)
                        val environmentVariables = cargoBuildVariant.rustTarget.ndkEnvVariables(
                            sdkRoot = androidDelegate!!.androidSdkRoot,
                            apiLevel = androidDelegate!!.androidMinSdk,
                            ndkVersion = androidDelegate!!.androidNdkVersion,
                            ndkRoot = androidDelegate!!.androidNdkRoot,
                        )
                        additionalEnvironment.putAll(environmentVariables)
                    }
                }
                cargoBuildVariant.checkTaskProvider.configure {
                    if (cargoBuild.installTargetBeforeBuild.get()) {
                        dependsOn(rustUpTargetAddTask)
                    }
                    if (cargoBuildVariant is CargoAndroidBuildVariant) {
                        @OptIn(InternalGobleyGradleApi::class)
                        val environmentVariables = cargoBuildVariant.rustTarget.ndkEnvVariables(
                            sdkRoot = androidDelegate!!.androidSdkRoot,
                            apiLevel = androidDelegate!!.androidMinSdk,
                            ndkVersion = androidDelegate!!.androidNdkVersion,
                            ndkRoot = androidDelegate!!.androidNdkRoot,
                        )
                        additionalEnvironment.putAll(environmentVariables)
                    }
                }
            }
            for (kotlinTarget in cargoBuild.kotlinTargets) {
                when (kotlinTarget.androidKmpLibraryCompatiblePlatformType) {
                    KotlinPlatformType.jvm -> {
                        cargoBuild as CargoJvmBuild<*>
                        cargoBuild.variants {
                            configureJvmPostBuildTasks(
                                kotlinTarget,
                                // cargoBuild.jvmVariant is checked inside
                                this,
                                androidTarget,
                            )
                        }
                    }

                    KotlinPlatformType.androidJvm -> {
                        if (cargoBuild is CargoJvmBuild<*>) {
                            if (jvmTarget == null) {
                                cargoBuild.variants {
                                    configureJvmPostBuildTasks(
                                        kotlinTarget,
                                        // cargoBuild.jvmVariant is checked inside
                                        this,
                                        kotlinTarget,
                                    )
                                }
                            }
                        } else {
                            cargoBuild as CargoAndroidBuild
                            cargoBuild.dynamicLibrarySearchPaths.addAll(
                                @OptIn(InternalGobleyGradleApi::class)
                                cargoBuild.rustTarget.ndkLibraryDirectories(
                                    sdkRoot = androidDelegate!!.androidSdkRoot,
                                    apiLevel = androidDelegate!!.androidMinSdk,
                                    ndkVersion = androidDelegate!!.androidNdkVersion,
                                    ndkRoot = androidDelegate!!.androidNdkRoot,
                                ),
                            )
                            Variant.entries.forEach {
                                configureAndroidPostBuildTasks(cargoBuild.variant(it))
                            }
                        }
                    }

                    KotlinPlatformType.native -> {
                        cargoBuild as CargoNativeBuild<*>
                        configureNativeCompilation(
                            kotlinTarget as KotlinNativeTarget,
                            cargoBuild.variant(cargoBuild.nativeVariant.get())
                        )
                    }

                    KotlinPlatformType.js -> {
                        cargoBuild as CargoWasmBuild
                        configureWasmCompilation(
                            kotlinTarget as KotlinJsIrTarget,
                            cargoBuild.variant(cargoBuild.wasmVariant.get()),
                            wasmBindgenInstallTask,
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    /**
     * Configures post-build tasks for JVM targets (and Android unit tests).
     *
     * Key responsibilities:
     * - Copying dynamic libraries to a location where `java.library.path` or similar can find them.
     * - Configuring the `processResources` task to include these libraries in the JAR/classpath.
     * - Setting up Maven publishing for JVM artifacts.
     */
    private fun Project.configureJvmPostBuildTasks(
        // kotlinTarget can be KotlinAndroidTarget when the JVM target is not present. This is for
        // Android local unit tests.
        kotlinTarget: KotlinTarget,
        cargoBuildVariant: CargoJvmBuildVariant<*>,
        androidTarget: KotlinTarget?,
    ) {
        val buildTask = cargoBuildVariant.buildTaskProvider
        val checkTask = cargoBuildVariant.checkTaskProvider
        val findDynamicLibrariesTask = cargoBuildVariant.findDynamicLibrariesTaskProvider
        val jarTask = cargoBuildVariant.jarTaskProvider
        cargoBuildVariant.dynamicLibrarySearchPaths.add(
            cargoBuildVariant.profile.zip(cargoExtension.cargoPackage) { profile, cargoPackage ->
                cargoPackage.outputDirectory(profile, cargoBuildVariant.rustTarget).asFile
            }
        )
        cargoBuildVariant.dynamicLibrarySearchPaths.addAll(
            cargoBuildVariant.buildTaskProvider.flatMap { it.buildScriptOutputDirectories }
        )
        val projectLayout = layout
        findDynamicLibrariesTask.configure {
            libraryPathsCacheFile.set(projectLayout.outputCacheFile(this, "libraryPathsCacheFile"))
        }

        // For Kotlin/JVM projects without the application plugin or the Compose Multiplatform
        // plugin, the dynamic libraries must be copied in the resources directory to be loaded
        // during runtime. See #95.
        //
        // To avoid being included in the resources when class files are packaged into a JAR file,
        // `copyLibrariesTask` is invoked only when `GradleUtils.invokedByKotlinJvmBuild()`
        // returns `false`.
        val resourcePrefix = cargoBuildVariant.resourcePrefix.orNull?.takeIf(String::isNotEmpty)
        val resourceDirectory = layout.buildDirectory
            .dir("intermediates/rust/${cargoBuildVariant.rustTarget.rustTriple}/${cargoBuildVariant.variant}")
        val resourceCopyDestination = if (resourcePrefix == null) {
            resourceDirectory
        } else resourceDirectory.map {
            it.dir(resourcePrefix)
        }
        val copyLibrariesTask = tasks.register<Copy>({
            +"jvm"
            +cargoBuildVariant
        }) {
            from(cargoBuildVariant.libraryFiles)
            into(resourceCopyDestination)
            dependsOn(buildTask, findDynamicLibrariesTask)
        }

        @OptIn(InternalGobleyGradleApi::class)
        if (
            kotlinTarget !is KotlinAndroidTarget
            && cargoBuildVariant.embedRustLibrary.get()
            && cargoBuildVariant.variant == cargoBuildVariant.build.jvmVariant.get()
        ) {
            val invokedByKotlinJvmBuild = GradleUtils.invokedByKotlinJvmBuild(gradle)
            if (invokedByKotlinJvmBuild) {
                val expectedTaskName = when (kotlinExtensionDelegate?.pluginId) {
                    PluginIds.KOTLIN_JVM -> "processResources"
                    else -> "${kotlinTarget.name}ProcessResources"
                }
                tasks.withType<ProcessResources> {
                    if (name == expectedTaskName) {
                        dependsOn(copyLibrariesTask)
                    }
                }
            }
            // THE FIX: Dynamically fetch the source set for the current target (e.g., 'androidMain' or 'jvmMain')
            val mainSourceSet = kotlinTarget.compilations.getByName("main").defaultSourceSet
            with(mainSourceSet) {
                if (invokedByKotlinJvmBuild) {
                    resources.srcDir(resourceDirectory)
                }
                dependencies {
                    // In modern Gradle/KGP, you may need to use 'implementation'
                    // instead of 'runtimeOnly' if the source set rejects runtimeOnly.
                    // But if runtimeOnly is currently working for your setup, leave it as is!
                    runtimeOnly(files(jarTask.flatMap { it.archiveFile }))
                }
            }
        }

        @OptIn(InternalGobleyGradleApi::class)
        if (
            kotlinTarget !is KotlinAndroidTarget
            && cargoBuildVariant.embedRustLibrary.get()
            && cargoBuildVariant.variant == cargoBuildVariant.build.jvmPublishingVariant.get()
            && cargoExtension.publishJvmArtifacts.get()
            && kotlinExtensionDelegate?.pluginId == PluginIds.KOTLIN_MULTIPLATFORM
        ) {
            plugins.withId("maven-publish") {
                val publishing = extensions.getByType(PublishingExtension::class.java)
                val publication = publishing.publications.getByName(kotlinTarget.name)
                if (publication is MavenPublication) {
                    publication.artifact(jarTask)
                }
            }
        }

        if (cargoBuildVariant.embedRustLibrary.get()) {
            tasks.named("check") {
                dependsOn(checkTask)
            }
        }

        @OptIn(InternalGobleyGradleApi::class)
        if (androidTarget != null && cargoBuildVariant.androidUnitTest.get()) {
            DependencyUtils.addAndroidUnitTestRuntimeRustLibraryJar(
                this,
                cargoBuildVariant.rustTarget,
                cargoBuildVariant.variant,
                jarTask,
            )
            with(kotlinExtensionDelegate!!.sourceSets.androidUnitTest(cargoBuildVariant.variant)) {
                dependencies {
                    runtimeOnly(files(jarTask.flatMap { it.archiveFile }))
                }
            }
            // Only support debug mode Compose previews.
            // Since one of the dependencies of Compose previews, androidx.compose.ui:ui-tooling,
            // is referenced as debugImplementation in the default template generated from
            // Android Studio, and there is relatively small chance of users requiring to use
            // the Rust library from release mode Compose previews, let's just handle debug mode
            // Compose previews. See #94 for details.
            if (cargoBuildVariant.variant == Variant.Debug
                && cargoBuildVariant.variant == GradleUtils.getComposePreviewVariant(gradle)
            ) {
                with(kotlinExtensionDelegate!!.sourceSets.androidMain(Variant.Debug)) {
                    dependencies {
                        runtimeOnly(files(jarTask.flatMap { it.archiveFile }))
                    }
                }
            }
        }
    }

    private fun Project.configureAndroidPostBuildTasks(cargoBuildVariant: CargoAndroidBuildVariant) {
        val checkTask = cargoBuildVariant.checkTaskProvider
        val findDynamicLibrariesTask = cargoBuildVariant.findDynamicLibrariesTaskProvider

        cargoBuildVariant.dynamicLibrarySearchPaths.add(
            cargoBuildVariant.profile.zip(cargoExtension.cargoPackage) { profile, cargoPackage ->
                cargoPackage.outputDirectory(profile, cargoBuildVariant.rustTarget).asFile
            }
        )
        cargoBuildVariant.dynamicLibrarySearchPaths.addAll(
            cargoBuildVariant.buildTaskProvider.flatMap { it.buildScriptOutputDirectories }
        )

        val projectLayout = layout
        findDynamicLibrariesTask.configure {
            libraryPathsCacheFile.set(projectLayout.outputCacheFile(this, "libraryPathsCacheFile"))
        }

        tasks.named("check") {
            dependsOn(checkTask)
        }
    }

    private fun Project.configureNativeCompilation(
        kotlinTarget: KotlinNativeTarget,
        cargoBuildVariant: CargoNativeBuildVariant<*>,
    ) {
        val buildTask = cargoBuildVariant.buildTaskProvider
        val checkTask = cargoBuildVariant.checkTaskProvider

        val buildOutputFile = buildTask
            .flatMap { it.libraryFileByCrateType }
            .map { it[CrateType.SystemStaticLibrary]!! }

        kotlinTarget.compilations.getByName("main") {
            cinterops.register("rust") {
                defFile(buildTask.flatMap { it.nativeStaticLibsDefFile })
                extraOpts(
                    "-libraryPath",
                    cargoExtension.cargoPackage.zip(cargoBuildVariant.profile) { cargoPackage, profile ->
                        cargoPackage.outputDirectory(profile, cargoBuildVariant.rustTarget)
                    }.get()
                )
                project.tasks.named(interopProcessingTaskName) {
                    inputs.file(buildOutputFile)
                    dependsOn(buildTask)
                }
            }
            compileTaskProvider.configure {
                compilerOptions.optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        tasks.named("check") {
            dependsOn(checkTask)
        }
    }

    private fun Project.configureWasmCompilation(
        kotlinTarget: KotlinJsIrTarget,
        cargoBuildVariant: CargoWasmBuildVariant,
        wasmBindgenInstallTask: TaskProvider<InstallWasmTransformerTask>,
    ) {
        val buildTask = cargoBuildVariant.buildTaskProvider
        val checkTask = cargoBuildVariant.checkTaskProvider

        cargoBuildVariant.transformWasmProvider.configure {
            wasmTransformer.set(wasmBindgenInstallTask.get().wasmTransformer)
        }

        if (!cargoBuildVariant.embedRustLibrary.get())
            return

        @OptIn(InternalGobleyGradleApi::class)
        kotlinExtensionDelegate!!.sourceSets.run {
            jsMain.kotlin.srcDir(
                cargoBuildVariant.transformWasmProvider.flatMap { it.outputDirectory }
            )
        }

        kotlinTarget.compilations.getByName("main") {
            compileTaskProvider.dependsOn(buildTask)
        }

        tasks.named("check") {
            dependsOn(checkTask)
        }
    }

    private fun Project.configureCleanTasks() {
        val cleanCrate = tasks.register<CargoCleanTask>("cargoClean") {
            group = TASK_GROUP
            cargoPackage.set(cargoExtension.cargoPackage)
        }

        tasks.named<Delete>("clean") {
            dependsOn(cleanCrate)
        }
    }
}

private fun KotlinPlatformType.requiredCrateType(): CrateType? = when (this) {
    // TODO: properly handle JS and WASM targets
    KotlinPlatformType.common -> null
    KotlinPlatformType.jvm -> CrateType.SystemDynamicLibrary
    KotlinPlatformType.js -> CrateType.SystemDynamicLibrary
    KotlinPlatformType.androidJvm -> CrateType.SystemDynamicLibrary
    KotlinPlatformType.native -> CrateType.SystemStaticLibrary
    KotlinPlatformType.wasm -> CrateType.SystemStaticLibrary
}

private fun ProjectLayout.outputCacheFile(task: Task, propertyName: String): Provider<RegularFile> {
    val trimmedPropertyName = propertyName
        .substringBeforeLast("File")
        .substringBeforeLast("Cache")
    return buildDirectory.file("taskOutputCache/${task.name}/$trimmedPropertyName")
}