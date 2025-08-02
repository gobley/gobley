/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.rust.targets.RustTarget
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.specs.Spec

interface CargoBuildCollection<T : CargoBuild<*>> : NamedDomainObjectCollection<T> {
    override fun <S : T> withType(type: Class<S>): CargoBuildCollection<S>

    override fun matching(spec: Spec<in T>): CargoBuildCollection<T>

    override fun matching(spec: Closure<*>): CargoBuildCollection<T>

    @Throws(UnknownDomainObjectException::class)
    override fun <S : T> named(name: String, type: Class<S>): CargoBuildProvider<S>

    @Throws(UnknownDomainObjectException::class)
    override fun <S : T> named(
        name: String, type: Class<S>, configurationAction: Action<in S>
    ): CargoBuildProvider<S>

    fun findByRustTarget(rustTarget: RustTarget): T?
}

@get:JvmName("android")
val CargoBuildCollection<CargoBuild<*>>.android get() = withType(CargoAndroidBuild::class.java)

@JvmName("configureAndroid")
fun CargoBuildCollection<CargoBuild<*>>.android(action: CargoAndroidBuild.() -> Unit) =
    android.apply { configureEach(action) }

@get:JvmName("appleMobile")
val CargoBuildCollection<CargoBuild<*>>.appleMobile get() = withType(CargoAppleMobileBuild::class.java)

@JvmName("configureAppleMobile")
fun CargoBuildCollection<CargoBuild<*>>.appleMobile(action: CargoAppleMobileBuild.() -> Unit) =
    appleMobile.apply { configureEach(action) }

@get:JvmName("desktop")
val CargoBuildCollection<CargoBuild<*>>.desktop get() = withType(CargoDesktopBuild::class.java)

@JvmName("configureDesktop")
fun CargoBuildCollection<CargoBuild<*>>.desktop(action: CargoDesktopBuild<*>.() -> Unit) =
    desktop.apply { configureEach(action) }

@get:JvmName("jvm")
val CargoBuildCollection<CargoBuild<*>>.jvm get() = withType(CargoJvmBuild::class.java)

@JvmName("configureJvm")
fun CargoBuildCollection<CargoBuild<*>>.jvm(action: CargoJvmBuild<*>.() -> Unit) =
    jvm.apply { configureEach(action) }

@get:JvmName("mobile")
val CargoBuildCollection<CargoBuild<*>>.mobile get() = withType(CargoMobileBuild::class.java)

@JvmName("configureMobile")
fun CargoBuildCollection<CargoBuild<*>>.mobile(action: CargoMobileBuild<*>.() -> Unit) =
    mobile.apply { configureEach(action) }

@get:JvmName("native")
val CargoBuildCollection<CargoBuild<*>>.native get() = withType(CargoNativeBuild::class.java)

@JvmName("configureNative")
fun CargoBuildCollection<CargoBuild<*>>.native(action: CargoNativeBuild<*>.() -> Unit) =
    native.apply { configureEach(action) }

@get:JvmName("posix")
val CargoBuildCollection<CargoBuild<*>>.posix get() = withType(CargoPosixBuild::class.java)

@JvmName("configurePosix")
fun CargoBuildCollection<CargoBuild<*>>.posix(action: CargoPosixBuild.() -> Unit) =
    posix.apply { configureEach(action) }

@get:JvmName("mingw")
val CargoBuildCollection<CargoBuild<*>>.mingw get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isWindows() }

@JvmName("configureMingw")
fun CargoBuildCollection<CargoBuild<*>>.mingw(action: CargoPosixBuild.() -> Unit) =
    mingw.apply { configureEach(action) }

@get:JvmName("linux")
val CargoBuildCollection<CargoBuild<*>>.linux get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isLinux() }

@JvmName("configureLinux")
fun CargoBuildCollection<CargoBuild<*>>.linux(action: CargoPosixBuild.() -> Unit) =
    linux.apply { configureEach(action) }

@get:JvmName("macos")
val CargoBuildCollection<CargoBuild<*>>.macos get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isMacOS() }

@JvmName("configureMacos")
fun CargoBuildCollection<CargoBuild<*>>.macos(action: CargoPosixBuild.() -> Unit) =
    macos.apply { configureEach(action) }

@get:JvmName("wasm")
val CargoBuildCollection<CargoBuild<*>>.wasm get() = withType(CargoWasmBuild::class.java)

@JvmName("configureWasm")
fun CargoBuildCollection<CargoBuild<*>>.wasm(action: CargoWasmBuild.() -> Unit) =
    wasm.apply { configureEach(action) }

@get:JvmName("windows")
val CargoBuildCollection<CargoBuild<*>>.windows get() = withType(CargoWindowsBuild::class.java)

@JvmName("configureWindows")
fun CargoBuildCollection<CargoBuild<*>>.windows(action: CargoWindowsBuild.() -> Unit) =
    windows.apply { configureEach(action) }

@get:JvmName("desktopJvm")
val CargoBuildCollection<CargoDesktopBuild<*>>.jvm get() = withType(CargoJvmBuild::class.java)

@JvmName("desktopConfigureJvm")
fun CargoBuildCollection<CargoDesktopBuild<*>>.jvm(action: CargoJvmBuild<*>.() -> Unit) =
    jvm.apply { configureEach(action) }

@get:JvmName("desktopPosix")
val CargoBuildCollection<CargoDesktopBuild<*>>.posix get() = withType(CargoPosixBuild::class.java)

@JvmName("desktopConfigurePosix")
fun CargoBuildCollection<CargoDesktopBuild<*>>.posix(action: CargoPosixBuild.() -> Unit) =
    posix.apply { configureEach(action) }

@get:JvmName("desktopMingw")
val CargoBuildCollection<CargoDesktopBuild<*>>.mingw get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isWindows() }

@JvmName("desktopConfigureMingw")
fun CargoBuildCollection<CargoDesktopBuild<*>>.mingw(action: CargoPosixBuild.() -> Unit) =
    mingw.apply { configureEach(action) }

@get:JvmName("desktopLinux")
val CargoBuildCollection<CargoDesktopBuild<*>>.linux get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isLinux() }

@JvmName("desktopConfigureLinux")
fun CargoBuildCollection<CargoDesktopBuild<*>>.linux(action: CargoPosixBuild.() -> Unit) =
    linux.apply { configureEach(action) }

@get:JvmName("desktopMacos")
val CargoBuildCollection<CargoDesktopBuild<*>>.macos get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isMacOS() }

@JvmName("desktopConfigureMacos")
fun CargoBuildCollection<CargoDesktopBuild<*>>.macos(action: CargoPosixBuild.() -> Unit) =
    macos.apply { configureEach(action) }

@get:JvmName("desktopWindows")
val CargoBuildCollection<CargoDesktopBuild<*>>.windows get() = withType(CargoWindowsBuild::class.java)

@JvmName("desktopConfigureWindows")
fun CargoBuildCollection<CargoDesktopBuild<*>>.windows(action: CargoWindowsBuild.() -> Unit) =
    windows.apply { configureEach(action) }

@get:JvmName("jvmPosix")
val CargoBuildCollection<CargoJvmBuild<*>>.posix get() = withType(CargoPosixBuild::class.java)

@JvmName("configureJvmPosix")
fun CargoBuildCollection<CargoJvmBuild<*>>.posix(action: CargoPosixBuild.() -> Unit) =
    posix.apply { configureEach(action) }

@get:JvmName("jvmMingw")
val CargoBuildCollection<CargoJvmBuild<*>>.mingw get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isWindows() }

@JvmName("configureJvmMingw")
fun CargoBuildCollection<CargoJvmBuild<*>>.mingw(action: CargoPosixBuild.() -> Unit) =
    mingw.apply { configureEach(action) }

@get:JvmName("jvmLinux")
val CargoBuildCollection<CargoJvmBuild<*>>.linux get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isLinux() }

@JvmName("configureJvmLinux")
fun CargoBuildCollection<CargoJvmBuild<*>>.linux(action: CargoPosixBuild.() -> Unit) =
    linux.apply { configureEach(action) }

@get:JvmName("jvmMacos")
val CargoBuildCollection<CargoJvmBuild<*>>.macos get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isMacOS() }

@JvmName("configureJvmMacos")
fun CargoBuildCollection<CargoJvmBuild<*>>.macos(action: CargoPosixBuild.() -> Unit) =
    macos.apply { configureEach(action) }

@get:JvmName("jvmWindows")
val CargoBuildCollection<CargoJvmBuild<*>>.windows get() = withType(CargoWindowsBuild::class.java)

@JvmName("configureJvmWindows")
fun CargoBuildCollection<CargoJvmBuild<*>>.windows(action: CargoWindowsBuild.() -> Unit) =
    windows.apply { configureEach(action) }

@get:JvmName("mobileAndroid")
val CargoBuildCollection<CargoMobileBuild<*>>.android get() = withType(CargoAndroidBuild::class.java)

@JvmName("configureMobileAndroid")
fun CargoBuildCollection<CargoMobileBuild<*>>.android(action: CargoAndroidBuild.() -> Unit) =
    android.apply { configureEach(action) }

@get:JvmName("mobileAppleMobile")
val CargoBuildCollection<CargoMobileBuild<*>>.appleMobile get() = withType(CargoAppleMobileBuild::class.java)

@JvmName("configureMobileAppleMobile")
fun CargoBuildCollection<CargoMobileBuild<*>>.appleMobile(action: CargoAppleMobileBuild.() -> Unit) =
    appleMobile.apply { configureEach(action) }

@get:JvmName("nativeAppleMobile")
val CargoBuildCollection<CargoNativeBuild<*>>.appleMobile get() = withType(CargoAppleMobileBuild::class.java)

@JvmName("configureNativeAppleMobile")
fun CargoBuildCollection<CargoNativeBuild<*>>.appleMobile(action: CargoAppleMobileBuild.() -> Unit) =
    appleMobile.apply { configureEach(action) }

@get:JvmName("nativePosix")
val CargoBuildCollection<CargoNativeBuild<*>>.posix get() = withType(CargoPosixBuild::class.java)

@JvmName("configureNativePosix")
fun CargoBuildCollection<CargoNativeBuild<*>>.posix(action: CargoPosixBuild.() -> Unit) =
    posix.apply { configureEach(action) }

@get:JvmName("nativeMingw")
val CargoBuildCollection<CargoNativeBuild<*>>.mingw get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isWindows() }

@JvmName("configureNativeMingw")
fun CargoBuildCollection<CargoNativeBuild<*>>.mingw(action: CargoPosixBuild.() -> Unit) =
    mingw.apply { configureEach(action) }

@get:JvmName("nativeLinux")
val CargoBuildCollection<CargoNativeBuild<*>>.linux get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isLinux() }

@JvmName("configureNativeLinux")
fun CargoBuildCollection<CargoNativeBuild<*>>.linux(action: CargoPosixBuild.() -> Unit) =
    linux.apply { configureEach(action) }

@get:JvmName("nativeMacos")
val CargoBuildCollection<CargoNativeBuild<*>>.macos get() = withType(CargoPosixBuild::class.java).matching { it.rustTarget.isMacOS() }

@JvmName("configureNativeMacos")
fun CargoBuildCollection<CargoNativeBuild<*>>.macos(action: CargoPosixBuild.() -> Unit) =
    macos.apply { configureEach(action) }

internal class CargoBuildCollectionImpl<T : CargoBuild<*>>(
    private val base: NamedDomainObjectCollection<T>
) : NamedDomainObjectCollection<T> by base, CargoBuildCollection<T> {
    override fun <S : T> withType(type: Class<S>): CargoBuildCollection<S> =
        CargoBuildCollectionImpl(base.withType(type))

    override fun matching(spec: Spec<in T>): CargoBuildCollection<T> =
        CargoBuildCollectionImpl(base.matching(spec))

    override fun matching(spec: Closure<*>): CargoBuildCollection<T> =
        CargoBuildCollectionImpl(base.matching(spec))

    @Throws(UnknownDomainObjectException::class)
    override fun <S : T> named(name: String, type: Class<S>): CargoBuildProvider<S> =
        CargoBuildProviderImpl(base.named(name, type))

    @Throws(UnknownDomainObjectException::class)
    override fun <S : T> named(
        name: String, type: Class<S>, configurationAction: Action<in S>
    ): CargoBuildProvider<S> = CargoBuildProviderImpl(base.named(name, type, configurationAction))

    override fun findByRustTarget(rustTarget: RustTarget): T? {
        return findByName(rustTarget.friendlyName)
    }
}
