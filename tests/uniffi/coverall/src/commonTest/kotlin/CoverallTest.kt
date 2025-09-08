/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import coverall.Color
import coverall.ComplexException
import coverall.CoverallException
import coverall.Coveralls
import coverall.DictWithDefaults
import coverall.EmptyStruct
import coverall.FalliblePatch
import coverall.Getters
import coverall.InternalException
import coverall.NoPointer
import coverall.Node
import coverall.NodeTrait
import coverall.OtherError
import coverall.Patch
import coverall.Repair
import coverall.RootException
import coverall.ThreadsafeCounter
import coverall.ancestorNames
import coverall.createNoneDict
import coverall.createSomeDict
import coverall.getComplexError
import coverall.getErrorDict
import coverall.getNumAlive
import coverall.getRootError
import coverall.getStringUtilTraits
import coverall.getTraits
import coverall.makeRustGetters
import coverall.testGetters
import coverall.testRoundTripThroughForeign
import coverall.testRoundTripThroughRust
import coverall.throwRootError
import coverall.use
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.doubles.ToleranceMatcher
import io.kotest.matchers.floats.FloatToleranceMatcher
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.test.BeforeTest
import kotlin.test.Test

expect fun runGC()
expect fun blockingDelay(delay: Long)
expect val gcSupported: Boolean
expect val uniffiSupported: Boolean
expect val multithreadedCoroutineContext: CoroutineContext

class CoverallTest {
    private val gcDelay: Long = 100

    private fun runGCWithDelay() {
        runGC()
        blockingDelay(gcDelay)
    }

    // Ensures getNumAlive() always returns deterministic value
    private val coverallLock = ReentrantLock()

    private class CoverallLockScope(val initialNumAlive: ULong) {
        fun getNumAlive(): ULong {
            return max(coverall.getNumAlive(), initialNumAlive) - initialNumAlive
        }
    }

    private inline fun <T> withCoverallLock(block: CoverallLockScope.() -> T) {
        coverallLock.withLock {
            CoverallLockScope(
                when {
                    uniffiSupported && gcSupported -> getNumAlive()
                    else -> 0UL
                }
            ).block()
        }
    }

    @BeforeTest
    fun gcBetweenTests() {
        runGCWithDelay()
    }

    @Test
    fun dict() = withCoverallLock {
        // floats should be "close enough".
        infix fun Float.almostEquals(other: Float) =
            shouldBe(FloatToleranceMatcher(other, 0.000001F))

        infix fun Double.almostEquals(other: Double) =
            shouldBe(ToleranceMatcher(other, 0.000001))

        if (!uniffiSupported) return

        createSomeDict().use { d ->
            d.text shouldBe "text"
            d.maybeText shouldBe "maybe_text"
            d.someBytes shouldBe "some_bytes".encodeToByteArray()
            d.maybeSomeBytes shouldBe "maybe_some_bytes".encodeToByteArray()
            d.aBool shouldBe true
            d.maybeABool shouldBe false
            d.unsigned8 shouldBe 1.toUByte()
            d.maybeUnsigned8 shouldBe 2.toUByte()
            d.unsigned16 shouldBe 3.toUShort()
            d.maybeUnsigned16 shouldBe 4.toUShort()
            d.unsigned64 shouldBe 18446744073709551615UL
            d.maybeUnsigned64 shouldBe 0UL
            d.signed8 shouldBe 8.toByte()
            d.maybeSigned8 shouldBe 0.toByte()
            d.signed64 shouldBe 9223372036854775807L
            d.maybeSigned64 shouldBe 0L

            d.float32 almostEquals 1.2345F
            d.maybeFloat32!! almostEquals (22.0F / 7.0F)
            d.float64 almostEquals 0.0
            d.maybeFloat64!! almostEquals 1.0

            d.coveralls!!.getName() shouldBe "some_dict"

            d.coverallsList[0]!!.getName() shouldBe "some_dict_1"
            d.coverallsList[1] shouldBe null
            d.coverallsList[2]!!.getName() shouldBe "some_dict_2"

            d.coverallsMap["some_dict_3"]!!.getName() shouldBe "some_dict_3"
            d.coverallsMap["none"] shouldBe null
            d.coverallsMap["some_dict_4"]!!.getName() shouldBe "some_dict_4"

            getNumAlive() shouldBe 5UL
        }

        getNumAlive() shouldBe 0UL

        createNoneDict().use { d ->
            d.text shouldBe "text"
            d.maybeText shouldBe null
            d.someBytes shouldBe "some_bytes".encodeToByteArray()
            d.maybeSomeBytes shouldBe null
            d.aBool shouldBe true
            d.maybeABool shouldBe null
            d.unsigned8 shouldBe 1.toUByte()
            d.maybeUnsigned8 shouldBe null
            d.unsigned16 shouldBe 3.toUShort()
            d.maybeUnsigned16 shouldBe null
            d.unsigned64 shouldBe 18446744073709551615UL
            d.maybeUnsigned64 shouldBe null
            d.signed8 shouldBe 8.toByte()
            d.maybeSigned8 shouldBe null
            d.signed64 shouldBe 9223372036854775807L
            d.maybeSigned64 shouldBe null

            d.float32 almostEquals 1.2345F
            d.maybeFloat32 shouldBe null
            d.float64 almostEquals 0.0
            d.maybeFloat64 shouldBe null

            d.coveralls shouldBe null

            getNumAlive() shouldBe 0UL
        }

        getNumAlive() shouldBe 0UL
    }

    @Test
    fun coverallTests() = withCoverallLock {
        if (!uniffiSupported) return

        Coveralls("test_arcs").use { coveralls ->
            getNumAlive() shouldBe 1UL
            // One ref held by the foreign-language code, one created for this method call.
            coveralls.strongCount() shouldBe 2UL
            coveralls.getOther() shouldBe null
            coveralls.takeOther(coveralls)
            // Should now be a new strong ref, held by the object's reference to itself.
            coveralls.strongCount() shouldBe 3UL
            // But the same number of instances.
            getNumAlive() shouldBe 1UL
            // Careful, this makes a new Kotlin object which must be separately destroyed.
            coveralls.getOther()!!.use { other ->
                // It's the same Rust object.
                other.getName() shouldBe "test_arcs"
            }
            shouldThrow<CoverallException.TooManyHoles> {
                coveralls.takeOtherFallible()
            }
            shouldThrow<InternalException> {
                coveralls.takeOtherPanic("expected panic: with an arc!")
            }
            shouldThrow<InternalException> {
                coveralls.falliblePanic("Expected panic in a fallible function!")
            }
            coveralls.takeOther(null)

            runGCWithDelay()

            coveralls.strongCount() shouldBe 2UL
        }

        runGCWithDelay()

        getNumAlive() shouldBe 0UL

        Coveralls("test_return_objects").use { coveralls ->
            getNumAlive() shouldBe 1UL
            coveralls.strongCount() shouldBe 2UL
            coveralls.cloneMe().use { c2 ->
                c2.getName() shouldBe coveralls.getName()
                getNumAlive() shouldBe 2UL
                c2.strongCount() shouldBe 2UL

                coveralls.takeOther(c2)
                // same number alive but `c2` has an additional ref count.
                getNumAlive() shouldBe 2UL
                coveralls.strongCount() shouldBe 2UL
                c2.strongCount() shouldBe 3UL
            }
            // Here we've dropped Kotlin's reference to `c2`, but the rust struct will not
            // be dropped as coveralls hold an `Arc<>` to it.
            getNumAlive() shouldBe 2UL
        }

        runGCWithDelay()

        // Destroying `coveralls` will kill both.
        getNumAlive() shouldBe 0UL

        Coveralls("test_simple_errors").use { coveralls ->
            shouldThrow<CoverallException.TooManyHoles> {
                coveralls.maybeThrow(true)
            }.also { e ->
                e.message shouldBe "The coverall has too many holes"
            }

            shouldThrow<CoverallException.TooManyHoles> {
                coveralls.maybeThrowInto(true)
            }

            shouldThrow<InternalException> {
                coveralls.panic("oops")
            }.also { e ->
                e.message shouldBe "oops"
            }
        }

        Coveralls("test_complex_errors").use { coveralls ->
            coveralls.maybeThrowComplex(0) shouldBe true

            shouldThrow<ComplexException.OsException> {
                coveralls.maybeThrowComplex(1)
            }.also { e ->
                e.code shouldBe 10.toShort()
                e.extendedCode shouldBe 20.toShort()
                e.toString() shouldBeIn setOf(
                    "coverall.ComplexException.OsException: code=10, extendedCode=20",
                    "coverall.ComplexException\$OsException: code=10, extendedCode=20"
                )
            }

            shouldThrow<ComplexException.PermissionDenied> {
                coveralls.maybeThrowComplex(2)
            }.also { e ->
                e.reason shouldBe "Forbidden"
                e.toString() shouldBeIn setOf(
                    "coverall.ComplexException.PermissionDenied: reason=Forbidden",
                    "coverall.ComplexException\$PermissionDenied: reason=Forbidden"
                )
            }

            shouldThrow<ComplexException.UnknownException> {
                coveralls.maybeThrowComplex(3)
            }.also { e ->
                e.toString() shouldBeIn setOf(
                    "coverall.ComplexException.UnknownException: ",
                    "coverall.ComplexException\$UnknownException: "
                )
            }

            shouldThrow<InternalException> {
                coveralls.maybeThrowComplex(4)
            }
        }

        Coveralls("test_error_values").use { _ ->
            shouldThrow<RootException.Complex> {
                throwRootError()
            }.also { e ->
                e.error.shouldBeInstanceOf<ComplexException.OsException>()
            }
            val e = getRootError()
            if (e is RootException.Other) {
                e.error shouldBe OtherError.UNEXPECTED
            }
            val ce = getComplexError(null)
            ce.shouldBeInstanceOf<ComplexException.PermissionDenied>()
            getErrorDict(null).complexError shouldBe null
        }

        Coveralls("test_interfaces_in_dicts").use { coveralls ->
            coveralls.addPatch(Patch(Color.RED))
            coveralls.addRepair(
                Repair(`when` = Clock.System.now(), patch = Patch(Color.BLUE))
            )
            coveralls.getRepairs().size shouldBe 2
        }

        Coveralls("test_regressions").use { coveralls ->
            coveralls.getStatus("success") shouldBe "status: success"
        }

        Coveralls("test_empty_records").use { coveralls ->
            coveralls.setAndGetEmptyStruct(EmptyStruct) shouldBe EmptyStruct
            // Upstream generates EmptyStruct as a normal class, but we generate as an object.
            // EmptyStruct shouldNotBeSameInstanceAs EmptyStruct
        }

        runGCWithDelay()
    }

    @Test
    fun gc() = withCoverallLock {
        if (!uniffiSupported || !gcSupported) return

        // The GC test; we should have 1000 alive by the end of the loop.
        //
        // Later on, nearer the end of the script, we'll test again, when the cleaner
        // has had time to clean up.
        //
        // The number alive then should be zero.
        //
        // First we make 1000 objects, and wait for the rest of the test to run. If it has, then
        // the garbage objects have been collected, and the Rust counter parts have been dropped.
        repeat(1000) {
            val c = Coveralls("GC testing $it")
            @Suppress("UNUSED_EXPRESSION")
            c
        }

        // This is from an earlier GC test; ealier, we made 1000 new objects.
        // By now, the GC has had time to clean up, and now we should see 0 alive.
        // (hah! Wishful-thinking there ;)
        // * We need to System.gc() and/or sleep.
        // * There's one stray thing alive, not sure what that is, but it's unrelated.
        for (i in 1..100) {
            if (getNumAlive() <= 1UL) {
                break
            }
            runGCWithDelay()
        }

        getNumAlive() shouldBeLessThanOrEqualTo 1UL

        runGCWithDelay()
    }

    class KotlinGetters : Getters {
        override fun getBool(v: Boolean, arg2: Boolean): Boolean {
            return v != arg2
        }

        override fun getString(v: String, arg2: Boolean): String {
            return if (v == "too-many-holes") {
                throw CoverallException.TooManyHoles("too many holes")
            } else if (v == "unexpected-error") {
                throw RuntimeException("unexpected error")
            } else if (arg2) {
                v.uppercase()
            } else {
                v
            }
        }

        override fun getOption(v: String, arg2: Boolean): String? {
            if (v == "os-error") {
                throw ComplexException.OsException(100, 200)
            } else if (v == "unknown-error") {
                throw ComplexException.UnknownException()
            } else if (arg2) {
                if (!v.isEmpty()) {
                    return v.uppercase()
                } else {
                    return null
                }
            } else {
                return v
            }
        }

        override fun getList(v: List<Int>, arg2: Boolean): List<Int> {
            if (arg2) {
                return v
            } else {
                return listOf()
            }
        }

        override fun getNothing(v: String) = Unit

        override fun roundTripObject(coveralls: Coveralls): Coveralls {
            return coveralls
        }
    }

    @Test
    fun getters() {
        if (uniffiSupported) {
            // Test traits implemented in Rust
            makeRustGetters().let { rustGetters ->
                testGetters(rustGetters)
                testGettersFromKotlin(rustGetters)
            }
        }

        // Test traits implemented in Kotlin
        KotlinGetters().let { kotlinGetters ->
            if (uniffiSupported) {
                testGetters(kotlinGetters)
            }
            testGettersFromKotlin(kotlinGetters)
        }
    }

    @Suppress("BooleanLiteralArgument")
    private fun testGettersFromKotlin(getters: Getters) {
        getters.getBool(true, true) shouldBe false
        getters.getBool(true, false) shouldBe true
        getters.getBool(false, true) shouldBe true
        getters.getBool(false, false) shouldBe false

        getters.getString("hello", false) shouldBe "hello"
        getters.getString("hello", true) shouldBe "HELLO"

        getters.getOption("hello", true) shouldBe "HELLO"
        getters.getOption("hello", false) shouldBe "hello"
        getters.getOption("", true) shouldBe null

        getters.getList(listOf(1, 2, 3), true) shouldBe listOf(1, 2, 3)
        getters.getList(listOf(1, 2, 3), false) shouldBe listOf<Int>()

        getters.getNothing("hello") shouldBe Unit

        shouldThrow<CoverallException.TooManyHoles> {
            getters.getString("too-many-holes", true)
        }

        shouldThrow<ComplexException.OsException> {
            getters.getOption("os-error", true)
        }.also { e ->
            e.code.toInt() shouldBe 100
            e.extendedCode.toInt() shouldBe 200
        }

        shouldThrow<ComplexException.UnknownException> {
            getters.getOption("unknown-error", true)
        }

        shouldThrowAny { getters.getString("unexpected-error", true) }
    }

    class KotlinNode() : NodeTrait {
        var currentParent: NodeTrait? = null

        override fun name() = "node-kt"

        override fun setParent(parent: NodeTrait?) {
            currentParent = parent
        }

        override fun getParent() = currentParent

        override fun strongCount(): ULong {
            return 0.toULong() // TODO
        }
    }

    @Test
    fun nodeTrait() {
        if (!uniffiSupported) return

        // Test NodeTrait
        getTraits().let { traits ->
            traits[0].name() shouldBe "node-1"
            // Note: strong counts are 1 more than you might expect, because the strongCount() method
            // holds a strong ref.
            traits[0].strongCount() shouldBe 2UL

            traits[1].name() shouldBe "node-2"
            traits[1].strongCount() shouldBe 2UL

            // Note: this doesn't increase the Rust strong count, since we wrap the Rust impl with a
            // Swift impl before passing it to `setParent()`
            traits[0].setParent(traits[1])
            ancestorNames(traits[0]) shouldBe listOf("node-2")
            ancestorNames(traits[1]).isEmpty() shouldBe true
            traits[1].strongCount() shouldBe 2UL
            traits[0].getParent()!!.name() shouldBe "node-2"

            val ktNode = KotlinNode()
            traits[1].setParent(ktNode)
            ancestorNames(traits[0]) shouldBe listOf("node-2", "node-kt")
            ancestorNames(traits[1]) shouldBe listOf("node-kt")
            ancestorNames(ktNode) shouldBe listOf<String>()

            traits[1].setParent(null)
            ktNode.setParent(traits[0])
            ancestorNames(ktNode) shouldBe listOf("node-1", "node-2")
            ancestorNames(traits[0]) shouldBe listOf("node-2")
            ancestorNames(traits[1]) shouldBe listOf<String>()

            // Unset everything and check that we don't get a memory error
            ktNode.setParent(null)
            traits[0].setParent(null)

            // FIXME: We should be calling `NodeTraitImpl.close()` to release the Rust pointer, however that's
            // not possible through the `NodeTrait` interface (see #1787).
        }

        Node("node").let { n ->
            n.describeParent() shouldBe "Some(Node { name: Some(\"via node\"), parent: Mutex { data: None, poisoned: false, .. } })"
            // NOTE same re-wrap problem described in the Python tests.
            n.setParent(n.getParent())
            // Expect: as above
            // Get: `Some(UniFFICallbackHandlerNodeTrait { handle: 18 })`
            // assert(n.describeParent() == "Some(Node { name: Some(\"via node\"), parent: Mutex { data: None, poisoned: false, .. } })")
            n.setParent(Node("parent"))
        }

        makeRustGetters().let { rustGetters ->
            // Check that these don't cause use-after-free bugs
            testRoundTripThroughRust(rustGetters)

            testRoundTripThroughForeign(KotlinGetters())
        }
    }

    @Test
    fun stringUtil() {
        if (!uniffiSupported) return

        getStringUtilTraits().let { traits ->
            traits[0].concat("cow", "boy") shouldBe "cowboy"
            traits[1].concat("cow", "boy") shouldBe "cowboy"
        }
    }

    // This tests that the UniFFI-generated scaffolding doesn't introduce any unexpected locking.
    // We have one thread busy-wait for a some period of time, while a second thread repeatedly
    // increments the counter and then checks if the object is still busy. The second thread should
    // not be blocked on the first, and should reliably observe the first thread being busy.
    // If it does not, that suggests UniFFI is accidentally serializing the two threads on access
    // to the shared counter object.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun threadSafe() = runTest {
        if (!uniffiSupported) return@runTest

        ThreadsafeCounter().use { counter ->
            val coroutineScope = CoroutineScope(multithreadedCoroutineContext)
            try {
                val busyWaiting = coroutineScope.launch {
                    // 1000 ms should be long enough for the other thread to easily finish
                    // its loop, but not so long as to annoy the user with a slow test.
                    counter.busyWait(1000)
                }
                val incrementing = coroutineScope.async {
                    var count = 0
                    for (n in 1..100) {
                        // We expect most iterations of this loop to run concurrently
                        // with the busy-waiting thread.
                        count = counter.incrementIfBusy()
                    }
                    count
                }

                busyWaiting.join()
                val count = incrementing.await()
                count shouldBeGreaterThan 0
            } finally {
                coroutineScope.cancel()
            }
        }
    }

    @Test
    fun noRustCall() {
        // This does not call Rust code.
        var d = DictWithDefaults()
        d.name shouldBe "default-value"
        d.category shouldBe null
        d.integer shouldBe 31UL
        d.itemList should { it.isEmpty() }
        d.itemMap should { it.isEmpty() }

        d = DictWithDefaults(name = "this", category = "that", integer = 42UL)
        d.name shouldBe "this"
        d.category shouldBe "that"
        d.integer shouldBe 42UL
    }

    @Test
    fun bytes() = withCoverallLock {
        if (!uniffiSupported) return

        Coveralls("test_bytes").use { coveralls ->
            coveralls.reverse("123".encodeToByteArray()).decodeToString() shouldBe "321"
        }
        runGCWithDelay()
    }

    class FakePatch(private val color: Color) : Patch(NoPointer) {
        override fun getColor(): Color = color
    }

    class FakeCoveralls(private val name: String) : Coveralls(NoPointer) {
        private val repairs = mutableListOf<Repair>()

        override fun addPatch(patch: Patch) {
            repairs += Repair(Clock.System.now(), patch)
        }

        override fun getRepairs(): List<Repair> {
            return repairs
        }
    }

    @Test
    fun fakesUsingOpenClasses() = withCoverallLock {
        FakeCoveralls("using_fakes").use { coveralls ->
            val patch = FakePatch(Color.RED)
            coveralls.addPatch(patch)
            coveralls.getRepairs().isEmpty() shouldBe false
        }

        FakeCoveralls("using_fakes_and_calling_methods_without_override_crashes").use { coveralls ->
            shouldThrowAny { coveralls.cloneMe() }
        }

        FakeCoveralls("using_fallible_constructors").use { _ ->
            shouldThrowAny { FalliblePatch() }
            shouldThrowAny { FalliblePatch.secondary() }
        }

        if (!uniffiSupported) return

        Coveralls("using_fakes_with_real_objects_crashes").use { coveralls ->
            val patch = FakePatch(Color.RED)
            shouldThrowAny { coveralls.addPatch(patch) }
        }

        runGCWithDelay()
    }
}