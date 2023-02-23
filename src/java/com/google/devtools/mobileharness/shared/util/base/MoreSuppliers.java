/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.shared.util.base;

import com.google.common.base.Throwables;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** More useful suppliers. */
public class MoreSuppliers {

  /**
   * Returns a supplier which caches the instance retrieved during the first call to {@code get()}
   * and returns that value on subsequent calls to {@code get()}.
   *
   * <p>The returned supplier is thread-safe. The delegate's {@code get()} method will be invoked at
   * most once <b>EVEN IF</b> the underlying {@code get()} throws an exception (note that the
   * behavior is different from {@code com.google.common.base.Suppliers#memoize}).
   *
   * <p>When the underlying delegate throws an exception then this memoizing supplier will always
   * return the same exception instance.
   *
   * <p>If {@code delegate} is an instance created by an earlier call to {@code memoize}, it is
   * returned directly.
   */
  public static <T> ThrowingSupplier<T> memoize(ThrowingSupplier<T> delegate) {
    if (delegate instanceof MemoizingThrowingSupplier) {
      return delegate;
    }
    return new MemoizingThrowingSupplier<>(delegate);
  }

  private static class MemoizingThrowingSupplier<T> implements ThrowingSupplier<T> {

    private final ThrowingSupplier<T> delegate;

    private volatile boolean initialized;

    private volatile boolean successful;
    private volatile T value;
    private volatile Throwable error;

    private MemoizingThrowingSupplier(ThrowingSupplier<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T get() throws MobileHarnessException {
      if (!initialized) {
        synchronized (this) {
          if (!initialized) {
            try {
              value = delegate.get();
              successful = true;
            } catch (MobileHarnessException | RuntimeException | Error e) {
              error = e;
              successful = false;
            }
            initialized = true;
          }
        }
      }

      if (successful) {
        return value;
      } else {
        Throwables.propagateIfPossible(error, MobileHarnessException.class);
        throw new AssertionError(); // The error can always be propagated.
      }
    }
  }

  private MoreSuppliers() {}
}
