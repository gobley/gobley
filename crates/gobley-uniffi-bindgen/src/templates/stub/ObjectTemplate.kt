
{%- let obj = ci.get_object_definition(name).unwrap() %}
{%- let interface_name = self::object_interface_name(ci, obj) %}
{%- let impl_class_name = self::object_impl_name(ci, obj) %}
{%- let methods = obj.methods() %}
{%- let interface_docstring = obj.docstring() %}
{%- let is_error = ci.is_name_used_as_error(name) %}
{%- let ffi_converter_name = obj|ffi_converter_name %}

{%- call kt::docstring(obj, 0) %}
{% if (is_error) %}
{{ visibility() }}actual open class {{ impl_class_name }} : kotlin.Exception, Disposable, {{ interface_name }} {
{% else -%}
{{ visibility() }}actual open class {{ impl_class_name }}: Disposable, {{ interface_name }}
{%- for t in obj.trait_impls() -%}
, {{ self::trait_interface_name(ci, t.trait_name)? }}
{%- endfor %} {
{%- endif %}

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    {{ visibility() }}actual constructor(noPointer: NoPointer)

    {%- match obj.primary_constructor() %}
    {%- when Some(cons) %}
    {%-     if cons.is_async() %}
    // Note no constructor generated for this object as it is async.
    {%-     else %}
    {%- call kt::docstring(cons, 4) %}

    {{ visibility() }}actual constructor({% call kt::arg_list(cons, false) -%}) {
        TODO()
    }
    {%-     endif %}
    {%- when None %}
    {%- endmatch %}

    actual override fun destroy() {
        TODO()
    }

    actual override fun close() {
        TODO()
    }

    {% for meth in obj.methods() -%}
    {%- call kt::func_decl_with_stub("actual override", meth, 4) -%}
    {% endfor %}

    {%- for tm in obj.uniffi_traits() %}
    {%-     match tm %}
    {%         when UniffiTrait::Display { fmt } %}
    actual override fun toString(): String {
        TODO()
    }
    {%         when UniffiTrait::Eq { eq, ne } %}
    {# only equals used #}
    actual override fun equals(other: Any?): Boolean {
        TODO()
    }
    {%         when UniffiTrait::Hash { hash } %}
    actual override fun hashCode(): Int {
        TODO()
    }
    {%-         else %}
    {%-     endmatch %}
    {%- endfor %}

    {# XXX - "companion object" confusion? How to have alternate constructors *and* be an error? #}
    {% if !obj.alternate_constructors().is_empty() -%}
    {{ visibility() }}actual companion object {
        {% for cons in obj.alternate_constructors() -%}
        {%- call kt::func_decl_with_stub("actual", cons, 8) %}
        {% endfor %}
    }
    {% else %}
    {{ visibility() }}actual companion object
    {% endif %}
}
