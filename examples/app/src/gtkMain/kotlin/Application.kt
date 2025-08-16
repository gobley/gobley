/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app

import kotlinx.cinterop.*
import org.gnome.gitlab.gtk.*

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)
open class Application(
    applicationId: String,
    private val args: Array<String>,
) : AutoCloseable {
    val application = gtk_application_new(applicationId, 0u)!!

    init {
        signalConnect(application.reinterpret(), "activate", ::activate)
    }

    protected open fun activate() {}

    fun run(): Int {
        memScoped {
            val argc = args.size + 1
            val argv = (arrayOf("ExamplesApp") + args).map { it.cstr.ptr }.toCValues()
            return g_application_run(G_APPLICATION(application.reinterpret()), argc, argv)
        }
    }

    override fun close() {
        g_object_unref(application)
    }
}

@Suppress("FunctionName")
@OptIn(ExperimentalForeignApi::class)
private inline fun G_APPLICATION(instance: CPointer<GTypeInstance>?): CPointer<GApplication>? {
    return g_type_check_instance_cast(instance, g_application_get_type())?.reinterpret()
}
