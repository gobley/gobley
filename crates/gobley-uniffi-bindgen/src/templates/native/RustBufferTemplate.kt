{% include "ffi/RustBufferTemplate.kt" %}

{{ visibility() }}typealias RustBuffer = CPointer<{{ ci.namespace() }}.cinterop.RustBuffer>

{{ visibility() }}var RustBuffer.capacity: Long
    get() = pointed.capacity
    set(value) { pointed.capacity = value }
{{ visibility() }}var RustBuffer.len: Long
    get() = pointed.len
    set(value) { pointed.len = value }
{{ visibility() }}var RustBuffer.data: Pointer?
    get() = pointed.data
    set(value) { pointed.data = value?.reinterpret() }
{{ visibility() }}fun RustBuffer.asByteBuffer(): ByteBuffer? {
    {% call kt::check_rust_buffer_length("pointed.len") %}
    return ByteBuffer(
        pointed.data?.reinterpret<kotlinx.cinterop.ByteVar>() ?: return null,
        pointed.len.toInt(),
    )
}

{{ visibility() }}typealias RustBufferByValue = CValue<{{ ci.namespace() }}.cinterop.RustBuffer>
{{ visibility() }}fun RustBufferByValue(
    capacity: Long,
    len: Long,
    data: Pointer?,
): RustBufferByValue {
    return cValue<{{ ci.namespace() }}.cinterop.RustBuffer> {
        this.capacity = capacity
        this.len = len
        this.data = data?.reinterpret()
    }
}
{{ visibility() }}val RustBufferByValue.capacity: Long
    get() = useContents { capacity }
{{ visibility() }}val RustBufferByValue.len: Long
    get() = useContents { len }
{{ visibility() }}val RustBufferByValue.data: Pointer?
    get() = useContents { data }
{{ visibility() }}fun RustBufferByValue.asByteBuffer(): ByteBuffer? {
    {% call kt::check_rust_buffer_length("len") %}
    return ByteBuffer(
        data?.reinterpret<kotlinx.cinterop.ByteVar>() ?: return null,
        len.toInt(),
    )
}

/**
 * The equivalent of the `*mut RustBuffer` type.
 * Required for callbacks taking in an out pointer.
 *
 * Size is the sum of all values in the struct.
 */
internal typealias RustBufferByReference = CPointer<{{ ci.namespace() }}.cinterop.RustBufferByReference>

internal fun RustBufferByReference.setValue(value: RustBufferByValue) {
    pointed.capacity = value.capacity
    pointed.len = value.len
    pointed.data = value.data?.reinterpret()
}
internal fun RustBufferByReference.getValue(): RustBufferByValue
    = pointed.reinterpret<{{ ci.namespace() }}.cinterop.RustBuffer>().readValue()


internal typealias ForeignBytes = CPointer<{{ ci.namespace() }}.cinterop.ForeignBytes>
internal var ForeignBytes.len: Int
    get() = pointed.len
    set(value) { pointed.len = value }
internal var ForeignBytes.data: Pointer?
    get() = pointed.data
    set(value) { pointed.data = value?.reinterpret() }

internal typealias ForeignBytesByValue = CValue<{{ ci.namespace() }}.cinterop.ForeignBytes>
internal val ForeignBytesByValue.len: Int
    get() = useContents { len }
internal val ForeignBytesByValue.data: Pointer?
    get() = useContents { data }
