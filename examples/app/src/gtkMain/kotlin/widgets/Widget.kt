/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app.widgets

import gobley.uniffi.examples.app.event.EventController
import kotlinx.cinterop.*
import org.gnome.gitlab.gtk.*

@OptIn(ExperimentalForeignApi::class)
abstract class Widget {
    abstract val widget: CPointer<GtkWidget>
    var hexpand: Boolean
        get() = gtk_widget_get_hexpand(widget) == TRUE
        set(value) = gtk_widget_set_hexpand(widget, value.toByte().toInt())

    var vexpand: Boolean
        get() = gtk_widget_get_vexpand(widget) == TRUE
        set(value) = gtk_widget_set_vexpand(widget, value.toByte().toInt())

    fun addController(controller: EventController) {
        gtk_widget_add_controller(widget, controller.eventController)
    }

    fun setMinimumSize(width: Int = -1, height: Int = -1) {
        gtk_widget_set_size_request(widget, width, height)
    }

    val styleContext by lazy { StyleContext(gtk_widget_get_style_context(widget)!!) }

    class StyleContext(private val styleContext: CPointer<GtkStyleContext>) {
        fun add(css: String, priority: Int = GTK_STYLE_PROVIDER_PRIORITY_USER) {
            val cssProvider = gtk_css_provider_new()!!
            gtk_css_provider_load_from_string(cssProvider, css)
            val display = gtk_style_context_get_display(styleContext)
            gtk_style_context_add_provider_for_display(
                display,
                GTK_STYLE_PROVIDER(cssProvider.reinterpret()),
                priority.toUInt(),
            )
        }
    }

    companion object {
        @Suppress("FunctionName")
        @OptIn(ExperimentalForeignApi::class)
        private inline fun GTK_STYLE_PROVIDER(instance: CPointer<GTypeInstance>?): CPointer<GtkStyleProvider>? {
            return g_type_check_instance_cast(instance, gtk_style_provider_get_type())?.reinterpret()
        }
    }
}
