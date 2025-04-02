/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi.utils;

import org.gradle.api.Transformer;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import gobley.gradle.InternalGobleyGradleApi;

// The Kotlin equivalent of the following code compiles well, but Android Studio shows an error.
@InternalGobleyGradleApi
public class TakeIfTransformer<T> implements Transformer<T, T> {
    @Nonnull
    private final Predicate<T> predicate;

    public TakeIfTransformer(@Nonnull Predicate<T> predicate) {
        this.predicate = predicate;
    }

    @Override
    public T transform(@Nonnull T in) {
        if (predicate.test(in)) {
            return in;
        }
        return null;
    }
}