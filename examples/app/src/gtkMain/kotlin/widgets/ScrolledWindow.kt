/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app.widgets

import gobley.uniffi.examples.app.signalConnect
import kotlinx.cinterop.*
import org.gnome.gitlab.gtk.*

@OptIn(ExperimentalForeignApi::class)
class ScrolledWindow : Widget() {
    override val widget = gtk_scrolled_window_new()!!
    private val scrolledWindow = GTK_GRID(widget.reinterpret())!!

    fun setChild(widget: Widget) {
        gtk_scrolled_window_set_child(scrolledWindow, widget.widget)
    }

    companion object {
        @Suppress("FunctionName")
        @OptIn(ExperimentalForeignApi::class)
        private inline fun GTK_GRID(instance: CPointer<GTypeInstance>?): CPointer<GtkScrolledWindow>? {
            return g_type_check_instance_cast(instance, gtk_scrolled_window_get_type())?.reinterpret()
        }
    }
}