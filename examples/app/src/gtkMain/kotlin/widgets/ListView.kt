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
open class ListView : Widget() {

    private val factory = gtk_signal_list_item_factory_new()!!
    private fun factorySetUp(listItem: CPointer<GtkListItem>) {
        val label = gtk_label_new(null)!!
        gtk_label_set_xalign(GTK_LABEL(label.reinterpret()), 0.0f)
        gtk_list_item_set_child(listItem, label)
    }

    private fun factoryBind(listItem: CPointer<GtkListItem>) {
        val label = gtk_list_item_get_child(listItem)!!
        val text = gtk_list_item_get_item(listItem)!!
        gtk_label_set_text(
            GTK_LABEL(label.reinterpret()),
            gtk_string_object_get_string(GTK_STRING_OBJECT(text.reinterpret()))?.toKString(),
        )
    }

    init {
        signalConnect(factory, "setup", ::factorySetUp)
        signalConnect(factory, "bind", ::factoryBind)
    }

    private val model = gtk_string_list_new(null)

    private val selectionModel = GTK_SELECTION_MODEL(gtk_single_selection_new(G_LIST_MODEL(model)))

    final override val widget =
        gtk_list_view_new(selectionModel, factory)!!

    fun add(entry: String) {
        gtk_string_list_append(model, entry)
    }

    fun clear() {
        gtk_string_list_splice(model, 0u, g_list_model_get_n_items(G_LIST_MODEL(model)), null)
    }

    val selectedIndex: Int
        get() = gtk_bitset_get_minimum(gtk_selection_model_get_selection(selectionModel)).let {
            if (it == G_MAXUINT) -1 else it.toInt()
        }

    companion object {
        @Suppress("FunctionName")
        @OptIn(ExperimentalForeignApi::class)
        private inline fun GTK_LABEL(instance: CPointer<GTypeInstance>?): CPointer<GtkLabel>? {
            return g_type_check_instance_cast(instance, gtk_label_get_type())?.reinterpret()
        }

        @Suppress("FunctionName")
        @OptIn(ExperimentalForeignApi::class)
        private inline fun GTK_STRING_OBJECT(instance: CPointer<GTypeInstance>?): CPointer<GtkStringObject>? {
            return g_type_check_instance_cast(instance, gtk_string_object_get_type())?.reinterpret()
        }
    }
}
