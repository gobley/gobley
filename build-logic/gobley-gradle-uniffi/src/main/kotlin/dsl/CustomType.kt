/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi.dsl

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class CustomType @Inject constructor() {
    /**
     * The list of import directives needed to implement this custom type.
     */
    abstract val imports: ListProperty<String>

    /**
     * Optional. Stores a name of a type. If this value is present, an alias declaration for the
     * type will be generated.
     */
    abstract val typeName: Property<String>

    /**
     * The expression that converts an expression of the underlying type to an expression of the
     * custom type. The underlying type expression will be assigned to the placeholder, `{}`.
     */
    abstract val lift: Property<String>

    /**
     * The expression that converts an expression of the custom type to an expression of the
     * underlying type. The custom type expression will be assigned to the placeholder, `{}`.
     */
    abstract val lower: Property<String>
}