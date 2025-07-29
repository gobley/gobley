
{#
// Kotlin's `enum class` construct doesn't support variants with associated data,
// but is a little nicer for consumers than its `sealed class` enum pattern.
// So, we switch here, using `enum class` for enums with no associated data
// and `sealed class` for the general case.
#}

{%- let should_generate_serializable = config.generate_serializable() && e|serializable_enum(ci) -%}

{%- if e.is_flat() %}

{%- call kt::docstring(e, 0) %}
{% match e.variant_discr_type() %}
{% when None %}
{% if should_generate_serializable %}@kotlinx.serialization.Serializable{% endif %}
enum class {{ type_name }} {
    {% for variant in e.variants() -%}
    {%- call kt::docstring(variant, 4) %}
    {{ variant|variant_name(config) }}{% if loop.last %};{% else %},{% endif %}
    {%- endfor %}
    companion object
}
{% when Some(variant_discr_type) %}
{% if should_generate_serializable %}@kotlinx.serialization.Serializable{% endif %}
enum class {{ type_name }}(val value: {{ variant_discr_type|type_name(ci) }}) {
    {% for variant in e.variants() -%}
    {%- call kt::docstring(variant, 4) %}
    {{ variant|variant_name(config) }}({{ e|variant_discr_literal(loop.index0) }}){% if loop.last %};{% else %},{% endif %}
    {%- endfor %}
    companion object
}
{% endmatch %}
{% else %}

{%- call kt::docstring(e, 0) %}
{% if should_generate_serializable %}@kotlinx.serialization.Serializable{% endif %}
sealed class {{ type_name }}{% if contains_object_references %}: Disposable {% endif %} {
    {% for variant in e.variants() -%}
    {%- let variant_type_name = variant|variant_type_name(ci) -%}
    {%- let should_generate_variant_serializable = config.generate_serializable() && variant|serializable_enum_variant(ci) -%}
    {%- call kt::docstring(variant, 4) %}
    {%- if !variant.has_fields() %}
    {% if should_generate_variant_serializable %}@kotlinx.serialization.Serializable{% endif %}
    {% if config.use_data_objects() %}data {% endif %}object {{ variant_type_name }} : {{ type_name }}() {% if contains_object_references %} {
        override fun destroy() = Unit
    }
    {% endif %}
    {% else -%}
    {%- let should_generate_equals_hash_code = variant|should_generate_equals_hash_code_enum_variant -%}
    {% if should_generate_variant_serializable %}@kotlinx.serialization.Serializable{% endif %}
    data class {{ variant_type_name }}(
        {%- for field in variant.fields() -%}
        {%- call kt::docstring(field, 8) %}
        val {% call kt::field_name(field, loop.index) %}: {{ field|type_name(ci) }},
        {%- endfor %}
    ) : {{ type_name }}() {
        {%- if should_generate_equals_hash_code -%}
        {%- call kt::generate_equals_hash_code(variant, variant_type_name, 8) -%}
        {%- endif -%}
        {%- if contains_object_references %}
        override fun destroy() {
            {%- if variant.has_fields() -%}
            {%- call kt::destroy_fields(variant, 12) -%}
            {%- else %}
            // Nothing to destroy
            {%- endif %}
        }
        {%- endif %}
    }
    {%- endif %}
    {% endfor %}
}

{% endif %}
