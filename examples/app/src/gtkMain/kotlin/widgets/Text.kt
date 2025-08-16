/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app.widgets

import kotlinx.cinterop.*
import org.gnome.gitlab.gtk.*

@OptIn(ExperimentalForeignApi::class)
class Text : Widget() {
    override val widget = gtk_text_new()!!
    private val text = GTK_TEXT(widget.reinterpret())!!

    val buffer = Buffer(gtk_entry_buffer_new("", 0)!!).apply {
        gtk_text_set_buffer(this@Text.text, entryBuffer)
    }

    class Buffer(val entryBuffer: CPointer<GtkEntryBuffer>) {
        var text: String
            get() = gtk_entry_buffer_get_text(entryBuffer)!!.toKString()
            set(value) = gtk_entry_buffer_set_text(entryBuffer, value, value.utf8.size)
    }

    companion object {
        @Suppress("FunctionName")
        @OptIn(ExperimentalForeignApi::class)
        private inline fun GTK_TEXT(instance: CPointer<GTypeInstance>?): CPointer<GtkText>? {
            return g_type_check_instance_cast(instance, gtk_text_get_type())?.reinterpret()
        }
    }
}
