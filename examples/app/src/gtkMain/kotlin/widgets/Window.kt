/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app.widgets

import gobley.uniffi.examples.app.Application
import kotlinx.cinterop.*
import org.gnome.gitlab.gtk.*

@OptIn(ExperimentalForeignApi::class)
open class Window(application: Application) : Widget() {
    final override val widget = gtk_application_window_new(application.application)!!
    private val window = GTK_WINDOW(widget.reinterpret())!!

    var title: String?
        get() = gtk_window_get_title(window)?.toKString()
        set(value) = gtk_window_set_title(window, value)

    var resizable: Boolean
        get() = gtk_window_get_resizable(window) == TRUE
        set(value) = gtk_window_set_resizable(window, if (value) TRUE else FALSE)

    fun setDefaultSize(width: Int, height: Int) {
        gtk_window_set_default_size(window, width, height)
    }

    fun present() {
        gtk_window_present(window)
    }

    fun setChild(widget: Widget) {
        gtk_window_set_child(window, widget.widget)
    }

    companion object {
        @Suppress("FunctionName")
        @OptIn(ExperimentalForeignApi::class)
        private inline fun GTK_WINDOW(instance: CPointer<GTypeInstance>?): CPointer<GtkWindow>? {
            return g_type_check_instance_cast(instance, gtk_window_get_type())?.reinterpret()
        }
    }
}
