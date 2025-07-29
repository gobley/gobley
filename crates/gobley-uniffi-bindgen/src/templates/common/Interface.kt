
{%- call kt::docstring_value(interface_docstring, 0) %}
interface {{ interface_name }} {
    {% for meth in methods.iter() -%}
    {%- call kt::func_decl("", meth, 4, true) %}
    {% endfor %}
    companion object
}
