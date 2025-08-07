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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.ThreadSafe;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * A wrapper of {@link ListenableFuture} that records the finish timestamp of the future.
 *
 * <p>The finish timestamp is the timestamp recorded when the original future is completed due to
 * normal termination or an exception. The wrapper future will be completed after the finish
 * timestamp is recorded.
 */
@ThreadSafe
public final class TimestampedFuture<V> extends ForwardingListenableFuture<V> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ListenableFuture<V> delegate;
  private final SettableFuture<Instant> finishTimestamp;

  private TimestampedFuture(ListenableFuture<V> delegate, SettableFuture<Instant> finishTimestamp) {
    this.delegate = delegate;
    this.finishTimestamp = finishTimestamp;
  }

  /**
   * Creates a {@link TimestampedFuture} from a {@link ListenableFuture} and an {@link
   * InstantSource} to get the finish timestamp.
   *
   * @param delegate the delegate future
   * @param executor the executor to invoke the InstantSource#instant() after the future is done
   * @param instantSource the instant source to provide the finish timestamp
   * @return the timestamped future
   */
  @SuppressWarnings("Interruption") // We are propagating an interrupt from the caller.
  public static <V> TimestampedFuture<V> create(
      ListenableFuture<V> delegate, Executor executor, InstantSource instantSource) {
    SettableFuture<Instant> finishTimestamp = SettableFuture.create();
    if (delegate.isDone()) {
      if (delegate.isCancelled()) {
        finishTimestamp.cancel(/* mayInterruptIfRunning= */ true);
      } else {
        try {
          finishTimestamp.set(instantSource.instant());
        } catch (DateTimeException e) {
          finishTimestamp.setException(e);
        }
      }
      return new TimestampedFuture<>(delegate, finishTimestamp);
    }

    SettableFuture<V> finalDelegate = SettableFuture.create();
    delegate.addListener(
        () -> {
          if (delegate.isCancelled()) {
            finishTimestamp.cancel(/* mayInterruptIfRunning= */ true);
          } else {
            try {
              finishTimestamp.set(instantSource.instant());
            } catch (DateTimeException e) {
              finishTimestamp.setException(e);
            }
          }
          // The final delegate is set after the finish timestamp is recorded.
          finalDelegate.setFuture(delegate);
        },
        executor);

    finalDelegate.addListener(
        () -> {
          if (finalDelegate.isCancelled()) {
            delegate.cancel(/* mayInterruptIfRunning= */ true);
            finishTimestamp.cancel(/* mayInterruptIfRunning= */ true);
          }
        },
        executor);
    return new TimestampedFuture<>(finalDelegate, finishTimestamp);
  }

  /**
   * Creates a {@link TimestampedFuture} from a {@link ListenableFuture} with finish timestamp
   * provided by the system clock.
   *
   * @param delegate the delegate future
   * @param executor the executor to invoke the InstantSource#instant() after the future is done
   * @return the timestamped future
   */
  @SuppressWarnings("Interruption") // We are propagating an interrupt from the caller.
  public static <V> TimestampedFuture<V> create(ListenableFuture<V> delegate, Executor executor) {
    return create(delegate, executor, InstantSource.system());
  }

  @Override
  protected ListenableFuture<V> delegate() {
    return delegate;
  }

  /**
   * Returns the finish timestamp of the future.
   *
   * <p>The finish timestamp is recorded when the future is completed due to normal termination or
   * an exception.
   *
   * @return the finish timestamp of the future. The timestamp is empty if the future is not done
   *     yet or cancelled or interrupted.
   * @throws DateTimeException if the instantSource#instant() method throws a DateTimeException.
   */
  public Optional<Instant> finishTimestamp() {
    if (!finishTimestamp.isDone()) {
      return Optional.empty();
    }
    try {
      return Optional.of(finishTimestamp.get());
    } catch (ExecutionException e) {
      if (e.getCause() instanceof DateTimeException) {
        throw (DateTimeException) e.getCause();
      }
      throw new AssertionError("Should not happen.", e);
    } catch (CancellationException e) {
      logger.atInfo().withCause(e).log(
          "No finish timestamp is recorded because the future is cancelled.");
      return Optional.empty();
    } catch (InterruptedException e) {
      logger.atInfo().withCause(e).log(
          "No finish timestamp is recorded because the future is interrupted.");
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
