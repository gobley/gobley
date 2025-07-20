
{%- match config.custom_types.get(name.as_str())  %}
{%- when None %}
{#- Define the type using typealiases to the builtin #}
/**
 * Typealias from the type name used in the UDL file to the builtin type.  This
 * is needed because the UDL type name is used in function/method signatures.
 * It's also what we have an external type that references a custom type.
 */
typealias {{ type_name }} = {{ builtin|type_name(ci) }}

{%- when Some(config) %}

{%- let ffi_type_name=builtin|ffi_type|ref|ffi_type_name_by_value(ci) %}

{# When the config specifies a different type name, create a typealias for it #}
{%- match config.type_name %}
{%- when Some(concrete_type_name) %}
/**
 * Typealias from the type name used in the UDL file to the custom type.  This
 * is needed because the UDL type name is used in function/method signatures.
 * It's also what we have an external type that references a custom type.
 */
typealias {{ type_name }} = {{ concrete_type_name }}
{%- else %}
{%- endmatch %}

{%- match config.imports %}
{%- when Some(imports) %}
{%- for import_name in imports %}
{{ self.add_import(import_name) }}
{%- endfor %}
{%- else %}
{%- endmatch %}

{%- endmatch %}
