{%- if config.kotlin_multiplatform -%}
{%- call kt::func_decl_with_body("actual", func, 0) -%}
{%- else -%}
{%- call kt::func_decl_with_body("", func, 0) -%}
{%- endif %}