/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app

import gobley.uniffi.examples.todolist.TodoList
import kotlin.system.exitProcess

@OptIn(ExperimentalStdlibApi::class)
fun main(args: Array<String>) {
    val application = object : Application("dev.gobley.uniffi.examples.app", args) {
        override fun activate() {
            ContentWindow(this, TodoList()).run {
                title = "TodoList App"
                resizable = false
                setDefaultSize(300, 200)
                present()
            }
        }
    }
    val status = application.use { it.run() }
    exitProcess(status)
}
