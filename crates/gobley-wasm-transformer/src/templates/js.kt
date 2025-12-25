@file:Suppress("ClassName", "Unused", "RemoveRedundantBackticks", "PropertyName")

{% if let Some(package_name) = package_name -%}
package {{ package_name }}

{% endif -%}

{%- for global_entity in global_entities() %}

{{ global_entity.modifier }} val `{{ global_entity.name }}`: {{ global_entity.ty }} =
{%- match global_entity.lang %}
{%- when GlobalEntityLang::JavaScript %} js("""{{ global_entity.expr }}""")
{%- when GlobalEntityLang::Kotlin %} {{ global_entity.expr }}
{%- endmatch %}
{%- endfor %}

{%- if !wasm_bindgen_js_modules().is_empty() %}

internal typealias WasmBindgenJsModuleFactory = ((String) -> dynamic, dynamic, dynamic) -> Unit

private val wbgAdditionalFactoryById: MutableMap<String, WasmBindgenJsModuleFactory> = mutableMapOf()

internal fun addWasmBindgenModuleFactory(id: String, factory: WasmBindgenJsModuleFactory) {
    if (wbgAdditionalFactoryById.containsKey(id)) {
        error("Cannot register wasm-bindgen JS module `$id` more than once")
    }
    wbgAdditionalFactoryById[id] = factory
}

private val wbgModuleCache: MutableMap<String, dynamic> = mutableMapOf()

internal fun wasmBindgenRequire(id: String): dynamic {
    if (wbgModuleCache.containsKey(id)) {
        return wbgModuleCache[id].exports
    }

    val moduleFactory = wbgFactoryById[id]
        ?: wbgAdditionalFactoryById[id]
        ?: error("wasm-bindgen JS module `$id` doesn't exist")

    val module = js("""({ exports: {} })""")
    wbgModuleCache[id] = module

    moduleFactory(::wasmBindgenRequire, module, module.exports)

    return module.exports
}

internal fun wasmBindgenRequireStem(): dynamic {
    return wasmBindgenRequire("{{ Transformer::WASM_BINDGEN_STEM_FILENAME }}")
}
{%- endif %}

private const val BASE64: String = "{{ base64 }}"

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
    class Module

    class Instance<T : Any>(module: Module, imports: Any) {
        val exports: T
    }

    class Memory(descriptor: Any) {
        val buffer: org.khronos.webgl.ArrayBuffer
        fun grow(delta: Int)
    }

    class Table(descriptor: Any, value: Any = definedExternally) {
        operator fun get(idx: Int): Any
        operator fun set(idx: Int, value: Any)
        val length: Int
        fun grow(delta: Int, value: Any = definedExternally)
    }

    class Global<T : Any>(descriptor: Any, value: T? = definedExternally) {
        var value: T
    }
}

internal external interface RustWebAssemblyExports {
    {%- for export in exports() %}
    val {{ export.name }}: {{ export_to_kt_signature(export) }}
    {%- endfor %}
}

private fun atob(s: String): String = js("atob(s)")

private fun isBufferUnavailable(): Boolean = js("typeof Buffer === \"undefined\"")

private fun moduleFromBuffer(buffer: Any): WebAssembly.Module = js("new WebAssembly.Module(buffer)") as WebAssembly.Module

private fun moduleFromBase64(string: String): WebAssembly.Module {
    return moduleFromBuffer(if (isBufferUnavailable()) {
        Uint8Array.from(atob(string)) { it[0].code.toByte() }
    } else {
        Buffer.from(string, "base64")
    })
}

internal val module: WebAssembly.Module by lazy {
    moduleFromBase64(BASE64)
}

internal class RustWebAssemblyImports(
    {%- for (idx, wasm_bindgen_js_module) in wasm_bindgen_js_modules().iter().enumerate() %}
    @JsName("{{ wasm_bindgen_js_module.name }}")
    val wbgModule{{ idx }}: dynamic = wasmBindgenRequire("{{ wasm_bindgen_js_module.name }}"),
    {%- endfor %}
    {%- for import_module in import_modules() %}
    @JsName("{{ import_module }}")
    val `{{ import_module }}`: `Import_{{ import_module }}`,
    {%- endfor %}
) {
    {%- for import_module in import_modules() %}
    class `Import_{{ import_module }}`(
        {%- for import in imports_from_module(import_module) %}
        @JsName("{{ import.name }}")
        val `{{ import.name }}`: {{ import_to_kt_signature(import) }},
        {%- endfor %}
    ) {
        companion object {
            {%- for import in imports_from_module(import_module) %}
            {%- if let Some(table_entry_idx) = import_to_function_table_entry_idx(import ) %}
            const val tblIdx_{{ import.name }}: Int = {{ table_entry_idx }}
            {%- endif %}
            {%- endfor %}
        }
    }
    {%- endfor %}
}

internal fun createInstance(
    imports: RustWebAssemblyImports,
): WebAssembly.Instance<RustWebAssemblyExports> {
    return WebAssembly.Instance(module, imports)
}