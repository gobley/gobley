{%- call kt::docstring_value(ci.namespace_docstring(), 0) %}

@file:Suppress("RemoveRedundantBackticks")

package {{ config.package_name() }}

// Public interface members begin here.
{{ type_helper_code }}

{% import "macros.kt" as kt %}

{%- for func in ci.function_definitions() %}
{%- include "TopLevelFunctionTemplate.kt" %}
{%- endfor %}
