/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import futures.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.ranges.beIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldHave
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.time.measureTime

expect fun uniffiForeignFutureHandleMapSize(): Int

class FuturesTest {
    @BeforeTest
    fun fireUpUniffi() = runTest {
        val time = measureTime {
            // init UniFFI to get good measurements after that
            alwaysReady()

            // init kotest as well, as some comparison takes much time on the first invocation
            1U shouldBe 1U
        }

        println("init time: $time")
    }

    private fun assertReturnsImmediately(block: suspend CoroutineScope.() -> Unit) =
        assertMaxTime(20 /* delay enough amount to pass in CI */, block)

    private fun assertApproximateTime(expectedTime: Int, block: suspend CoroutineScope.() -> Unit) =
        runTest {
            val actualTime = measureTime {
                block()
            }.inWholeMilliseconds
            actualTime shouldHave beIn(expectedTime.toLong()..expectedTime.toLong() + 500)
        }

    private fun assertMaxTime(maxTime: Int, block: suspend CoroutineScope.() -> Unit) = runTest {
        val actualTime = measureTime {
            block()
        }.inWholeMilliseconds
        actualTime shouldBeLessThanOrEqualTo maxTime.toLong()
    }

    // TODO think about some real async/suspend tests
    // This could be done via sync callbacks to Kotlin
    // at key points in the async protocol.
    //
    // For example:
    //
    //    trait SleepCallback {
    //        fn before_sleep(&self);
    //        fn after_sleep(&self);
    //    }
    //
    //    async func sleep(ms: u16, sleep_callback: Box<dyn SleepCallback>) {
    //        // ...
    //    }

    // This test is to ensure that coroutines still work after multiple coroutine calls.
    // Coroutines may halt or crash after several coroutine invocations due to wrong
    // atomic operations in the generated binding. Please see #24 for more details.
    @Test
    fun testSleepWithRepeat() = runTest {
        repeat(65) {
            sleep(20u) shouldBe true
        }
    }

    @Test
    fun testAlwaysReady() = assertReturnsImmediately {
        alwaysReady() shouldBe true
    }

    @Test
    fun testVoid() = assertReturnsImmediately {
        void() shouldBe Unit
    }

    @Test
    fun testSleep() = assertApproximateTime(200) {
        sleep(200u) shouldBe true
    }

    @Test
    fun testSequentialFutures() = assertApproximateTime(300) {
        sayAfter(100u, "Alice") shouldBe "Hello, Alice!"
        sayAfter(200u, "Bob") shouldBe "Hello, Bob!"
    }

    @Test
    fun testConcurrentFutures() = assertApproximateTime(200) {
        val resultAlice = async { sayAfter(100u, "Alice") }
        val resultBob = async { sayAfter(200u, "Bob") }
        resultAlice.await() shouldBe "Hello, Alice!"
        resultBob.await() shouldBe "Hello, Bob!"
    }

    @Test
    fun testAsyncMethodsFromObject() = assertApproximateTime(200) {
        val megaphone = newMegaphone()
        megaphone.sayAfter(200u, "Alice") shouldBe "HELLO, ALICE!"
    }

    @Test
    fun testAsyncMethodsFromTopLevel() = assertApproximateTime(200) {
        val megaphone = newMegaphone()
        sayAfterWithMegaphone(megaphone, 200u, "Alice") shouldBe "HELLO, ALICE!"
    }

    @Test
    fun testAsyncConstructors() = runTest {
        val megaphone = Megaphone.secondary()
        megaphone.sayAfter(1U, "hi") shouldBe "HELLO, HI!"
    }

    @Test
    fun testAsyncMethodReturningOptional() = runTest {
        asyncMaybeNewMegaphone(true) shouldNotBe null
        asyncMaybeNewMegaphone(false) shouldBe null
    }

    @Test
    fun testAsyncMethodsInTraits() = assertApproximateTime(200) {
        val traits = getSayAfterTraits()
        val result1 = traits[0].sayAfter(100U, "Alice")
        val result2 = traits[1].sayAfter(100U, "Bob")

        result1 shouldBe "Hello, Alice!"
        result2 shouldBe "Hello, Bob!"
    }

    @Test
    fun testAsyncMethodsInUdlDefinedTraits() = assertApproximateTime(200) {
        val traits = getSayAfterUdlTraits()
        val result1 = traits[0].sayAfter(100U, "Alice")
        val result2 = traits[1].sayAfter(100U, "Bob")

        result1 shouldBe "Hello, Alice!"
        result2 shouldBe "Hello, Bob!"
    }

    // Test foreign implemented async trait methods
    class KotlinAsyncParser : AsyncParser {
        var completedDelays: Int = 0

        override suspend fun asString(delayMs: Int, value: Int): String {
            delay(delayMs.toLong())
            return value.toString()
        }

        override suspend fun tryFromString(delayMs: Int, value: String): Int {
            delay(delayMs.toLong())
            if (value == "force-unexpected-exception") {
                throw RuntimeException("UnexpectedException")
            }
            try {
                return value.toInt()
            } catch (e: NumberFormatException) {
                throw ParserException.NotAnInt()
            }
        }

        override suspend fun delay(delayMs: Int) {
            delay(delayMs.toLong())
            completedDelays += 1
        }

        override suspend fun tryDelay(delayMs: String) {
            val parsed = try {
                delayMs.toLong()
            } catch (e: NumberFormatException) {
                throw ParserException.NotAnInt()
            }
            delay(parsed)
            completedDelays += 1
        }
    }

    @Test
    fun testForeignImplementedAsyncTraitMethods() = runTest {
        val traitObj = KotlinAsyncParser()
        asStringUsingTrait(traitObj, 1, 42) shouldBe "42"
        tryFromStringUsingTrait(traitObj, 1, "42") shouldBe 42
        shouldThrow<ParserException.NotAnInt> {
            tryFromStringUsingTrait(traitObj, 1, "fourty-two")
        }
        shouldThrow<ParserException.UnexpectedException> {
            tryFromStringUsingTrait(traitObj, 1, "force-unexpected-exception")
        }
        delayUsingTrait(traitObj, 1)
        shouldThrow<ParserException.NotAnInt> {
            tryDelayUsingTrait(traitObj, "one")
        }
        val completedDelaysBefore = traitObj.completedDelays
        cancelDelayUsingTrait(traitObj, 3000 /* delay enough amount to pass in CI */)
        // sleep long enough so that the `delay()` call would finish if it wasn't cancelled.
        delay(100)
        // If the task was cancelled, then completedDelays won't have increased
        traitObj.completedDelays shouldBe completedDelaysBefore

        // Test that all handles were cleaned up
        uniffiForeignFutureHandleMapSize() shouldBe 0
    }

    @Test
    fun testWithTokioRuntime() = assertApproximateTime(200) {
        sayAfterWithTokio(200u, "Alice") shouldBe "Hello, Alice (with Tokio)!"
    }

    @Test
    fun testFallibleFunctions() {
        assertMaxTime(100) {
            fallibleMe(false)
        }
        assertMaxTime(100) {
            assertFails {
                fallibleMe(true)
            }
        }
    }

    @Test
    fun testFallibleMethods() {
        val megaphone = newMegaphone()
        assertMaxTime(100) {
            megaphone.fallibleMe(false)
        }
        assertMaxTime(100) {
            assertFails {
                megaphone.fallibleMe(true)
            }
        }
    }

    @Test
    fun testFallibleStruct() = runTest {
        fallibleStruct(false) shouldNotBe null
        assertFails {
            fallibleStruct(true)
        }
    }

    @Test
    fun testRecord() = assertMaxTime(100) {
        newMyRecord("foo", 42u) shouldBe MyRecord("foo", 42u)
    }

    @Test
    fun testBrokenSleep() = assertApproximateTime(500) {
        brokenSleep(100U, 0U) // calls the waker twice immediately
        sleep(100U) // wait for possible failure

        brokenSleep(100U, 100U) // calls the waker a second time after 1s
        sleep(200U) // wait for possible failure
    }

    @Test
    fun testFutureWithLockAndCancelled() = assertMaxTime(100) {
        val job = launch {
            useSharedResource(SharedResourceOptions(releaseAfterMs = 5000U, timeoutMs = 100U))
        }

        // Wait some time to ensure the task has locked the shared resource
        delay(50)
        // Cancel the job before the shared resource has been released.
        job.cancel()

        // Try accessing the shared resource again.  The initial task should release the shared resource
        // before the timeout expires.
        useSharedResource(SharedResourceOptions(releaseAfterMs = 0U, timeoutMs = 1000U))
    }

    @Test
    fun testFutureWithLockButNotCancelled() = assertApproximateTime(100) {
        useSharedResource(SharedResourceOptions(releaseAfterMs = 100U, timeoutMs = 1000U))
        useSharedResource(SharedResourceOptions(releaseAfterMs = 0U, timeoutMs = 1000U))
    }
}
