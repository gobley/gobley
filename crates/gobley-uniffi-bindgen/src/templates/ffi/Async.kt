
internal const val UNIFFI_RUST_FUTURE_POLL_READY = 0.toByte()
internal const val UNIFFI_RUST_FUTURE_POLL_MAYBE_READY = 1.toByte()

internal val uniffiContinuationHandleMap = UniffiHandleMap<CancellableContinuation<Byte>>()

// FFI type for Rust future continuations
internal suspend fun<T, F, E: kotlin.Exception> uniffiRustCallAsync(
    rustFuture: Long,
    pollFunc: (Long, UniffiRustFutureContinuationCallback, Long) -> Unit,
    completeFunc: (Long, UniffiRustCallStatus) -> F,
    freeFunc: (Long) -> Unit,
    cancelFunc: (Long) -> Unit,
    liftFunc: (F) -> T,
    errorHandler: UniffiRustCallStatusErrorHandler<E>
): T {
    return withContext(Dispatchers.IO) {
        try {
            do {
                val pollResult = suspendCancellableCoroutine<Byte> { continuation ->
                    val handle = uniffiContinuationHandleMap.insert(continuation)
                    continuation.invokeOnCancellation {
                        cancelFunc(rustFuture)
                    }
                    pollFunc(
                        rustFuture,
                        uniffiRustFutureContinuationCallbackCallback,
                        handle
                    )
                }
            } while (pollResult != UNIFFI_RUST_FUTURE_POLL_READY);

            return@withContext liftFunc(
                uniffiRustCallWithError(errorHandler) { status -> completeFunc(rustFuture, status) }
            )
        } finally {
            freeFunc(rustFuture)
        }
    }
}

{%- if ci.has_async_callback_interface_definition() %}
internal inline fun<T> uniffiTraitInterfaceCallAsync(
    crossinline makeCall: suspend () -> T,
    crossinline handleSuccess: (T) -> Unit,
    crossinline handleError: (UniffiRustCallStatusByValue) -> Unit,
): UniffiForeignFutureUniffiByValue {
    // Using `GlobalScope` is labeled as a "delicate API" and generally discouraged in Kotlin programs, since it breaks structured concurrency.
    // However, our parent task is a Rust future, so we're going to need to break structure concurrency in any case.
    //
    // Uniffi does its best to support structured concurrency across the FFI.
    // If the Rust future is dropped, `uniffiForeignFutureFreeImpl` is called, which will cancel the Kotlin coroutine if it's still running.
    @OptIn(DelicateCoroutinesApi::class)
    val job = GlobalScope.launch {
        try {
            handleSuccess(makeCall())
        } catch(e: kotlin.Exception) {
            val status = UniffiRustCallStatusByValue(
                UNIFFI_CALL_UNEXPECTED_ERROR,
                {{ Type::String.borrow()|lower_fn }}(e.toString()),
            )
            handleError(status)
        }
    }
    val handle = uniffiForeignFutureHandleMap.insert(job)
    return UniffiForeignFutureUniffiByValue(handle, uniffiForeignFutureFreeImpl)
}

internal inline fun<T, reified E: Throwable> uniffiTraitInterfaceCallAsyncWithError(
    crossinline makeCall: suspend () -> T,
    crossinline handleSuccess: (T) -> Unit,
    crossinline handleError: (UniffiRustCallStatusByValue) -> Unit,
    crossinline lowerError: (E) -> RustBufferByValue,
): UniffiForeignFutureUniffiByValue {
    // See uniffiTraitInterfaceCallAsync for details on `DelicateCoroutinesApi`
    @OptIn(DelicateCoroutinesApi::class)
    val job = GlobalScope.launch {
        try {
            handleSuccess(makeCall())
        } catch(e: kotlin.Exception) {
            if (e is E) {
                val status = UniffiRustCallStatusByValue(
                    UNIFFI_CALL_ERROR,
                    lowerError(e),
                )
                handleError(status)
            } else {
                val status = UniffiRustCallStatusByValue(
                    UNIFFI_CALL_UNEXPECTED_ERROR,
                    {{ Type::String.borrow()|lower_fn }}(e.toString()),
                )
                handleError(status)
            }
        }
    }
    val handle = uniffiForeignFutureHandleMap.insert(job)
    return UniffiForeignFutureUniffiByValue(handle, uniffiForeignFutureFreeImpl)
}

internal val uniffiForeignFutureHandleMap = UniffiHandleMap<Job>()

{%- endif %}
