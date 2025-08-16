/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app.event

import gobley.uniffi.examples.app.signalConnect
import kotlinx.cinterop.*
import org.gnome.gitlab.gtk.*

@OptIn(ExperimentalForeignApi::class)
open class EventControllerKey : EventController() {
    final override val eventController: CPointer<GtkEventController> = gtk_event_controller_key_new()!!

    open fun keyReleased(keyVal: guint, keyCode: guint, state: GdkModifierType) {}

    init {
        signalConnect(eventController, "key-released", ::keyReleased)
    }
}
