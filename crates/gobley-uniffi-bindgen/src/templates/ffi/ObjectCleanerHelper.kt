
// The cleaner interface for Object finalization code to run.
// This is the entry point to any implementation that we're using.
//
// The cleaner registers disposables and returns cleanables, so now we are
// defining a `UniffiCleaner` with a `UniffiClenaer.Cleanable` to abstract the
// different implementations available at compile time.
interface UniffiCleaner {
    interface Cleanable {
        fun clean()
    }

    fun register(resource: Any, disposable: Disposable): UniffiCleaner.Cleanable

    companion object
}
