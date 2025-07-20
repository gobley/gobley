{%- for type_ in ci.iter_external_types() %}
{%- let name = type_.name().unwrap() %}
{%- let module_path = type_.module_path().unwrap() %}
{% include "ExternalTypeTemplate.h" %}
{%- endfor %}
