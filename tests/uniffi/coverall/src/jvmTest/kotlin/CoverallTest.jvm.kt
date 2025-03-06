/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlin.coroutines.CoroutineContext

actual fun runGC() {
    System.gc()
}

actual fun blockingDelay(delay: Long) {
    Thread.sleep(delay)
}

actual val gcSupported = true

actual val uniffiSupported = true

@OptIn(DelicateCoroutinesApi::class)
actual val multithreadedCoroutineContext: CoroutineContext =
    newFixedThreadPoolContext(3, "CoverallTest.threadSafe Thread Pool")