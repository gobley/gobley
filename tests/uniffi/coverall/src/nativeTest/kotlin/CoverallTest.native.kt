/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import platform.posix.usleep
import kotlin.coroutines.CoroutineContext
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

@OptIn(NativeRuntimeApi::class)
actual fun runGC() {
    GC.collect()
}

actual fun blockingDelay(delay: Long) {
    if (delay < 0) return
    usleep((delay * 1000).toUInt())
}

actual val gcSupported = true

actual val uniffiSupported = true

@OptIn(DelicateCoroutinesApi::class)
actual val multithreadedCoroutineContext: CoroutineContext =
    newFixedThreadPoolContext(3, "CoverallTest.threadSafe Thread Pool")