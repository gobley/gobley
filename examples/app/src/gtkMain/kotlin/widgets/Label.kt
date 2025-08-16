/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app.widgets

import kotlinx.cinterop.*
import org.gnome.gitlab.gtk.*

@OptIn(ExperimentalForeignApi::class)
open class Label(text: String? = null) : Widget() {
    final override val widget = gtk_label_new(text)!!
    private val label = GTK_LABEL(widget.reinterpret())!!

    var text: String?
        get() = gtk_label_get_text(label)?.toKString()
        set(value) = gtk_label_set_text(label, value)

    companion object {
        @Suppress("FunctionName")
        @OptIn(ExperimentalForeignApi::class)
        private inline fun GTK_LABEL(instance: CPointer<GTypeInstance>?): CPointer<GtkLabel>? {
            return g_type_check_instance_cast(instance, gtk_label_get_type())?.reinterpret()
        }
    }
}
