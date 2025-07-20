{{ self.add_import("kotlinx.cinterop.invoke") }}

{%- let trait_impl=format!("uniffiCallbackInterface{}", name) %}

// Put the implementation in an object so we don't pollute the top-level namespace
internal object {{ trait_impl }} {
    {%- for (ffi_callback, meth) in vtable_methods.iter() %}
    internal fun {{ meth.name()|var_name }}(
        {%- call kt::arg_list_ffi_decl(ffi_callback, 8) %}
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
            val uniffiResult = cValue<{{ ci.namespace() }}.cinterop.{{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}> {
                {%- if let Some(return_type) = meth.return_type() %}
                {%- match return_type.into() %}
                {%- when FfiType::RustBuffer(_) %}
                {{ return_type|lower_fn }}(returnValue).write(this.returnValue.rawPtr)
                {%- when FfiType::RustCallStatus %}
                {{ return_type|lower_fn }}(returnValue).write(this.returnValue.rawPtr)
                {%- when _ %}
                this.returnValue = {{ return_type|lower_fn }}(returnValue)
                {%- endmatch %}
                {%- endif %}
                UniffiRustCallStatusHelper.allocValue().write(this.callStatus.rawPtr)
            }
            uniffiFutureCallback.invoke(uniffiCallbackData, uniffiResult)
        }
        val uniffiHandleError = { callStatus: UniffiRustCallStatusByValue ->
            val uniffiResult = cValue<{{ ci.namespace() }}.cinterop.{{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}> {
                {%- if let Some(return_type) = meth.return_type() %}
                {%- match return_type.into() %}
                {%- when FfiType::RustBuffer(_) %}
                {{ return_type.into()|ffi_default_value }}.write(this.returnValue.rawPtr)
                {%- when FfiType::RustCallStatus %}
                {{ return_type.into()|ffi_default_value }}.write(this.returnValue.rawPtr)
                {%- when _ %}
                this.returnValue = {{ return_type.into()|ffi_default_value }}
                {%- endmatch %}
                {%- endif %}
                callStatus.write(this.callStatus.rawPtr)
            }
            uniffiFutureCallback.invoke(uniffiCallbackData, uniffiResult)
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
    {% endfor %}
    internal fun uniffiFree(handle: Long) {
        {{ ffi_converter_name }}.handleMap.remove(handle)
    }

    internal val vtable = nativeHeap.alloc<{{ci.namespace()}}.cinterop.{{ vtable|ffi_type_name(ci) }}> {
        {%- for (ffi_callback, meth) in vtable_methods.iter() %}
        {% if ffi_callback|ffi_callback_needs_casting_native -%}
        @Suppress("UNCHECKED_CAST")
        {% endif -%}
        this.{{ meth.name()|var_name }} = staticCFunction {
            {%- for arg in ffi_callback.arguments() %}
            {{ arg.name().borrow()|var_name }}: {{ arg.type_().borrow()|ffi_type_name_by_value(ci) }},
            {%- endfor -%}
            {%- if ffi_callback.has_rust_call_status_arg() %}
            uniffiCallStatus: UniffiRustCallStatus,
            {%- endif %}
            ->
            {{ trait_impl }}.{{ meth.name()|var_name() }}(
                {%- for arg in ffi_callback.arguments() %}
                {{ arg.name().borrow()|var_name }},
                {%- endfor -%}
                {%- if ffi_callback.has_rust_call_status_arg() %}
                uniffiCallStatus,
                {%- endif %}
            )
        }
        {%- if ffi_callback|ffi_callback_needs_casting_native -%}
        {{ "" }} as {{ ci.namespace() }}.cinterop.{{ ffi_callback.name()|ffi_callback_name }}
        {%- endif -%}
        {%- endfor %}
        this.uniffiFree = staticCFunction { handle: Long ->
            {{ trait_impl }}.uniffiFree(handle)
        }
    }.ptr

    internal fun register(lib: UniffiLib) {
        lib.{{ ffi_init_callback.name() }}(vtable)
    }
}