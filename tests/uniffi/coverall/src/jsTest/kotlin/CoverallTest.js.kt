/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

actual fun runGC() = Unit

actual fun blockingDelay(delay: Long) = Unit

actual val gcSupported = false

actual val uniffiSupported = false

actual val multithreadedCoroutineContext: CoroutineContext = Dispatchers.Default