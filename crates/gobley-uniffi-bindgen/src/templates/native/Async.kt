{% include "ffi/Async.kt" %}

val uniffiRustFutureContinuationCallbackCallback = staticCFunction { handle: Long, pollResult: Byte ->
    uniffiContinuationHandleMap.remove(handle).resume(pollResult)
}

{%- if ci.has_async_callback_interface_definition() %}

val uniffiForeignFutureFreeImpl = staticCFunction { handle: Long ->
    val job = uniffiForeignFutureHandleMap.remove(handle)
    if (!job.isCompleted) {
        job.cancel()
    }
}

{%- endif %}