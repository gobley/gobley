
{%- let obj = ci.get_object_definition(name).unwrap() %}
{%- let interface_name = self::object_interface_name(ci, obj) %}
{%- let impl_class_name = self::object_impl_name(ci, obj) %}
{%- let methods = obj.methods() %}
{%- let interface_docstring = obj.docstring() %}
{%- let is_error = ci.is_name_used_as_error(name) %}
{%- let ffi_converter_name = obj|ffi_converter_name %}

{%- include "Interface.kt" %}
{% if config.kotlin_multiplatform %}
{% call kt::docstring(obj, 0) %}
{% if (is_error) %}
{{ visibility() }}expect open class {{ impl_class_name }} : kotlin.Exception, Disposable, {{ interface_name }} {
{% else -%}
{{ visibility() }}expect open class {{ impl_class_name }}: Disposable, {{ interface_name }}
{%- for t in obj.trait_impls() -%}
, {{ self::trait_interface_name(ci, t.trait_name)? }}
{%- endfor %} {
{%- endif %}
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    {%- match obj.primary_constructor() %}
    {%- when Some(cons) %}
    {%-     if cons.is_async() %}
    @Suppress("ConvertSecondaryConstructorToPrimary")
    {%-     else %}
    {%- call kt::docstring(cons, 4) %}
    {%-     endif %}
    {%- when None %}
    @Suppress("ConvertSecondaryConstructorToPrimary")
    {%- endmatch %}
    {{ visibility() }}constructor(noPointer: NoPointer)

    {% match obj.primary_constructor() -%}
    {%- when Some(cons) -%}
    {%-     if cons.is_async() -%}
    // Note no constructor generated for this object as it is async.
    {%     else -%}
    {%- call kt::docstring(cons, 4) %}
    {{ visibility() }}constructor({% call kt::arg_list(cons, true) -%})
    {%-     endif %}
    {%- when None %}
    {%- endmatch %}

    override fun destroy()
    override fun close()

    {% for meth in obj.methods() -%}
    {%- call kt::func_decl("override", meth, 4, false) %}
    {% endfor %}

    {%- for tm in obj.uniffi_traits() %}
    {%-     match tm %}
    {%         when UniffiTrait::Display { fmt } %}
    override fun toString(): String
    {%         when UniffiTrait::Eq { eq, ne } %}
    {# only equals used #}
    override fun equals(other: Any?): Boolean
    {%         when UniffiTrait::Hash { hash } %}
    override fun hashCode(): Int
    {%-         else %}
    {%-     endmatch %}
    {%- endfor %}

    {# XXX - "companion object" confusion? How to have alternate constructors *and* be an error? #}
    {%- if !obj.alternate_constructors().is_empty() -%}
    {{ visibility() }}companion object {
        {% for cons in obj.alternate_constructors() -%}
        {%- call kt::func_decl("", cons, 8, false) %}
        {% endfor %}
    }
    {% else %}
    {{ visibility() }}companion object
    {%- endif %}
}
{% endif %}