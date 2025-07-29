
@kotlin.jvm.JvmInline
value class ByteBuffer(private val inner: java.nio.ByteBuffer) {
    init {
        inner.order(java.nio.ByteOrder.BIG_ENDIAN)
    }

    fun internal() = inner

    fun limit() = inner.limit()

    fun position() = inner.position()

    fun hasRemaining() = inner.hasRemaining()

    fun get() = inner.get()

    fun get(bytesToRead: Int): ByteArray = ByteArray(bytesToRead).apply(inner::get)

    fun getShort() = inner.getShort()

    fun getInt() = inner.getInt()

    fun getLong() = inner.getLong()

    fun getFloat() = inner.getFloat()

    fun getDouble() = inner.getDouble()



    fun put(value: Byte) {
        inner.put(value)
    }

    fun put(src: ByteArray) {
        inner.put(src)
    }

    fun putShort(value: Short) {
        inner.putShort(value)
    }

    fun putInt(value: Int) {
        inner.putInt(value)
    }

    fun putLong(value: Long) {
        inner.putLong(value)
    }

    fun putFloat(value: Float) {
        inner.putFloat(value)
    }

    fun putDouble(value: Double) {
        inner.putDouble(value)
    }


    fun writeUtf8(value: String) {
        Charsets.UTF_8.newEncoder().run {
            onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
            encode(java.nio.CharBuffer.wrap(value), inner, false)
        }
    }
}
