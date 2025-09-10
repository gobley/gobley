/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

#include <cstdint>

int32_t pow(int32_t lhs, int32_t rhs) {
    if (rhs < 0) return 0;
    if (rhs == 0) return 1;
    int32_t result = pow(lhs, rhs / 2);
    result *= result;
    if (rhs % 2 != 0) result *= lhs;
    return result;
}