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

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.Futures.whenAllSucceed;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugStackTrace;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

/** More utility futures. */
public class MoreFutures {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private MoreFutures() {}

  /** Adds a callback to a future that will log a message if the future fails. */
  @SuppressWarnings("FloggerWithoutCause")
  @FormatMethod
  @CanIgnoreReturnValue
  public static <V> ListenableFuture<V> logFailure(
      ListenableFuture<V> future, Level level, String message, Object... params) {
    addCallback(
        future,
        new FutureCallback<>() {
          @Override
          public void onSuccess(V result) {}

          @Override
          public void onFailure(Throwable t) {
            boolean interrupted = t instanceof InterruptedException;
            logger.at(level).withCause(interrupted ? null : t).logVarargs(message, params);
            if (interrupted) {
              logger.at(level).log(
                  "%s, stack_trace=%s", t, shortDebugStackTrace(t, /* maxLength= */ 0));
            }
          }
        },
        directExecutor());
    return future;
  }

  /**
   * Calls {@link java.util.concurrent.Future#get}. If the cause of {@link ExecutionException} is
   * {@link MobileHarnessException}, {@link InterruptedException} or an unchecked exception, throws
   * the cause directly. Otherwise, if the cause is another checked exception, wraps the cause in a
   * new {@link MobileHarnessException} with the given {@link ErrorId} and throws it.
   */
  public static <V> V get(ListenableFuture<V> future, ErrorId defaultErrorId)
      throws MobileHarnessException, InterruptedException {
    try {
      return future.get();
    } catch (ExecutionException e) {
      return MobileHarnessExceptions.rethrow(e.getCause(), defaultErrorId);
    }
  }

  /**
   * Calls {@link java.util.concurrent.Future#get}. If the cause of the {@link ExecutionException}
   * is an unchecked exception or an {@link InterruptedException}, throws the cause directly.
   * Otherwise, throws an {@link AssertionError}.
   *
   * @throws CancellationException if the computation was cancelled
   * @throws InterruptedException if the current thread was interrupted while waiting, or the
   *     computation threw an {@link InterruptedException}
   */
  @CanIgnoreReturnValue
  public static <V> V getUnchecked(ListenableFuture<V> future) throws InterruptedException {
    try {
      return future.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      throwIfInstanceOf(cause, InterruptedException.class);
      throwIfUnchecked(cause);
      throw new AssertionError(cause);
    }
  }

  /**
   * Creates a new {@code ListenableFuture} whose value is a map containing the values of all its
   * input futures at each corresponding key, if all succeed.
   *
   * <p>The map of results is in the same order as the input map.
   *
   * <p>Failure of one input future will make the result future fail, and will cancel other input
   * futures.
   *
   * <p>Cancellation of one input future or the result future will cancel all input futures and the
   * result future.
   */
  public static <K, V> ListenableFuture<ImmutableMap<K, V>> allAsMap(
      ImmutableMap<? extends K, ? extends ListenableFuture<? extends V>> futures) {
    ListenableFuture<ImmutableMap<K, V>> result =
        whenAllSucceed(futures.values())
            .call(
                () ->
                    futures.entrySet().stream()
                        .collect(
                            toImmutableMap(Entry::getKey, e -> Futures.getUnchecked(e.getValue()))),
                directExecutor());
    addCallback(result, new AllAsMapCallback<>(futures.values()), directExecutor());
    return result;
  }

  private static class AllAsMapCallback<K, V> implements FutureCallback<ImmutableMap<K, V>> {

    private final ImmutableCollection<? extends ListenableFuture<? extends V>> futures;

    private AllAsMapCallback(ImmutableCollection<? extends ListenableFuture<? extends V>> futures) {
      this.futures = futures;
    }

    @Override
    public void onSuccess(ImmutableMap<K, V> result) {
      // Does nothing.
    }

    @SuppressWarnings("Interruption")
    @Override
    public void onFailure(Throwable t) {
      futures.forEach(future -> future.cancel(/* mayInterruptIfRunning= */ true));
    }
  }
}
