{% include "ffi/ObjectCleanerHelper.kt" %}
{{- self.add_import("kotlinx.atomicfu.atomic") }}
{{- self.add_import("kotlinx.atomicfu.AtomicBoolean") }}
{{- self.add_import("kotlin.native.ref.createCleaner") }}

private class NativeCleaner : UniffiCleaner {
    override fun register(resource: Any, disposable: Disposable): UniffiCleaner.Cleanable =
        // Ignore value here. In Kotlin/Native, if a cleaner object is referenced by the resource
        // object, both objects might leak and cleanupAction won't be called.
        //
        // Since this cleanable instance will be referenced by the resource instance, ignoring
        // the resource and making the cleaner only reference the disposable prevents the cleaner
        // from making a reference cycle thus able to destroy the disposable at a proper moment.
        UniffiNativeCleanable(disposable)
}

private class UniffiNativeCleanable(val disposable: Disposable) : UniffiCleaner.Cleanable {
    private val cleanAction = UniffiNativeCleanAction(disposable)

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cleaner = createCleaner(cleanAction, UniffiNativeCleanAction::clean)

    override fun clean() {
        cleanAction.clean()
    }

    private class UniffiNativeCleanAction(private val disposable: Disposable) {
        private val cleaned = atomic(false)
        fun clean() {
            if (cleaned.compareAndSet(false, true)) {
                disposable.destroy()
            }
        }
    }
}

private fun UniffiCleaner.Companion.create(): UniffiCleaner =
    NativeCleaner()
