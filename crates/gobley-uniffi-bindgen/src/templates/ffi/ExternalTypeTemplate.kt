{%- if ci.is_name_used_as_error(name) %}
{%- let class_name = name|class_name(ci) %}

object {{ class_name }}ErrorHandler : UniffiRustCallStatusErrorHandler<{{ class_name }}> {
    override fun lift(errorBuf: RustBufferByValue): {{ class_name }} = {{ package_name }}.{{ class_name }}ErrorHandler.lift(errorBuf.as{{ name }}())
}

{%- endif %}