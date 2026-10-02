
// Initial value and increment amount for handles.
// These ensure that Kotlin-generated handles always have the lowest bit set, which
// distinguishes them from Rust-generated handles (lowest bit clear).
private const val UNIFFI_HANDLEMAP_INITIAL = 1L
private const val UNIFFI_HANDLEMAP_DELTA = 2L

internal class UniffiHandleMap<T: Any> {
    private val mapLock = kotlinx.atomicfu.locks.ReentrantLock()
    private val map = HashMap<Long, T>()

    private val counter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(UNIFFI_HANDLEMAP_INITIAL)

    internal val size: Int
        get() = map.size

    // Insert a new object into the handle map and get a handle for it
    internal fun insert(obj: T): Long {
        val handle = counter.getAndAdd(UNIFFI_HANDLEMAP_DELTA)
        syncAccess { map.put(handle, obj) }
        return handle
    }

    // Clone a handle, creating a new one that refers to the same object
    internal fun clone(handle: Long): Long {
        val obj = syncAccess { map.get(handle) } ?: throw InternalException("UniffiHandleMap.clone: Invalid handle")
        return insert(obj)
    }

    // Get an object from the handle map
    internal fun get(handle: Long): T {
        return syncAccess { map.get(handle) } ?: throw InternalException("UniffiHandleMap.get: Invalid handle")
    }

    // Remove an entry from the handlemap and get the Kotlin object back
    internal fun remove(handle: Long): T {
        return syncAccess { map.remove(handle) } ?: throw InternalException("UniffiHandleMap.remove: Invalid handle")
    }

    internal fun <T> syncAccess(block: () -> T): T {
        mapLock.lock()
        try {
            return block()
        } finally {
            mapLock.unlock()
        }
    }
}
