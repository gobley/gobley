
{%- let type_name = type_|type_name(ci) %}
{%- let ffi_converter_name = type_|ffi_converter_name %}
{%- let canonical_type_name = type_|canonical_name %}

{% if e.is_flat() %}
{%- call kt::docstring(e, 0) %}
sealed class {{ type_name }}(message: String): kotlin.Exception(message){% if contains_object_references %}, Disposable {% endif %} {
    {% for variant in e.variants() -%}
    {%- call kt::docstring(variant, 4) %}
    class {{ variant|error_variant_name }}(message: String) : {{ type_name }}(message)
    {% endfor %}
}
{%- else %}
{%- call kt::docstring(e, 0) %}
sealed class {{ type_name }}: kotlin.Exception(){% if contains_object_references %}, Disposable {% endif %} {
    {% for variant in e.variants() -%}
    {%- call kt::docstring(variant, 4) %}
    {%- let variant_name = variant|error_variant_name %}
    class {{ variant_name }}(
        {%- for field in variant.fields() -%}
        {%- call kt::docstring(field, 8) %}
        val {% call kt::field_name(field, loop.index) %}: {{ field|type_name(ci) }},
        {%- endfor %}
    ) : {{ type_name }}() {
        override val message
            get() = "{%- for field in variant.fields() %}{% call kt::field_name_unquoted(field, loop.index) %}=${ {% call kt::field_name(field, loop.index) %} }{% if !loop.last %}, {% endif %}{% endfor %}"
        {%- if contains_object_references %}

        override fun destroy() {
            {% if variant.has_fields() -%}
            {%- call kt::destroy_fields(variant, 12) -%}
            {%- else -%}
            // Nothing to destroy
            {%- endif %}
        }
        {%- endif %}
    }
    {% endfor %}
}
{%- endif %}
