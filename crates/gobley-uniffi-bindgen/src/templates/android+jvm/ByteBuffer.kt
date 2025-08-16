
@kotlin.jvm.JvmInline
{{ visibility() }}value class ByteBuffer(private val inner: java.nio.ByteBuffer) {
    init {
        inner.order(java.nio.ByteOrder.BIG_ENDIAN)
    }

    {{ visibility() }}fun internal(): java.nio.ByteBuffer = inner

    {{ visibility() }}fun limit(): Int = inner.limit()

    {{ visibility() }}fun position(): Int = inner.position()

    {{ visibility() }}fun hasRemaining(): Boolean = inner.hasRemaining()

    {{ visibility() }}fun get(): Byte = inner.get()

    {{ visibility() }}fun get(bytesToRead: Int): ByteArray = ByteArray(bytesToRead).apply(inner::get)

    {{ visibility() }}fun getShort(): Short = inner.getShort()

    {{ visibility() }}fun getInt(): Int = inner.getInt()

    {{ visibility() }}fun getLong(): Long = inner.getLong()

    {{ visibility() }}fun getFloat(): Float = inner.getFloat()

    {{ visibility() }}fun getDouble(): Double = inner.getDouble()

    {{ visibility() }}fun put(value: Byte) {
        inner.put(value)
    }

    {{ visibility() }}fun put(src: ByteArray) {
        inner.put(src)
    }

    {{ visibility() }}fun putShort(value: Short) {
        inner.putShort(value)
    }

    {{ visibility() }}fun putInt(value: Int) {
        inner.putInt(value)
    }

    {{ visibility() }}fun putLong(value: Long) {
        inner.putLong(value)
    }

    {{ visibility() }}fun putFloat(value: Float) {
        inner.putFloat(value)
    }

    {{ visibility() }}fun putDouble(value: Double) {
        inner.putDouble(value)
    }
}
