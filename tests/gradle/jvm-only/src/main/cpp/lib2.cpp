/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

#ifdef __cplusplus
    #include <cstdint>
    #define EXTERN extern "C"
#else
    #include <stdint.h>
    #define EXTERN extern
#endif

#ifdef _WIN32
    #define EXPORT __declspec(dllexport)
#else
    #define EXPORT __attribute__((visibility("default")))
#endif

EXTERN EXPORT int32_t my_gcd(int32_t lhs, int32_t rhs) {
    if (rhs == 0) return lhs;
    return my_gcd(rhs, lhs % rhs);
}
