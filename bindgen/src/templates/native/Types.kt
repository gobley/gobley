
{%- import "macros.kt" as kt %}

{%- if ci.has_callback_definitions() %}
{%- include "ffi/CallbackInterfaceRuntime.kt" %}
{%- endif %}

{%- if ci.has_object_definitions() %}
{%- include "ObjectCleanerHelper.kt" %}
{%- endif %}

{%- for type_ in ci.iter_local_types() %}
{%- let type_name = type_|type_name(ci) %}
{%- let ffi_converter_name = type_|ffi_converter_name %}
{%- let canonical_type_name = type_|canonical_name %}
{%- let contains_object_references = ci.item_contains_object_references(type_) %}

{#
 # Map `Type` instances to an include statement for that type.
 #
 # There is a companion match in `KotlinCodeOracle::create_code_type()` which performs a similar function for the
 # Rust code.
 #
 #   - When adding additional types here, make sure to also add a match arm to that function.
 #   - To keep things manageable, let's try to limit ourselves to these 2 mega-matches
 #}

{%- match type_ %}

{%- when Type::Boolean %}
{%- include "ffi/BooleanHelper.kt" %}

{%- when Type::Int8 %}
{%- include "ffi/Int8Helper.kt" %}

{%- when Type::Int16 %}
{%- include "ffi/Int16Helper.kt" %}

{%- when Type::Int32 %}
{%- include "ffi/Int32Helper.kt" %}

{%- when Type::Int64 %}
{%- include "ffi/Int64Helper.kt" %}

{%- when Type::UInt8 %}
{%- include "ffi/UInt8Helper.kt" %}

{%- when Type::UInt16 %}
{%- include "ffi/UInt16Helper.kt" %}

{%- when Type::UInt32 %}
{%- include "ffi/UInt32Helper.kt" %}

{%- when Type::UInt64 %}
{%- include "ffi/UInt64Helper.kt" %}

{%- when Type::Float32 %}
{%- include "ffi/Float32Helper.kt" %}

{%- when Type::Float64 %}
{%- include "ffi/Float64Helper.kt" %}

{%- when Type::String %}
{%- include "ffi/StringHelper.kt" %}

{%- when Type::Bytes %}
{%- include "ffi/ByteArrayHelper.kt" %}

{%- when Type::Enum { name, module_path } %}
{%- let e = ci.get_enum_definition(name).unwrap() %}
{%- if !ci.is_name_used_as_error(name) %}
{% include "ffi/EnumTemplate.kt" %}
{%- else %}
{% include "ffi/ErrorTemplate.kt" %}
{%- endif -%}

{%- when Type::Object { module_path, name, .. } %}
{% include "ffi/ObjectTemplate.kt" %}
{%- let obj = ci.get_object_definition(name).unwrap() %}
{%- if obj.has_callback_interface() %}
{%- let vtable = obj.vtable().expect("trait interface should have a vtable") %}
{%- let vtable_methods = obj.vtable_methods() %}
{%- let ffi_init_callback = obj.ffi_init_callback() %}
{% include "CallbackInterfaceImpl.kt" %}
{%- endif %}

{%- when Type::Record { name, module_path } %}
{% include "ffi/RecordTemplate.kt" %}

{%- when Type::Optional { inner_type } %}
{% include "ffi/OptionalTemplate.kt" %}

{%- when Type::Sequence { inner_type } %}
{% include "ffi/SequenceTemplate.kt" %}

{%- when Type::Map { key_type, value_type } %}
{% include "ffi/MapTemplate.kt" %}

{%- when Type::CallbackInterface { module_path, name } %}
{% include "CallbackInterfaceTemplate.kt" %}

{%- when Type::Timestamp %}
{% include "ffi/TimestampHelper.kt" %}

{%- when Type::Duration %}
{% include "ffi/DurationHelper.kt" %}

{%- when Type::Custom { module_path, name, builtin } %}
{% include "ffi/CustomTypeTemplate.kt" %}

{%- else %}
{%- endmatch %}
{%- endfor %}

{%- for type_ in ci.iter_external_types() %}
{%- let name = type_.name().unwrap() %}
{%- let module_path = type_.module_path().unwrap() %}
{% include "ExternalTypeTemplate.kt" %}
{%- endfor %}


{%- if ci.has_async_fns() %}
{# Import types needed for async support #}
{{ self.add_import("kotlin.coroutines.resume") }}
{{ self.add_import("kotlinx.coroutines.launch") }}
{{ self.add_import("kotlinx.coroutines.suspendCancellableCoroutine") }}
{{ self.add_import("kotlinx.coroutines.CancellableContinuation") }}
{{ self.add_import("kotlinx.coroutines.DelicateCoroutinesApi") }}
{{ self.add_import("kotlinx.coroutines.Job") }}
{{ self.add_import("kotlinx.coroutines.GlobalScope") }}
{{ self.add_import("kotlinx.coroutines.withContext") }}
{{ self.add_import("kotlinx.coroutines.IO") }}
{{ self.add_import("kotlinx.coroutines.Dispatchers") }}
{%- endif %}
