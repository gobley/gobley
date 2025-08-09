
{%- let namespace = ci.namespace_for_module_path(module_path)? %}
{%- let package_name=self.external_type_package_name(module_path, namespace) %}
{%- include "ffi/ExternalTypeTemplate.kt" %}

{%- let fully_qualified_type_name = "{}.{}"|format(package_name, name|class_name(ci)) %}
{%- let fully_qualified_ffi_converter_name = "{}.FfiConverterType{}"|format(package_name, name) %}
{%- let fully_qualified_rustbuffer_name = "{}.RustBuffer"|format(package_name) %}
{%- let local_rustbuffer_name = "RustBuffer{}"|format(name) %}
{%- let fully_qualified_rustbuffer_by_value_name = "{}.RustBufferByValue"|format(package_name) %}
{%- let local_rustbuffer_by_value_name = "RustBuffer{}ByValue"|format(name) %}

{{- self.add_import(fully_qualified_type_name) }}
{{- self.add_import(fully_qualified_ffi_converter_name) }}
{{ self.add_import_as(fully_qualified_rustbuffer_name, local_rustbuffer_name) }}
{{ self.add_import_as(fully_qualified_rustbuffer_by_value_name, local_rustbuffer_by_value_name) }}

internal fun RustBufferByValue.as{{ name }}(): {{ local_rustbuffer_by_value_name }} {
    return {{ local_rustbuffer_by_value_name }}(
        capacity = capacity,
        len = len,
        data = data,
    )
}

internal fun {{ local_rustbuffer_by_value_name }}.from{{ name }}ToLocal(): RustBufferByValue {
    return RustBufferByValue(
        capacity = capacity,
        len = len,
        data = data,
    )
}

internal fun {{ fully_qualified_ffi_converter_name }}.read{{ name }}(buf: ByteBuffer): {{ name|class_name(ci) }} {
    return read({{ package_name }}.ByteBuffer(buf.internal()))
}

internal fun {{ fully_qualified_ffi_converter_name }}.write{{ name }}(value: {{ name|class_name(ci) }}, buf: ByteBuffer) {
    write(value, {{ package_name }}.ByteBuffer(buf.internal()))
}