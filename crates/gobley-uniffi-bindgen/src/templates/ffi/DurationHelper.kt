
{{ self.add_import("kotlin.time.Duration.Companion.nanoseconds") }}
{{ self.add_import("kotlin.time.Duration.Companion.seconds") }}

object FfiConverterDuration: FfiConverterRustBuffer<kotlin.time.Duration> {
    override fun read(buf: ByteBuffer): kotlin.time.Duration {
        // Type mismatch (should be u64) but we check for overflow/underflow below
        val secs = buf.getLong()
        // Type mismatch (should be u32) but we check for overflow/underflow below
        val nanos = buf.getInt().toLong()
        if (secs < 0) {
            throw IllegalArgumentException("Duration exceeds minimum or maximum value supported by uniffi")
        }
        if (nanos < 0) {
            throw IllegalArgumentException("Duration nanoseconds exceed minimum or maximum supported by uniffi")
        }
        return secs.seconds + nanos.nanoseconds
    }

    // 8 bytes for seconds, 4 bytes for nanoseconds
    override fun allocationSize(value: kotlin.time.Duration) = 12UL

    override fun write(value: kotlin.time.Duration, buf: ByteBuffer) {
        if (value < 0.nanoseconds) {
            throw IllegalArgumentException("Invalid duration, must be non-negative")
        }
        value.toComponents { seconds, nanoseconds ->
            buf.putLong(seconds)
            buf.putInt(nanoseconds)
        }
    }
}
