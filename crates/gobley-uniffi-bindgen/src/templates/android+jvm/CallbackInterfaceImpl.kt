{%- let trait_impl=format!("uniffiCallbackInterface{}", name) %}

// Put the implementation in an object so we don't pollute the top-level namespace
internal object {{ trait_impl }} {
    {%- for (ffi_callback, meth) in vtable_methods.iter() %}
    internal object {{ meth.name()|var_name }}: {{ ffi_callback.name()|ffi_callback_name }} {
        override fun callback (
            {%- call kt::arg_list_ffi_decl(ffi_callback, 12) %}
        )
        {%- if let Some(return_type) = ffi_callback.return_type() -%}
            : {{ return_type|ffi_type_name_by_value(ci) }}
        {%- endif %} {
            val uniffiObj = {{ ffi_converter_name }}.handleMap.get(uniffiHandle)
            val makeCall = {% if meth.is_async() %}suspend {% endif %}{ ->
                uniffiObj.{{ meth.name()|fn_name() }}(
                    {%- for arg in meth.arguments() %}
                    {%- if arg|as_ffi_type|ref|need_non_null_assertion %}
                    {{ arg|lift_fn }}({{ arg.name()|var_name }}!!),
                    {%- else %}
                    {{ arg|lift_fn }}({{ arg.name()|var_name }}),
                    {%- endif -%}
                    {%- endfor %}
                )
            }
            {%- if !meth.is_async() %}

            {%- match meth.return_type() %}
            {%- when Some(return_type) %}
            val writeReturn = { uniffiResultValue: {{ return_type|type_name(ci) }} ->
                uniffiOutReturn.setValue({{ return_type|lower_fn }}(uniffiResultValue))
            }
            {%- when None %}
            val writeReturn = { _: Unit ->
                @Suppress("UNUSED_EXPRESSION")
                uniffiOutReturn
                Unit
            }
            {%- endmatch %}

            {%- match meth.throws_type() %}
            {%- when None %}
            uniffiTraitInterfaceCall(uniffiCallStatus, makeCall, writeReturn)
            {%- when Some(error_type) %}
            uniffiTraitInterfaceCallWithError(
                uniffiCallStatus,
                makeCall,
                writeReturn,
            ) { e: {{error_type|type_name(ci) }} -> {{ error_type|lower_fn }}(e) }
            {%- endmatch %}

            {%- else %}
            val uniffiHandleSuccess = { {% if meth.return_type().is_some() %}returnValue{% else %}_{% endif %}: {% match meth.return_type() %}{%- when Some(return_type) %}{{ return_type|type_name(ci) }}{%- when None %}Unit{% endmatch %} ->
                val uniffiResult = {{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}UniffiByValue(
                    {%- if let Some(return_type) = meth.return_type() %}
                    {{ return_type|lower_fn }}(returnValue),
                    {%- endif %}
                    UniffiRustCallStatusHelper.allocValue()
                )
                uniffiResult.write()
                uniffiFutureCallback.callback(uniffiCallbackData, uniffiResult)
            }
            val uniffiHandleError = { callStatus: UniffiRustCallStatusByValue ->
                uniffiFutureCallback.callback(
                    uniffiCallbackData,
                    {{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}UniffiByValue(
                        {%- if let Some(return_type) = meth.return_type() %}
                        {{ return_type.into()|ffi_default_value }},
                        {%- endif %}
                        callStatus,
                    ),
                )
            }

            uniffiOutReturn.uniffiSetValue(
                {%- match meth.throws_type() %}
                {%- when None %}
                uniffiTraitInterfaceCallAsync(
                    makeCall,
                    uniffiHandleSuccess,
                    uniffiHandleError
                )
                {%- when Some(error_type) %}
                uniffiTraitInterfaceCallAsyncWithError(
                    makeCall,
                    uniffiHandleSuccess,
                    uniffiHandleError,
                ) { e: {{error_type|type_name(ci) }} -> {{ error_type|lower_fn }}(e) }
                {%- endmatch %}
            )
            {%- endif %}
        }
    }
    {%- endfor %}
    internal object uniffiFree: {{ "CallbackInterfaceFree"|ffi_callback_name }} {
        override fun callback(handle: Long) {
            {{ ffi_converter_name }}.handleMap.remove(handle)
        }
    }

    internal val vtable = {{ vtable|ffi_type_name(ci) }}(
        {%- for (ffi_callback, meth) in vtable_methods.iter() %}
        {{ meth.name()|var_name() }},
        {%- endfor %}
        uniffiFree,
    )

    internal fun register(lib: UniffiLib) {
        lib.{{ ffi_init_callback.name() }}(vtable)
    }
}