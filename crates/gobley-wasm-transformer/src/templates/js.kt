{%- if let Some(package_name) = package_name -%}
package {{ package_name }}

{% endif -%}
private const val BASE64 = "{{ base64 }}"

internal external interface Buffer {
    companion object {
        fun from(string: String, encoding: String): Buffer
    }
}

internal external interface Uint8Array {
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

internal val instance: WebAssembly.Instance<RustWebAssemblyExports> by lazy {
    val module = moduleFromBase64(BASE64)
    WebAssembly.Instance(module, object {})
}