
{%- let type_name = type_|type_name(ci) %}
{%- let ffi_converter_name = type_|ffi_converter_name %}
{%- let canonical_type_name = type_|canonical_name %}

{{ visibility() }}object {{ type_name }}ErrorHandler : UniffiRustCallStatusErrorHandler<{{ type_name }}> {
    override fun lift(errorBuf: RustBufferByValue): {{ type_name }} = {{ ffi_converter_name }}.lift(errorBuf)
}

{{ visibility() }}object {{ e|ffi_converter_name }} : FfiConverterRustBuffer<{{ type_name }}> {
    override fun read(buf: ByteBuffer): {{ type_name }} {
        {%- if e.is_flat() %}
        return when (buf.getInt()) {
            {%- for variant in e.variants() %}
            {{ loop.index }} -> {{ type_name }}.{{ variant|error_variant_name }}({{ Type::String.borrow()|read_fn(ci) }}(buf))
            {%- endfor %}
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
        {%- else %}
        return when (buf.getInt()) {
            {%- for variant in e.variants() %}
            {{ loop.index }} -> {{ type_name }}.{{ variant|error_variant_name }}({% if variant.has_fields() %}
                {% for field in variant.fields() -%}
                {{ field|read_fn(ci) }}(buf),
                {% endfor -%}
            {%- endif -%})
            {%- endfor %}
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
        {%- endif %}
    }

    override fun allocationSize(value: {{ type_name }}): ULong {
        {%- if e.is_flat() %}
        return 4UL
        {%- else %}
        return when (value) {
            {%- for variant in e.variants() %}
            is {{ type_name }}.{{ variant|error_variant_name }} -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                {%- for field in variant.fields() %}
                + {{ field|allocation_size_fn }}(value.{% call kt::field_name(field, loop.index) %})
                {%- endfor %}
            )
            {%- endfor %}
        }
        {%- endif %}
    }

    override fun write(value: {{ type_name }}, buf: ByteBuffer) {
        when (value) {
            {%- for variant in e.variants() %}
            is {{ type_name }}.{{ variant|error_variant_name }} -> {
                buf.putInt({{ loop.index }})
                {%- for field in variant.fields() %}
                {{ field|write_fn(ci) }}(value.{% call kt::field_name(field, loop.index) %}, buf)
                {%- endfor %}
                Unit
            }
            {%- endfor %}
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}
