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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.time.Duration;
import java.time.Instant;

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

  /**
   * Returns a supplier which caches the instance retrieved during the first call to {@code get()}
   * and removes the cached value after the specified time has passed. Subsequent calls to {@code
   * get()} return the cached value if the expiration time has not passed. After the expiration
   * time, a new value is retrieved, cached, and returned.
   *
   * <p>The returned supplier is thread-safe. The delegate's {@code get()} method will be invoked at
   * most once before the expiration time. And the expiration time will be extended with the given
   * {@code duration} after the delegate's {@code get()} method is invoked.
   *
   * <p>When the underlying delegate throws an exception then this memoizing supplier will always
   * return the same exception instance until expiration (note that the behavior is different from
   * {@code com.google.common.base.Suppliers#memoizeWithExpiration}).
   *
   * <p>This method provides the similar functionality as {@code
   * com.google.common.base.Suppliers#memoizeWithExpiration} and it follows GoodTime practices.
   */
  public static <T> ThrowingSupplier<T> memoizeWithExpiration(
      ThrowingSupplier<T> delegate, Duration duration) {
    checkNotNull(delegate);
    // The alternative of `duration.compareTo(Duration.ZERO) > 0` causes J2ObjC trouble.
    checkArgument(
        !duration.isNegative() && !duration.isZero(), "duration (%s) must be > 0", duration);
    return new ExpiringMemoizingThrowingSupplier<>(delegate, duration);
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
        throwIfInstanceOf(error, MobileHarnessException.class);
        throwIfUnchecked(error);
        throw new AssertionError(); // The error can always be propagated.
      }
    }
  }

  private static class ExpiringMemoizingThrowingSupplier<T> implements ThrowingSupplier<T> {
    private final ThrowingSupplier<T> delegate;
    private final Duration duration;

    private volatile boolean successful;
    private volatile T value;
    private volatile Throwable error;
    private volatile Instant expirationTime;

    ExpiringMemoizingThrowingSupplier(ThrowingSupplier<T> delegate, Duration duration) {
      this.delegate = delegate;
      this.duration = duration;
    }

    @Override
    public T get() throws MobileHarnessException {
      if (expirationTime == null || Instant.now().isAfter(expirationTime)) {
        synchronized (this) {
          Instant now = Instant.now();
          if (expirationTime == null || now.isAfter(expirationTime)) {
            try {
              value = delegate.get();
              successful = true;
            } catch (MobileHarnessException | RuntimeException | Error e) {
              error = e;
              successful = false;
            }
            expirationTime = now.plus(duration);
          }
        }
      }

      if (successful) {
        return value;
      } else {
        throwIfInstanceOf(error, MobileHarnessException.class);
        throwIfUnchecked(error);
        throw new AssertionError(); // The error can always be propagated.
      }
    }
  }

  private MoreSuppliers() {}
}
