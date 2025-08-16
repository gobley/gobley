/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app.widgets

import kotlinx.cinterop.*
import org.gnome.gitlab.gtk.*

@OptIn(ExperimentalForeignApi::class)
class Grid : Widget() {
    override val widget = gtk_grid_new()!!
    private val grid = GTK_GRID(widget.reinterpret())!!

    fun attach(widget: Widget, column: Int, row: Int, width: Int, height: Int) {
        gtk_grid_attach(grid, widget.widget, column, row, width, height)
    }

    companion object {
        @Suppress("FunctionName")
        @OptIn(ExperimentalForeignApi::class)
        private inline fun GTK_GRID(instance: CPointer<GTypeInstance>?): CPointer<GtkGrid>? {
            return g_type_check_instance_cast(instance, gtk_grid_get_type())?.reinterpret()
        }
    }
}
