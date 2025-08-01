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

package com.google.devtools.mobileharness.shared.util.cache;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.ThreadSafeTypeParameter;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A wrapper of a {@link ListenableFuture} of Optional<V> with a finish timestamp.
 *
 * <p>If the {@link ListenableFuture} is done, the finish timestamp is computed immediately. If the
 * {@link ListenableFuture} is still running, the finish timestamp is set when the {@link
 * ListenableFuture} is completed. In the latter case, the valueFuture is essentially equivalent to
 * the original {@link ListenableFuture} except that it will be completed after the finish timestamp
 * is set.
 */
@ThreadSafe
public final class RecordedResult<@ThreadSafeTypeParameter V> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ListenableFuture<Optional<V>> valueFuture;
  private final AtomicReference<Instant> finishTimestamp;

  private RecordedResult(
      ListenableFuture<Optional<V>> valueFuture, AtomicReference<Instant> finishTimestamp) {
    this.valueFuture = valueFuture;
    this.finishTimestamp = finishTimestamp;
  }

  /**
   * Creates a {@link RecordedResult} from a {@link ListenableFuture} of Optional<V> and an {@link
   * InstantSource}.
   *
   * @param valueFuture the {@link ListenableFuture} of Optional<V> that provides the possible
   *     result.
   * @param executor the {@link Executor} to set the finish timestamp.
   * @param instantSource the {@link InstantSource} to get the finish timestamp.
   * @return the {@link RecordedResult}
   */
  @SuppressWarnings("Interruption") // We are propagating an interrupt from the caller.
  public static <@ThreadSafeTypeParameter V> RecordedResult<V> create(
      ListenableFuture<Optional<V>> valueFuture, Executor executor, InstantSource instantSource) {
    AtomicReference<Instant> finishTimestamp = new AtomicReference<>();
    if (valueFuture.isDone()) {
      try {
        finishTimestamp.set(instantSource.instant());
      } catch (DateTimeException e) {
        logger.atWarning().withCause(e).log("Failed to set finish timestamp.");
      }
      return new RecordedResult<>(valueFuture, finishTimestamp);
    }

    SettableFuture<Optional<V>> finalFuture = SettableFuture.create();
    valueFuture.addListener(
        () -> {
          try {
            finishTimestamp.set(instantSource.instant());
          } catch (DateTimeException e) {
            logger.atWarning().withCause(e).log("Failed to set finish timestamp.");
          }
          finalFuture.setFuture(valueFuture);
        },
        executor);

    finalFuture.addListener(
        () -> {
          if (finalFuture.isCancelled()) {
            valueFuture.cancel(/* mayInterruptIfRunning= */ true);
          }
        },
        executor);
    return new RecordedResult<>(finalFuture, finishTimestamp);
  }

  /** Returns the {@link ListenableFuture} of Optional<V> that provides the possible result. */
  public ListenableFuture<Optional<V>> valueFuture() {
    return valueFuture;
  }

  /**
   * Returns the finish timestamp of the valueFuture. Returns empty if it fails to set the finish
   * timestamp.
   */
  public Optional<Instant> finishTimestamp() {
    return Optional.ofNullable(finishTimestamp.get());
  }
}
