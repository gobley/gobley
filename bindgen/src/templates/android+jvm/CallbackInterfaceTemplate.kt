
{%- let cbi = ci.get_callback_interface_definition(name).unwrap() %}
{%- let ffi_init_callback = cbi.ffi_init_callback() %}
{%- let interface_name = cbi|type_name(ci) %}
{%- let interface_docstring = cbi.docstring() %}
{%- let methods = cbi.methods() %}
{%- let vtable = cbi.vtable() %}
{%- let vtable_methods = cbi.vtable_methods() %}

{% include "CallbackInterfaceImpl.kt" %}

object {{ ffi_converter_name }} : FfiConverterCallbackInterface<{{ interface_name }}>()