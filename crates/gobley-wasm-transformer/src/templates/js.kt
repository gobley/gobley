{%- if let Some(package_name) = package_name -%}
package {{ package_name }}

{% endif -%}
private const val BASE64 = "{{ base64 }}"

private external interface Buffer {
    companion object {
        fun from(string: String, encoding: String): Buffer
    }
}

private external interface Uint8Array {
    companion object {
        fun from(string: String, transform: (String) -> Byte): Uint8Array
    }
}

internal external class WebAssembly {
    class Module {
        internal constructor(buffer: Buffer)
        internal constructor(buffer: Uint8Array)
        companion object
    }

    class Instance<T: Exports>(module: Module, imports: Any) {
        val exports: T
    }

    class Memory
    interface Exports {
        val memory: Memory
    }
}

internal external interface RustWebAssemblyExports : WebAssembly.Exports {
    @JsName("__gobley_add_to_stack_pointer")
    fun gobleyAddToStackPointer(amount: Int): Int
}

private external fun atob(s: String): String

private fun isBufferUnavailable() = js("typeof Buffer === \"undefined\"")

private fun moduleFromBase64(string: String): WebAssembly.Module {
    return if (isBufferUnavailable() as Boolean) {
        val buffer = Uint8Array.from(atob(string)) { it[0].code.toByte() }
        WebAssembly.Module(buffer)
    } else {
        val buffer = Buffer.from(string, "base64")
        WebAssembly.Module(buffer)
    }
}

internal val module: WebAssembly.Module by lazy {
    moduleFromBase64(BASE64)
}

internal class RustWebAssemblyImports(
    {%- for import_module in import_modules() %}
    {{ import_module }}: Import_{{ import_module }},
    {%- endfor %}
) {
    {%- for import_module in import_modules() %}
    class Import_{{ import_module }}(
        {%- for (import_name, import_function) in import_functions_from_module(import_module) %}
        {{ import_name }}: {{ function_to_kt_signature(import_function) }},
        {%- endfor %}
    )
    {%- endfor %}
}

internal fun createInstance(
    imports: RustWebAssemblyImports,
): WebAssembly.Instance<RustWebAssemblyExports> {
    return WebAssembly.Instance(module, imports)
}