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

package com.google.devtools.mobileharness.shared.util.concurrent;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Thread-safe lazy holder for a resource whose cleanup runs at most once on {@link #close()}.
 *
 * <p>The resource is created on the first {@link #get()} call, reused for subsequent calls, and
 * released outside the internal lock on {@link #close()}. Closing an unused instance is a no-op and
 * never invokes the supplier.
 */
public final class LazyCloseable<T> implements NonThrowingAutoCloseable {

  private final Supplier<? extends T> supplier;
  private final Consumer<? super T> closer;
  private final Object lock = new Object();

  @GuardedBy("lock")
  @Nullable
  private T value;

  @GuardedBy("lock")
  private boolean closed;

  private LazyCloseable(Supplier<? extends T> supplier, Consumer<? super T> closer) {
    this.supplier = checkNotNull(supplier);
    this.closer = checkNotNull(closer);
  }

  /**
   * Creates a {@link LazyCloseable} backed by {@code supplier} and released via {@code closer}.
   *
   * <p>{@code supplier} runs while holding the internal lock, so concurrent {@link #get()} and
   * {@link #close()} calls wait for an in-flight initialization. It must return a non-null resource
   * and must not call {@link #get()} or {@link #close()} on this instance. If it throws, the
   * failure is not cached and a later {@link #get()} retries until {@link #close()} is called.
   */
  public static <T> LazyCloseable<T> of(
      Supplier<? extends T> supplier, Consumer<? super T> closer) {
    return new LazyCloseable<>(supplier, closer);
  }

  /**
   * Returns the lazily created resource, creating and caching it on first call.
   *
   * @throws IllegalStateException if this holder has already been closed
   */
  public T get() {
    synchronized (lock) {
      checkState(!closed, "LazyCloseable is already closed");
      if (value == null) {
        value = checkNotNull(supplier.get(), "LazyCloseable supplier returned null");
      }
      return value;
    }
  }

  /**
   * Marks this holder closed and releases the cached resource if one was created.
   *
   * <p>Idempotent: later calls are no-ops. {@code closer} runs outside the internal lock.
   */
  @Override
  public void close() {
    T toClose;
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      toClose = value;
      value = null;
    }
    if (toClose != null) {
      closer.accept(toClose);
    }
  }
}
