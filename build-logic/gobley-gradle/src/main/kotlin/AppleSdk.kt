/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle

import gobley.gradle.rust.targets.RustAppleMobileTarget
import gobley.gradle.rust.targets.RustPosixTarget
import gobley.gradle.rust.targets.RustTarget

@InternalGobleyGradleApi
data class AppleSdk(val destination: Destination, val version: String) {
    enum class Destination(val actualName: String) {
        Ios("iphoneos"),
        IosSimulator("iphonesimulator"),
        MacOS("macosx"),
        TvOS("appletvos"),
        TvOSSimulator("appletvsimulator"),
        VisionOS("xros"),
        VisionOSSimulator("xrsimulator"),
        WatchOS("watchos"),
        WatchOSSimulator("watchsimulator"),
    }

    @Suppress("EnumEntryName")
    enum class Arch(val actualName: String) {
        Arm64("arm64"),
        Arm64e("arm64e"),
        Armv7k("armv7k"),
        Arm64_32("arm64_32"),
        X86("i386"),
        X64("x86_64"),
    }

    val sdkName = "${destination.actualName}$version"

    fun rustTarget(arch: Arch): RustTarget? {
        return rustTargetsByDestinationArch[destination to arch]
    }

    companion object {
        fun Arch(actualName: String): Arch =
            Arch.entries.first { it.actualName == actualName }

        private val rustTargetsByDestinationArch = mapOf<Pair<Destination, Arch>, RustTarget>(
            (Destination.MacOS to Arch.Arm64) to RustPosixTarget.MacOSArm64,
            (Destination.MacOS to Arch.X64) to RustPosixTarget.MacOSX64,
            (Destination.Ios to Arch.Arm64) to RustAppleMobileTarget.IosArm64,
            (Destination.IosSimulator to Arch.Arm64) to RustAppleMobileTarget.IosSimulatorArm64,
            (Destination.IosSimulator to Arch.X64) to RustAppleMobileTarget.IosX64,
            (Destination.TvOS to Arch.Arm64) to RustAppleMobileTarget.TvOsArm64,
            (Destination.TvOSSimulator to Arch.Arm64) to RustAppleMobileTarget.TvOsSimulatorArm64,
            (Destination.TvOSSimulator to Arch.X64) to RustAppleMobileTarget.TvOsX64,
            (Destination.WatchOS to Arch.Arm64) to RustAppleMobileTarget.WatchOsDeviceArm64,
            (Destination.WatchOS to Arch.Arm64_32) to RustAppleMobileTarget.WatchOsArm64,
            (Destination.WatchOS to Arch.Armv7k) to RustAppleMobileTarget.WatchOsArm32,
            (Destination.WatchOSSimulator to Arch.Arm64) to RustAppleMobileTarget.WatchOsSimulatorArm64,
            (Destination.WatchOSSimulator to Arch.X64) to RustAppleMobileTarget.WatchOsX64,
        )
    }
}

private val sdkNameRegex = Regex("([a-z]+)([0-9.]+)")

@InternalGobleyGradleApi
private fun tryGetAppleSdk(sdkName: String): AppleSdk? {
    val match = sdkNameRegex.matchEntire(sdkName) ?: return null
    val destinationName = match.groups[1]?.value ?: return null
    val destination =
        AppleSdk.Destination.entries.firstOrNull { it.actualName == destinationName } ?: return null
    return AppleSdk(destination, match.groups[2]?.value ?: return null)
}

@InternalGobleyGradleApi
fun AppleSdk(sdkName: String): AppleSdk {
    return tryGetAppleSdk(sdkName) ?: throw IllegalArgumentException("Invalid SDK name: $sdkName")
}
