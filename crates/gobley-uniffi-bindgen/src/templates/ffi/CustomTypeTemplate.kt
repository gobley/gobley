
{%- match config.custom_types.get(name.as_str())  %}
{%- when None %}

{{ visibility() }}typealias {{ ffi_converter_name }} = {{ builtin|ffi_converter_name }}

{%- when Some(config) %}

{%- let ffi_type_name=builtin|ffi_type|ref|ffi_type_name_by_value(ci) %}

{# When the config specifies a different type name, create a typealias for it #}

{%- match config.imports %}
{%- when Some(imports) %}
{%- for import_name in imports %}
{{ self.add_import(import_name) }}
{%- endfor %}
{%- else %}
{%- endmatch %}

{{ visibility() }}object {{ ffi_converter_name }}: FfiConverter<{{ type_name }}, {{ ffi_type_name }}> {
    override fun lift(value: {{ ffi_type_name }}): {{ type_name }} {
        val builtinValue = {{ builtin|lift_fn }}(value)
        return {{ config.lift("builtinValue") }}
    }

    override fun lower(value: {{ type_name }}): {{ ffi_type_name }} {
        val builtinValue = {{ config.lower("value") }}
        return {{ builtin|lower_fn }}(builtinValue)
    }

    override fun read(buf: ByteBuffer): {{ type_name }} {
        val builtinValue = {{ builtin|read_fn(ci) }}(buf)
        return {{ config.lift("builtinValue") }}
    }

    override fun allocationSize(value: {{ type_name }}): ULong {
        val builtinValue = {{ config.lower("value") }}
        return {{ builtin|allocation_size_fn }}(builtinValue)
    }

    override fun write(value: {{ type_name }}, buf: ByteBuffer) {
        val builtinValue = {{ config.lower("value") }}
        {{ builtin|write_fn(ci) }}(builtinValue, buf)
    }
}

{%- endmatch %}
