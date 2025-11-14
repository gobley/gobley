/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.tests.gradle.jsonly

import gobley.wasm.gobley_fixture_gradle_js_only.RustWebAssemblyImports
import gobley.wasm.gobley_fixture_gradle_js_only.addWasmBindgenModuleFactory
import gobley.wasm.gobley_fixture_gradle_js_only.createInstance
import gobley.wasm.gobley_fixture_gradle_js_only.wasmBindgenRequire
import gobley.wasm.gobley_fixture_gradle_js_only.wasmBindgenRequireStem
import org.khronos.webgl.Int32Array

object RustLibrary {
    private val externalFunctions = mutableListOf<() -> Unit>()

    private fun myOuterModule(require: (String) -> dynamic, module: dynamic, exports: dynamic) {
        exports.outside_function_in_module = {}
    }

    init {
        addWasmBindgenModuleFactory("my-outer-module", ::myOuterModule)
    }

    private val instance = createInstance(
        RustWebAssemblyImports(
            env = RustWebAssemblyImports.Import_env(
                external_function = ::invokeAllRegisteredExternalFunctions,
            ),
            gradle_function_imports = RustWebAssemblyImports.Import_gradle_function_imports(
                kotlin_side_function_1 = ::theKotlinFunctionCalledAsFunctionPointer1,
                kotlin_side_function_2 = ::theKotlinFunctionCalledAsFunctionPointer2,
            ),
            `my-outer-module` = wasmBindgenRequire("my-outer-module"),
        ),
    ).apply {
        wasmBindgenRequireStem().__wbg_set_wasm(exports)
        exports.__wbindgen_start()
    }

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

    fun addUsingWasmBindgen(lhs: Int, rhs: Int): Int {
        return instance.exports.my_wb_add(lhs, rhs)
    }

    fun callOutsideFunctionsUsingWasmBindgen() {
        return instance.exports.call_outside_functions()
    }

    fun addUsingWasmBindgenJsDelegated(lhs: Int, rhs: Int): Int {
        return instance.exports.add_using_js_delegated(lhs, rhs)
    }

    fun checkWasmBindgenCanImportVariables() {
        return instance.exports.check_module_exported_constants()
    }

    fun createFoo(): dynamic {
        val foo = wasmBindgenRequireStem().Foo
        return js("""new foo()""")
    }
}
