
{%- for def in ci.ffi_definitions() %}

{%- match def %}
{% when FfiDefinition::CallbackFunction(callback) %}
typedef
    {%- match callback.return_type() %}{% when Some(return_type) %} {{ return_type|header_ffi_type_name(ci) }} {% when None %} void {% endmatch -%}
    (*{{ callback.name()|ffi_callback_name }})(
        {%- for arg in callback.arguments() -%}
        {{ arg.type_().borrow()|header_ffi_type_name(ci) }}
        {%- if !loop.last || callback.has_rust_call_status_arg() %}, {% endif %}
        {%- endfor -%}
        {%- if callback.has_rust_call_status_arg() %}
        UniffiRustCallStatus *_Nonnull uniffiCallStatus
        {%- endif %}
    );
{% when FfiDefinition::Struct(ffi_struct) %}
typedef struct {{ ffi_struct.name()|ffi_struct_name }} {
    {%- for field in ffi_struct.fields() %}
    {{ field.type_().borrow()|header_ffi_type_name(ci) }} {{ field.name()|var_name_raw }};
    {%- endfor %}
} {{ ffi_struct.name()|ffi_struct_name }};
{% when FfiDefinition::Function(func) %}
{% match func.return_type() -%}{%- when Some(type_) %}{{ type_|header_ffi_type_name(ci) }}{% when None %}void{% endmatch %} {{ func.name() }}(
    {%- if func.arguments().len() > 0 %}
        {%- for arg in func.arguments() %}
            {{- arg.type_().borrow()|header_ffi_type_name(ci) }} {{ arg.name()|var_name|unquote -}}{% if !loop.last || func.has_rust_call_status_arg() %}, {% endif %}
        {%- endfor %}
        {%- if func.has_rust_call_status_arg() %}UniffiRustCallStatus *_Nonnull out_status{% endif %}
    {%- else %}
        {%- if func.has_rust_call_status_arg() %}UniffiRustCallStatus *_Nonnull out_status{%- else %}void{% endif %}
    {% endif %}
);
{%- endmatch %}
{%- endfor %}
