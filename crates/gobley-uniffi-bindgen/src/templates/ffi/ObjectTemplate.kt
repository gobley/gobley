
{%- let obj = ci.get_object_definition(name).unwrap() %}
{%- let interface_name = self::object_interface_name(ci, obj) %}
{%- let impl_class_name = self::object_impl_name(ci, obj) %}
{%- let methods = obj.methods() %}
{%- let interface_docstring = obj.docstring() %}
{%- let is_error = ci.is_name_used_as_error(name) %}
{%- let ffi_converter_name = obj|ffi_converter_name %}
{%- let actual -%}
{%- if config.kotlin_multiplatform -%}
{%-     let actual = "actual" -%}
{%- else -%}
{%-     let actual = "" -%}
{%- endif %}
{%- let actual_override -%}
{%- if config.kotlin_multiplatform -%}
{%-     let actual_override = "actual override" -%}
{%- else -%}
{%-     let actual_override = "override" -%}
{%- endif %}

{%- macro emit_actual %}{% if config.kotlin_multiplatform %}actual {% endif %}{% endmacro -%}

{%- call kt::docstring(obj, 0) %}
{% if (is_error) %}
{{ visibility() }}{% call emit_actual %}open class {{ impl_class_name }} : kotlin.Exception, Disposable, {{ interface_name }} {
{% else -%}
{{ visibility() }}{% call emit_actual %}open class {{ impl_class_name }}: Disposable, {{ interface_name }}
{%- for t in obj.trait_impls() -%}
, {{ self::object_trait_impl_interface_name(ci, t)? }}
{%- endfor %} {
{%- endif %}

    {{ visibility() }}constructor(handle: Long) {
        this.handle = handle
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiHandleDestroyer(handle))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    {{ visibility() }}{% call emit_actual %}constructor(noPointer: NoPointer) {
        this.handle = 0L
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiHandleDestroyer(0L))
    }

    {%- match obj.primary_constructor() %}
    {%- when Some(cons) %}
    {%-     if cons.is_async() %}
    // Note no constructor generated for this object as it is async.
    {%-     else %}
    {%- call kt::docstring(cons, 4) %}
    {{ visibility() }}{% call emit_actual %}constructor({% call kt::arg_list(cons, false) -%}) : this(
        {% call kt::to_ffi_call(cons, 8) %}
    )
    {%-     endif %}
    {%- when None %}
    {%- endmatch %}

    protected val handle: Long
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    {% call emit_actual %}override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    {% call emit_actual %}override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithHandle(block: (handle: Long) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the handle being freed concurrently.
        try {
            return block(this.uniffiCloneHandle())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiHandleDestroyer(private val handle: Long) : Disposable {
        override fun destroy() {
            if (handle == 0L) {
                // Fake object created with `NoPointer`, don't try to free.
                return
            }
            uniffiRustCall { status ->
                UniffiLib.{{ obj.ffi_object_free().name() }}(handle, status)
            }
        }
    }

    {{ visibility() }}fun uniffiCloneHandle(): Long {
        if (handle == 0L) {
            throw InternalException("uniffiCloneHandle() called on NoPointer object")
        }
        return uniffiRustCall { status ->
            UniffiLib.{{ obj.ffi_object_clone().name() }}(handle, status)
        }
    }

    {% for meth in obj.methods() -%}
    {%- call kt::func_decl_with_body(actual_override, meth, 4) -%}
    {% endfor %}

    {%- for tm in obj.uniffi_traits() %}
    {%-     match tm %}
    {%         when UniffiTrait::Display { fmt } %}
    {% call emit_actual %}override fun toString(): String {
        return {{ fmt.return_type().unwrap()|lift_fn }}({% call kt::to_ffi_call(fmt, 8) %})
    }
    {%         when UniffiTrait::Eq { eq, ne } %}
    {# only equals used #}
    {% call emit_actual %}override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is {{ impl_class_name}}) return false
        return {{ eq.return_type().unwrap()|lift_fn }}({% call kt::to_ffi_call(eq, 8) %})
    }
    {%         when UniffiTrait::Hash { hash } %}
    {% call emit_actual %}override fun hashCode(): Int {
        return {{ hash.return_type().unwrap()|lift_fn }}({%- call kt::to_ffi_call(hash, 8) %}).toInt()
    }
    {%-         else %}
    {%-     endmatch %}
    {%- endfor %}

    {# XXX - "companion object" confusion? How to have alternate constructors *and* be an error? #}
    {% if !obj.alternate_constructors().is_empty() -%}
    {{ visibility() }}{% call emit_actual %}companion object {
        {% for cons in obj.alternate_constructors() -%}
        {%- call kt::func_decl_with_body(actual, cons, 8) %}
        {% endfor %}
    }
    {% else %}
    {{ visibility() }}{% call emit_actual %}companion object
    {% endif %}
}

{% if is_error %}
{{ visibility() }}object {{ impl_class_name }}ErrorHandler : UniffiRustCallStatusErrorHandler<{{ impl_class_name }}> {
    override fun lift(errorBuf: RustBufferByValue): {{ impl_class_name }} {
        // Due to some mismatches in the ffi converter mechanisms, errors are a RustBuffer.
        val bb = errorBuf.asByteBuffer()
        if (bb == null) {
            throw InternalException("?")
        }
        return {{ ffi_converter_name }}.read(bb)
    }
}
{% endif %}

{% macro converter_type(obj) -%}
{%- if obj.has_callback_interface() -%}
{{ interface_name }}
{%- else -%}
{{ impl_class_name }}
{%- endif -%}
{%- endmacro %}

{{ visibility() }}object {{ ffi_converter_name }}: FfiConverter<{%- call converter_type(obj) -%}, Long> {
    {%- if obj.has_callback_interface() %}
    internal val handleMap = UniffiHandleMap<{%- call converter_type(obj) -%}>()
    {%- endif %}

    override fun lower(value: {% call converter_type(obj) %}): Long {
        {%- if obj.has_callback_interface() %}
        if (value is {{ impl_class_name }}) {
            // Rust-implemented object. Clone the handle and return it.
            return value.uniffiCloneHandle()
        } else {
            // Kotlin object, generate a new vtable handle and return that.
            return handleMap.insert(value)
        }
        {%- else %}
        return value.uniffiCloneHandle()
        {%- endif %}
    }

    override fun lift(value: Long): {% call converter_type(obj) %} {
        {%- if obj.has_callback_interface() %}
        if ((value and 1L) == 0L) {
            // Rust-generated handle, construct a new class that wraps the handle.
            return {{ impl_class_name }}(value)
        } else {
            // Kotlin-generated handle, get the object from the handle map.
            return handleMap.remove(value)
        }
        {%- else %}
        return {{ impl_class_name }}(value)
        {%- endif %}
    }

    override fun read(buf: ByteBuffer): {% call converter_type(obj) %} {
        // The Rust code always writes handles as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong())
    }

    override fun allocationSize(value: {% call converter_type(obj) %}): ULong = 8UL

    override fun write(value: {% call converter_type(obj) %}, buf: ByteBuffer) {
        // The Rust code always expects handles written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value))
    }
}
