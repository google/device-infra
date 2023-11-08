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

package com.google.devtools.mobileharness.shared.util.concurrent.retry;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * A {@code Callable} providing blocking retry capabilities. The retries may be controlled by the
 * combination of a typical strategy (e.g. exponential backoff) and a user-provided {@link
 * Predicate} that is invoked on each failure. Retry delays are measured from the end of one attempt
 * to the beginning of the next.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Retry according to 5 ms * (2 ^ (tries - 1)), making 6 attempts
 * // So delays will be 5 ms, 10 ms, 20 ms, 40 ms, 80 ms, (ABORT)
 * private static final RetryStrategy retryStrategy =
 *     RetryStrategy.exponentialBackoff(5, 2, 6);
 *
 * public void method() {
 *   ...
 *   RetryingCallable<Row> fetchRowRetryCallable =
 *       RetryingCallable.newBuilder(
 *               () -> table.fetchRow("rowKey"), retryStrategy)
 *           .build();
 *   Row row;
 *   try {
 *     row = fetchRowRetryCallable.call();
 *   } catch (RetryException e) {
 *     // The fetch failed e.getTries() times
 *     // The first exception thrown was e.getCause()
 *   }
 *   ...
 *   // Fetch row again for some reason
 *   fetchRowRetryCallable.reset()
 *   try {
 *     row = fetchRowRetryCallable.call();
 *   } catch (...) {
 *     ...
 *   }
 * }
 * }</pre>
 *
 * <p>This class is not threadsafe, since it has internal state describing the status of previous
 * attempts.
 *
 * <p>If the callable is interrupted during its first execution attempt, the cause of the {@link
 * RetryException} will be set to {@link InterruptedException} and the interrupt flag will be set.
 * An interruption after the first attempt will result in a {@link RetryException} with the cause
 * set to the last exception (non {@link InterruptedException}) thrown by the callable and the
 * interrupt flag set.
 *
 * @param <T> the result type of method <tt>call</tt>
 */
public class RetryingCallable<T> implements Callable<T> {

  /** The default exception handler logs every exception with INFO level. */
  private static final RetryExceptionHandler<Throwable> DEFAULT_EXCEPTION_HANDLER =
      RetryExceptionHandlers.logException(Level.INFO);

  private final Callable<T> callable;
  private final RetryStrategy strategy;
  private final Predicate<? super Exception> shouldContinue;
  private final Clock clock;
  private final Sleeper sleeper;
  private final ThrowStrategy throwStrategy;
  private final RetryExceptionHandler<? super Exception> exceptionHandler;
  private volatile int tries;
  @Nullable private volatile Exception caught;

  /** Builds a new Retrying callable. See {@link Builder} for parameter documentation. */
  private RetryingCallable(
      Clock clock,
      Sleeper sleeper,
      Callable<T> callable,
      RetryStrategy strategy,
      Predicate<? super Exception> shouldContinue,
      ThrowStrategy throwStrategy,
      RetryExceptionHandler<? super Exception> exceptionHandler) {
    this.clock = clock;
    this.sleeper = sleeper;
    this.callable = callable;
    this.strategy = strategy;
    this.shouldContinue = shouldContinue;
    this.throwStrategy = throwStrategy;
    this.exceptionHandler = exceptionHandler;
    this.tries = 0;
    this.caught = null;
  }

  /**
   * Creates a builder for {@link RetryingCallable} instances.
   *
   * @param callable the function to call
   * @param strategy the strategy for overcoming failure
   */
  public static <T> Builder<T> newBuilder(Callable<T> callable, RetryStrategy strategy) {
    checkNotNull(callable);
    checkNotNull(strategy);
    return new Builder<>(callable, strategy);
  }

  /** A builder for the {@link RetryingCallable} class. */
  public static class Builder<T> {
    private final Callable<T> callable;
    private final RetryStrategy strategy;

    private Clock clock = Clock.systemUTC();
    private Sleeper sleeper = Sleeper.defaultSleeper();
    private Predicate<? super Exception> shouldContinue = unused -> true;
    private ThrowStrategy throwStrategy = ThrowStrategy.THROW_FIRST;
    private RetryExceptionHandler<? super Exception> exceptionHandler = DEFAULT_EXCEPTION_HANDLER;

    /**
     * Creates a builder for {@link RetryingCallable} instances.
     *
     * @param callable the function to call
     * @param strategy the strategy for overcoming failure
     */
    private Builder(Callable<T> callable, RetryStrategy strategy) {
      this.callable = callable;
      this.strategy = strategy;
    }

    /** Sets the clock used for timing. */
    @CanIgnoreReturnValue
    public Builder<T> setClock(Clock clock) {
      this.clock = checkNotNull(clock);
      return this;
    }

    /** Sets the sleeper used to wait before retrying again. */
    @CanIgnoreReturnValue
    public Builder<T> setSleeper(Sleeper sleeper) {
      this.sleeper = checkNotNull(sleeper);
      return this;
    }

    /**
     * Sets the predicate used to decide whether to retry on an exception. If the predicate returns
     * true for a given exception, the callable will retry.
     */
    @CanIgnoreReturnValue
    public Builder<T> setPredicate(Predicate<? super Exception> shouldContinue) {
      this.shouldContinue = checkNotNull(shouldContinue);
      return this;
    }

    /**
     * Sets the strategy to determine which exception gets thrown when retries are exhausted or the
     * predicate returns false.
     */
    @CanIgnoreReturnValue
    public Builder<T> setThrowStrategy(ThrowStrategy throwStrategy) {
      this.throwStrategy = checkNotNull(throwStrategy);
      return this;
    }

    /**
     * Provides a method to intercept every retry attempt. If the handler throws an exception, the
     * retry loop breaks, and the exception is thrown to the caller. If this is not specified, the
     * default implementation will log the exception at {@link Level#INFO} level.
     *
     * <p>Note that the last exception when there are no more retry attempts is not passed to this
     * handler; instead is thrown to the caller.
     */
    @CanIgnoreReturnValue
    public Builder<T> setExceptionHandler(
        RetryExceptionHandler<? super Exception> exceptionHandler) {
      this.exceptionHandler = checkNotNull(exceptionHandler);
      return this;
    }

    /** Builds a new instance of {@link RetryingCallable}. */
    public RetryingCallable<T> build() {
      return new RetryingCallable<>(
          clock, sleeper, callable, strategy, shouldContinue, throwStrategy, exceptionHandler);
    }
  }

  /**
   * Calls the wrapped callable using the configured strategy.
   *
   * @implNote `tries` is `volatile` because it can be *read* from any thread at any time. However,
   *     RetryingCallable ensures that *writes* are performed by only one thread at a time, with
   *     proper happens-before edges in place. Thus, it's safe to increment `tries`.
   */
  @CanIgnoreReturnValue
  @Override
  @SuppressWarnings({"NonAtomicVolatileUpdate", "NonAtomicOperationOnVolatileField"})
  public T call() throws RetryException {
    Instant start = clock.instant();
    while (true) {
      ++tries;
      try {
        return callable.call();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        exceptionHandler.interrupted(e, tries);
        throw new RetryException(tries, caught != null ? caught : e);
      } catch (Exception e) {
        if (throwStrategy == ThrowStrategy.THROW_LAST || caught == null) {
          caught = e;
        }
        Duration delayTime = strategy.getDelay(tries, Duration.between(start, clock.instant()));
        if (!delayTime.isNegative() && shouldContinue.test(e)) {
          try {
            sleeper.sleep(delayTime);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            exceptionHandler.interrupted(ie, tries);
            throw new RetryException(tries, caught);
          }
          exceptionHandler.aboutToRetry(e, tries, delayTime);
        } else {
          throw new RetryException(tries, caught);
        }
      }
    }
  }

  /** Returns the number of times the wrapped callable has been called. */
  public int getTries() {
    return tries;
  }

  /** Resets this callable to be just like new. */
  public void reset() {
    tries = 0;
    caught = null;
  }

  /** Allows clients to configure which exception is thrown when retries are exhausted. */
  public enum ThrowStrategy {
    THROW_FIRST,
    THROW_LAST
  }
}
