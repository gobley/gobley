{% include "ffi/Async.kt" %}

object uniffiRustFutureContinuationCallbackCallback: UniffiRustFutureContinuationCallback {
    override fun callback(data: Long, pollResult: Byte) {
        uniffiContinuationHandleMap.remove(data).resume(pollResult)
    }
}

{%- if ci.has_async_callback_interface_definition() %}

object uniffiForeignFutureFreeImpl: UniffiForeignFutureFree {
    override fun callback(handle: Long) {
        val job = uniffiForeignFutureHandleMap.remove(handle)
        if (!job.isCompleted) {
            job.cancel()
        }
    }
}

{%- endif %}