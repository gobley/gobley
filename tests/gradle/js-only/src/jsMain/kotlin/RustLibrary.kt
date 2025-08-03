/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.tests.gradle.jsonly

import gobley.wasm.gobley_fixture_gradle_js_only.RustWebAssemblyImports
import org.khronos.webgl.Int32Array

object RustLibrary {
    private val externalFunctions = mutableListOf<() -> Unit>()

    private val instance =
        gobley.wasm.gobley_fixture_gradle_js_only.createInstance(
            RustWebAssemblyImports(
                env = RustWebAssemblyImports.Import_env(
                    external_function = ::invokeAllRegisteredExternalFunctions,
                ),
                gradle_function_imports = RustWebAssemblyImports.Import_gradle_function_imports(
                    kotlin_side_function_1 = ::theKotlinFunctionCalledAsFunctionPointer1,
                    kotlin_side_function_2 = ::theKotlinFunctionCalledAsFunctionPointer2,
                ),
            ),
        )

    fun add(lhs: Int, rhs: Int): Int = instance.exports.add(lhs, rhs)

    fun sendStructToRust(content: Array<Int>): Int {
        try {
            val sp = instance.exports.__gobley_add_to_stack_pointer(-80)
            val slice = Int32Array(instance.exports.memory.buffer, sp, 20)
            slice.set(content)
            return instance.exports.consume_big_struct(sp)
        } finally {
            instance.exports.__gobley_add_to_stack_pointer(80)
        }
    }

    fun registerExternalFunction(externalFn: () -> Unit) {
        externalFunctions.add(externalFn)
    }

    fun invokeAllRegisteredExternalFunctionsTwiceViaRust() {
        instance.exports.call_external_function_twice()
    }

    private fun invokeAllRegisteredExternalFunctions() {
        externalFunctions.forEach { it() }
    }

    fun theKotlinFunctionCalledAsFunctionPointer1(i: Int, f: Float): Double {
        return (i * 2).toDouble() + f.toDouble() / 4.0
    }

    fun theKotlinFunctionCalledAsFunctionPointer2(i: Int): Float {
        return i.toFloat() * 3.0f
    }

    fun callKotlinFunctionAsFunctionPointer1(): Double {
        return instance.exports.call_function_pointer_twice(
            RustWebAssemblyImports.Import_gradle_function_imports.tblIdx_kotlin_side_function_1
        )
    }

    fun callKotlinFunctionAsFunctionPointer2(): Double {
        return instance.exports.call_multiple_function_pointers(
            RustWebAssemblyImports.Import_gradle_function_imports.tblIdx_kotlin_side_function_1,
            RustWebAssemblyImports.Import_gradle_function_imports.tblIdx_kotlin_side_function_2,
        )
    }
}
