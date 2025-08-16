/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.app

import gobley.uniffi.examples.arithmeticpm.add
import gobley.uniffi.examples.arithmeticpm.sub
import gobley.uniffi.examples.todolist.TodoEntry
import gobley.uniffi.examples.todolist.TodoList
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.fflush
import platform.posix.printf
import platform.posix.scanf
import platform.posix.stdout

@OptIn(ExperimentalForeignApi::class)
fun main() = memScoped {
    val todoList = TodoList()
    var clicked: ULong = 0u
    val stride: ULong = 1u

    while (true) {
        printMenu(clicked, todoList)
        printf("Choose: ")
        fflush(stdout)
        when (readInt()) {
            1 -> {
                printf("Enter task: ")
                fflush(stdout)
                val task = readLine()
                val text = "$clicked $task"
                todoList.addEntry(TodoEntry(text))
                clicked = add(clicked, stride)
                printf("Added: %s\n", text)
            }

            2 -> printEntries(todoList)

            3 -> {
                val entries = todoList.getEntries()
                if (entries.isEmpty()) {
                    printf("Nothing to delete.\n")
                } else {
                    printEntries(todoList)
                    printf("Delete by index (1..%s): ", entries.size.toString())
                    fflush(stdout)
                    val idx = readInt()
                    val i = idx - 1
                    if (i in entries.indices) {
                        val text = entries[i].text
                        todoList.clearItem(text)
                        if (clicked > 0uL) clicked = sub(clicked, stride)
                        printf("Deleted: %s\n", text)
                    } else {
                        printf("Invalid index.\n")
                    }
                }
            }

            0 -> return
            else -> continue
        }
    }
}

private fun printMenu(clicked: ULong, todoList: TodoList) {
    printf("\n=== TodoList ===\n")
    printf("Clicked: %s\n", clicked.toString())
    printf("# of Tasks: %s\n", todoList.getEntries().size.toString())
    printf("  [1] Add task\n")
    printf("  [2] List tasks\n")
    printf("  [3] Delete task\n")
    printf("  [0] Exit\n")
}

private fun printEntries(todoList: TodoList) {
    printf("\n=== TodoList ===\n")
    val entries = todoList.getEntries()
    if (entries.isEmpty()) {
        printf("(empty)\n")
    } else {
        for ((i, e) in entries.withIndex()) {
            printf("%s: %s\n", (i + 1).toString(), e.text)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readInt(): Int = memScoped {
    val integer = alloc<IntVar>()
    while (true) {
        try {
            if (scanf("%d", integer.ptr) == 1) {
                return integer.value
            }
        } finally {
            consumeUntilLineEnd()
        }
    }
    @Suppress("UNREACHABLE_CODE")
    error("UNREACHABLE_CODE")
}

@OptIn(ExperimentalForeignApi::class)
private fun readLine(): String = memScoped {
    val line = allocArray<ByteVar>(1024)
    while (true) {
        try {
            if (scanf(" %1023[^\n", line) == 1) {
                return line.toKString()
            }
        } finally {
            consumeUntilLineEnd()
        }
    }
    @Suppress("UNREACHABLE_CODE")
    error("UNREACHABLE_CODE")
}

@OptIn(ExperimentalForeignApi::class)
private fun consumeUntilLineEnd() {
    scanf("%*[^\n]")
    scanf("%*c")
    fflush(stdout)
}