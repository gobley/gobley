/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app

import gobley.uniffi.examples.app.event.EventControllerKey
import gobley.uniffi.examples.app.widgets.*
import gobley.uniffi.examples.arithmeticpm.add
import gobley.uniffi.examples.arithmeticpm.sub
import gobley.uniffi.examples.todolist.TodoEntry
import gobley.uniffi.examples.todolist.TodoList
import org.gnome.gitlab.gtk.*
import kotlinx.cinterop.*

class ContentWindow(
    application: Application, private val todoList: TodoList
) : Window(application) {
    private val stride = 1UL
    private var clicked = 0uL

    private val clickedLabel = Label(clicked.toString()).apply {
        setMinimumSize(width = 40)
    }

    @OptIn(ExperimentalForeignApi::class)
    private val textFieldControllerKey = object : EventControllerKey() {
        override fun keyReleased(keyVal: guint, keyCode: guint, state: GdkModifierType) {
            if (keyVal.toInt() == GDK_KEY_Return) {
                addTodo()
            }
        }
    }
    private val textField = Text().apply {
        addController(textFieldControllerKey)
        hexpand = true
    }

    @OptIn(ExperimentalForeignApi::class)
    private val listViewControllerKey = object : EventControllerKey() {
        override fun keyReleased(keyVal: guint, keyCode: guint, state: GdkModifierType) {
            if ((keyVal.toInt() == GDK_KEY_BackSpace) or (keyVal.toInt() == GDK_KEY_Delete)) {
                removeTodo()
            }
        }
    }
    private val listView = ListView().apply {
        addController(listViewControllerKey)
    }

    init {
        val grid = Grid().apply {
            attach(clickedLabel, 0, 0, 1, 1)
            attach(textField, 1, 0, 3, 1)
            attach(
                object : Button("Add") {
                    override fun clicked() = addTodo()
                },
                4, 0, 1, 1,
            )
            attach(
                ScrolledWindow().apply {
                    setChild(listView)
                    vexpand = true
                },
                0, 1, 5, 1,
            )

            styleContext.add(STYLE)
        }
        setChild(grid)
    }

    private fun addTodo() {
        val entryText = "$clicked ${textField.buffer.text}"
        todoList.addEntry(TodoEntry(entryText))
        textField.buffer.text = ""
        clicked = add(clicked, stride)
        updateViews()
    }

    private fun removeTodo() {
        val todo = todoList.getEntries().getOrNull(listView.selectedIndex)
        if (todo != null) {
            todoList.clearItem(todo.text)
            clicked = sub(clicked, 1uL)
            updateViews()
        }
    }

    private fun updateViews() {
        listView.clear()
        todoList.getEntries().forEach { listView.add(it.text) }
        clickedLabel.text = clicked.toString()
    }
}

const val STYLE = """
    text {
        background-color: white;
    }
    listview {
        border-top: 1px solid black;
    }
"""
