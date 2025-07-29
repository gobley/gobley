{%- for def in ci.ffi_definitions() %}
{%- match def %}
{%- when FfiDefinition::CallbackFunction(callback) %}
internal typealias {{ callback.name()|ffi_callback_name }} = {{ ci.namespace() }}.cinterop.{{ callback.name()|ffi_callback_name }}
{%- when FfiDefinition::Struct(ffi_struct) %}
internal typealias {{ ffi_struct.name()|ffi_struct_name }} = CPointer<{{ ci.namespace() }}.cinterop.{{ ffi_struct.name()|ffi_struct_name }}>
{% for field in ffi_struct.fields() %}
internal var {{ ffi_struct.name()|ffi_struct_name }}.{{ field.name()|var_name }}: {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(ci) }}
    {%- let type_name = field.type_().borrow()|ffi_type_name_for_ffi_struct(ci) %}
    get() = pointed.{{ field.name()|var_name }}{% if type_name.contains("ByValue") %}.readValue(){% endif %}
    set(value) {
        {%- match field.type_() %}
        {%- when FfiType::RustBuffer(_) %}
        value.write(pointed.{{ field.name()|var_name }}.rawPtr)
        {%- when FfiType::RustCallStatus %}
        value.write(pointed.{{ field.name()|var_name }}.rawPtr)
        {%- when _ %}
        pointed.{{ field.name()|var_name }} = value
        {%- endmatch %}
    }
{% endfor %}

internal fun {{ ffi_struct.name()|ffi_struct_name }}.uniffiSetValue(other: {{ ffi_struct.name()|ffi_struct_name }}) {
    {%- for field in ffi_struct.fields() %}
    {{ field.name()|var_name }} = other.{{ field.name()|var_name }}
    {%- endfor %}
}
internal fun {{ ffi_struct.name()|ffi_struct_name }}.uniffiSetValue(other: {{ ffi_struct.name()|ffi_struct_name }}UniffiByValue) {
    {%- for field in ffi_struct.fields() %}
    {{ field.name()|var_name }} = other.{{ field.name()|var_name }}
    {%- endfor %}
}

internal typealias {{ ffi_struct.name()|ffi_struct_name }}UniffiByValue = CValue<{{ ci.namespace() }}.cinterop.{{ ffi_struct.name()|ffi_struct_name }}>
fun {{ ffi_struct.name()|ffi_struct_name }}UniffiByValue(
    {%- for field in ffi_struct.fields() %}
    {{ field.name()|var_name }}: {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(ci) }},
    {%- endfor %}
): {{ ffi_struct.name()|ffi_struct_name }}UniffiByValue {
    return cValue<{{ ci.namespace() }}.cinterop.{{ ffi_struct.name()|ffi_struct_name }}> {
        {%- for field in ffi_struct.fields() -%}
        {%- match field.type_() %}
        {%- when FfiType::RustBuffer(_) %}
        {{ field.name()|var_name }}.write(this.{{ field.name()|var_name }}.rawPtr)
        {%- when FfiType::RustCallStatus %}
        {{ field.name()|var_name }}.write(this.{{ field.name()|var_name }}.rawPtr)
        {%- when _ %}
        this.{{ field.name()|var_name }} = {{ field.name()|var_name }}
        {%- endmatch -%}
        {%- endfor %}
    }
}

{% for field in ffi_struct.fields() %}
internal val {{ ffi_struct.name()|ffi_struct_name }}UniffiByValue.{{ field.name()|var_name }}: {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(ci) }}
    {%- let type_name = field.type_().borrow()|ffi_type_name_for_ffi_struct(ci) %}
    get() = useContents { {{ field.name()|var_name }}{% if type_name.contains("ByValue") %}.readValue(){% endif %} }
{% endfor %}

{%- when FfiDefinition::Function(_) %}
{# functions are handled below #}
{%- endmatch %}
{%- endfor %}

internal interface UniffiLib {
    companion object {
        internal val INSTANCE: UniffiLib by lazy {
            {% if self.initialization_fns(ci).is_empty() -%}
            UniffiLibInstance()
            {%- else -%}
            UniffiLibInstance().also { lib ->
            {%- for init_fn in self.initialization_fns(ci) %}
                {{ init_fn }}
            {%- endfor %}
            }
            {%- endif %}
        }
        {% if ci.contains_object_types() %}
        // The Cleaner for the whole library
        internal val CLEANER: UniffiCleaner by lazy {
            UniffiCleaner.create()
        }
        {%- endif %}
    }

    {% for func in ci.iter_ffi_function_definitions() -%}
    fun {{ func.name() }}(
        {%- call kt::arg_list_ffi_decl(func, 8) %}
    ): {% match func.return_type() %}{% when Some(return_type) %}{{ return_type.borrow()|ffi_type_name_by_value(ci) }}{% when None %}Unit{% endmatch %}
    {% endfor %}
}

internal class UniffiLibInstance: UniffiLib {
    {% for func in ci.iter_ffi_function_definitions() -%}
    override fun {{ func.name() }}(
        {%- call kt::arg_list_ffi_decl(func, 8) %}
    ): {% match func.return_type() -%}
    {%- when Some(return_type) -%}
    {{- return_type.borrow()|ffi_type_name_by_value(ci) -}}
    {%- when None -%}
    Unit
    {%- endmatch %} = {{ ci.namespace() }}.cinterop.{{ func.name() }}(
        {%- call kt::arg_list_ffi_call_native(func) %}
    )
    {%- if let Some(return_type) = func.return_type() -%}
    {{- return_type|ffi_cast_to_external_rust_buffer_if_needed(ci) -}}
    {%- endif %}
    {% endfor %}
}

fun uniffiEnsureInitialized() {
    UniffiLib.INSTANCE
}