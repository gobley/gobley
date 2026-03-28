/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.support.uppercaseFirstChar

enum class Variant {
    Debug, Release;

    override fun toString(): String = name.lowercase()
}

fun Variant(s: String): Variant = when (s.lowercase()) {
    "debug" -> Variant.Debug
    "release" -> Variant.Release
    else -> throw IllegalArgumentException("Invalid variant name: $s")
}

/**
 * A utility container for values varying by build variants.
 */
interface VariantValues<T : Any> {
    val debug: T
    val release: T
}

fun <T : Any> VariantValues<T>.get(variant: Variant): T = when (variant) {
    Variant.Debug -> debug
    Variant.Release -> release
}

fun <T : Any> VariantValues<T>.variantOf(value: T): Variant? = when (value) {
    debug -> Variant.Debug
    release -> Variant.Release
    else -> null
}

private class FixedVariantValues<T : Any>(override val debug: T, override val release: T) : VariantValues<T> {
    override fun toString(): String = "Fixed(debug=$debug, release=$release)"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is FixedVariantValues<*>) {
            return false
        }

        return debug == other.debug && release == other.release
    }

    override fun hashCode(): Int {
        var result = debug.hashCode()
        result = 31 * result + release.hashCode()
        return result
    }
}

fun <T : Any> VariantValues(debug: T, release: T): VariantValues<T> = FixedVariantValues(debug, release)

private class UniformVariantValues<T : Any>(private val value: T) : VariantValues<T> {
    override val debug: T = value
    override val release: T = value
    override fun toString(): String = "Uniform(value=$value)"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is UniformVariantValues<*>) {
            return false
        }

        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}

fun <T : Any> VariantValues(all: T): VariantValues<T> = UniformVariantValues(all)

private class OverridingVariantValues<T : Any>(
    private val newDebug: T?,
    private val newRelease: T?,
    private val inner: VariantValues<T>,
) : VariantValues<T> {
    override val debug: T
        get() = newDebug ?: inner.debug
    override val release: T
        get() = newRelease ?: inner.release

    override fun toString(): String = "Overriding(newDebug=$newDebug, newRelease=$newRelease, inner=$inner)"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is OverridingVariantValues<*>) {
            return false
        }

        return newDebug == other.newDebug && newRelease == other.newRelease && inner == other.inner
    }

    override fun hashCode(): Int {
        var result = newDebug?.hashCode() ?: 0
        result = 31 * result + (newRelease?.hashCode() ?: 0)
        result = 31 * result + inner.hashCode()
        return result
    }
}

fun <T : Any> VariantValues<T>.with(debug: T? = null, release: T? = null): VariantValues<T> = OverridingVariantValues(
    newDebug = debug, newRelease = release, inner = this
)

class EmptyVariantValues<T : Any> : VariantValues<T> {
    override val debug: T
        get() = throw GradleException("Value for debug builds not provided")
    override val release: T
        get() = throw GradleException("Value for release builds not provided")

    override fun toString(): String = "Empty"
}

sealed interface VariantValuesConfiguration<T : Any> {
    fun debug(newDebug: T)
    fun release(newRelease: T)
    fun default(newDefault: T)
}

private class VariantValuesConfigurationImpl<T : Any>(
    private val property: Property<VariantValues<T>>,
) : VariantValuesConfiguration<T> {
    private fun currentValue(): VariantValues<T> = property.getOrElse(EmptyVariantValues())

    private var newDebug: T? = null
    override fun debug(newDebug: T) {
        this.newDebug = newDebug
    }

    private var newRelease: T? = null
    override fun release(newRelease: T) {
        this.newRelease = newRelease
    }

    private var newDefault: T? = null
    override fun default(newDefault: T) {
        this.newDefault = newDefault
    }

    fun saveChanges() {
        val newDebug = this.newDebug
        val newRelease = this.newRelease
        val newDefault = this.newDefault
        if (newDefault != null) {
            property.set(VariantValues(all = newDefault))
        }

        if (newDebug != null && newRelease != null) {
            property.set(VariantValues(debug = newDebug, release = newRelease))
        } else if (newDebug != null || newRelease != null) {
            property.set(currentValue().with(debug = newDebug, release = newRelease))
        }
    }
}

operator fun <T : Any> Property<VariantValues<T>>.invoke(action: VariantValuesConfiguration<T>.() -> Unit) {
    VariantValuesConfigurationImpl(this).run {
        action()
        saveChanges()
    }
}

fun <T : Any> Provider<VariantValues<T>>.with(debug: T? = null, release: T? = null): Provider<VariantValues<T>> =
    map { it.with(debug, release) }

fun <T : Any> Provider<VariantValues<T>>.debug(): Provider<T> = map { it.debug }

fun <T : Any> Provider<VariantValues<T>>.release(): Provider<T> = map { it.release }

fun <T : Any> Provider<VariantValues<T>>.get(variant: Variant): Provider<T> = when (variant) {
    Variant.Debug -> debug()
    Variant.Release -> release()
}

fun <T : Any> Provider<VariantValues<T>>.get(variant: Provider<Variant>): Provider<T> =
    zip(variant) { provider, profileValue -> provider.get(profileValue) }

val Task.variant: Variant?
    get() {
        val lowercaseName = name.lowercase()
        return when {
            lowercaseName.contains("debug") -> Variant.Debug
            lowercaseName.contains("release") -> Variant.Release
            else -> null
        }
    }

fun <T : Any> NamedDomainObjectContainer<T>.getByVariant(variant: Variant, postfix: String = ""): T {
    return getByName(variant.name.lowercase() + postfix.uppercaseFirstChar())
}

fun <T : Any> NamedDomainObjectContainer<T>.getByVariant(prefix: String, variant: Variant, postfix: String = ""): T {
    return getByName(prefix + variant.name.lowercase().uppercaseFirstChar() + postfix.uppercaseFirstChar())
}
