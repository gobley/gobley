/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app

import kotlinx.cinterop.*
import org.gnome.gitlab.gtk.*

// Add overloads as needed

@OptIn(ExperimentalForeignApi::class)
fun signalConnect(instance: gpointer, signal: String, handler: () -> Unit): gulong {
    fun signalCallHandler(@Suppress("UNUSED_PARAMETER") instance: gpointer?, data: gpointer?) {
        data?.asStableRef<() -> Unit>()?.get()?.invoke()
    }

    val stableRef = StableRef.create(handler)
    return g_signal_connect_data(
        instance,
        signal,
        staticCFunction(::signalCallHandler).reinterpret(),
        stableRef.asCPointer(),
        staticCFunction(::signalDestroyHandler),
        0u
    )
}

@OptIn(ExperimentalForeignApi::class)
fun signalConnect(instance: gpointer, signal: String, handler: (guint, guint, GdkModifierType) -> Unit): gulong {
    @OptIn(ExperimentalForeignApi::class)
    fun signalCallHandler(
        @Suppress("UNUSED_PARAMETER") instance: gpointer?,
        keyVal: guint,
        keyCode: guint,
        state: GdkModifierType,
        data: gpointer?
    ) {
        data?.asStableRef<(guint, guint, GdkModifierType) -> Unit>()?.get()?.invoke(keyVal, keyCode, state)
    }

    val stableRef = StableRef.create(handler)
    return g_signal_connect_data(
        instance,
        signal,
        staticCFunction(::signalCallHandler).reinterpret(),
        stableRef.asCPointer(),
        staticCFunction(::signalDestroyHandler),
        0u
    )
}

@OptIn(ExperimentalForeignApi::class)
fun signalConnect(instance: gpointer, signal: String, handler: (CPointer<GtkListItem>) -> Unit): gulong {
    @OptIn(ExperimentalForeignApi::class)
    fun signalCallHandler(
        @Suppress("UNUSED_PARAMETER") instance: gpointer?,
        listItem: CPointer<GtkListItem>,
        data: gpointer?
    ) {
        data?.asStableRef<(CPointer<GtkListItem>) -> Unit>()?.get()?.invoke(listItem)
    }

    val stableRef = StableRef.create(handler)
    return g_signal_connect_data(
        instance,
        signal,
        staticCFunction(::signalCallHandler).reinterpret(),
        stableRef.asCPointer(),
        staticCFunction(::signalDestroyHandler),
        0u
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun signalDestroyHandler(data: gpointer?, @Suppress("UNUSED_PARAMETER") closure: CPointer<GClosure>?) {
    data?.asStableRef<Any>()?.dispose()
}
