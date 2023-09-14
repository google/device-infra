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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Comparators.min;
import static com.google.common.collect.Iterables.size;
import static com.google.common.math.LongMath.saturatedAdd;
import static com.google.common.math.LongMath.saturatedMultiply;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.AbstractIterator;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.Serializable;
import java.time.Duration;
import javax.annotation.Nullable;

/**
 * An immutable strategy for retrying. Typically, it will be instantiated once and reused by a
 * component that wants to retry operations using a particular strategy. The static factory methods
 * return threadsafe instances that may be shared among any number of callers.
 *
 * <p>{@code RetryStrategy} instances are immutable, so you can store them in {@code static final}
 * fields:
 *
 * <pre>
 * private static final RetryStrategy RETRY_STRATEGY =
 *     RetryStrategy.exponentialBackoff(Duration.ofSeconds(5), 5, 3).withRandomization(0.5);
 * </pre>
 *
 * {@code RetryStrategy} is mainly useful as an input to some other classes in this package. If you
 * need to use it more directly, the simplest approach is often to use a for-each loop to compute
 * the delays:
 *
 * <pre>
 * for (Duration delay : RETRY_STRATEGY.delays()) {
 *   ...
 * }
 * </pre>
 *
 * <p><b>Note:</b> the first delay is always {@code Duration.ZERO}.
 */
public abstract class RetryStrategy implements Serializable {

  /**
   * This method is called to determine whether the caller should retry an operation.
   *
   * @param tries the number of previous attempts. (>= 0)
   * @return true if caller should try again
   */
  public boolean tryAgain(int tries) {
    return !getDelay(tries).isNegative();
  }

  /**
   * This method returns the delay, given that the operation has failed {@code tries} times.
   *
   * @param tries the number of previous attempts. (>= 0)
   * @return the delay suggested by this strategy, or a negative value to stop trying
   */
  public abstract Duration getDelay(int tries);

  /**
   * This method returns the delay, given that the operation has failed {@code tries} times and has
   * taken {@code elapsed} time so far. Returning a negative value indicates that no more attempts
   * should be made.
   *
   * @param tries the number of previous attempts. (>= 0)
   * @param elapsedTime how long the operation has taken so far
   * @return the delay suggested by this strategy or a negative value to stop trying
   */
  public Duration getDelay(int tries, Duration elapsedTime) {
    if (!tryAgain(tries)) {
      return Duration.ofMillis(-1L);
    }
    return getDelay(tries);
  }

  /**
   * Returns an {@linkplain Iterable iterable} of the delay series in milliseconds using the
   * provided {@link Ticker}.
   */
  public final Iterable<Duration> delaysWithTicker(final Ticker ticker) {
    checkNotNull(ticker);
    return () ->
        new AbstractIterator<>() {
          private int attempts = 0;
          private final Stopwatch stopwatch = Stopwatch.createStarted(ticker);

          @Override
          @Nullable
          protected Duration computeNext() {
            Duration delay = getDelay(attempts, stopwatch.elapsed());
            if (delay.isNegative()) {
              return endOfData();
            }
            attempts++;
            return delay;
          }
        };
  }

  /**
   * Returns the delay series.
   *
   * <p><b>Note:</b> the first delay is always {@link java.time.Duration#ZERO}.
   */
  public final Iterable<Duration> delays() {
    return delaysWithTicker(Ticker.systemTicker());
  }

  /** Returns the number of attempts configured for {@code this}. */
  int getNumAttempts() {
    return size(delays());
  }

  // Provided strategies

  /**
   * A strategy that has a constant delay between retries. <a
   * href="http://go/cascading-failures-retries">In general, use the more sophisticated strategy</a>
   * of {@linkplain #randomized randomized} {@linkplain #exponentialBackoff exponential backoff} --
   * for example, {@code exponentialBackoff(Duration.ofSeconds(5), 5, 3).withRandomization(0.5)}.
   *
   * @param delay the delay between attempts. (>= {@code Duration.ZERO})
   * @param numAttempts the maximum total number of attempts to make. (> 0)
   */
  public static RetryStrategy uniformDelay(Duration delay, int numAttempts) {
    if (delay.isZero()) {
      return noDelay(numAttempts);
    }
    return new ExponentialBackoff(delay, 1, numAttempts) {
      @Override
      public String toString() {
        return MoreObjects.toStringHelper("uniformDelay")
            .add("delay", firstDelay)
            .add("tries", numAttempts)
            .toString();
      }
    };
  }

  /**
   * A strategy that retries immediately. <a href="http://go/cascading-failures-retries">In general,
   * use the more sophisticated strategy</a> of {@linkplain #randomized randomized} {@linkplain
   * #exponentialBackoff exponential backoff} -- for example, {@code exponentialBackoff(5000, 5,
   * 3).withRandomization(0.5)}.
   *
   * @param numAttempts the maximum total number of attempts to make. (> 0)
   */
  public static RetryStrategy noDelay(int numAttempts) {
    return new Immediate(numAttempts) {
      @Override
      public String toString() {
        return MoreObjects.toStringHelper("noDelay").add("tries", numAttempts).toString();
      }
    };
  }

  /** A strategy that only tries once. In general, a more sophisticated strategy is warranted. */
  public static RetryStrategy none() {
    return RetryStrategyNone.INSTANCE;
  }

  /**
   * A strategy that retries for a fixed amount of time. <a
   * href="http://go/cascading-failures-retries">In general, use the more sophisticated strategy</a>
   * of {@linkplain #randomized randomized} {@linkplain #exponentialBackoff exponential backoff} --
   * for example, {@code exponentialBackoff(Duration.ofSeconds(5), 5, 3).withRandomization(0.5)}.
   *
   * @param delay the delay between attempts.
   * @param totalRetryTime the total amount of time to retry for. (> {@code Duration.ZERO})
   * @deprecated this strategy can result in dangerous <a
   *     href="http://go/cascading-failures-retries">thundering herds</a>. Furthermore, this
   *     strategy is also fundamentally broken when combined with {@link #then} (see b/26549059).
   *     Please use an alternative strategy (e.g., {@link #exponentialBackoff}) instead.
   */
  @Deprecated
  public static RetryStrategy timed(Duration delay, Duration totalRetryTime) {
    return new Timed(delay, totalRetryTime);
  }

  /**
   * A general exponential backoff. The delay after the Nth attempt is {@code firstDelay *
   * (multiplier ^ (N-1))}, where N is from 1 to {@code numAttempts - 1} (inclusive).
   *
   * <p><a href="http://go/cascading-failures-retries">In general, supplement this strategy</a> with
   * {@linkplain #randomized randomization} -- for example, {@code
   * exponentialBackoff(Duration.ofSeconds(5), 5, 3).withRandomization(0.5)}.
   *
   * @param firstDelay the base delay (> {@code Duration.ZERO})
   * @param multiplier what the previous delay is multiplied by (> 0)
   * @param numAttempts the maximum total number of attempts to make (> 0)
   */
  public static RetryStrategy exponentialBackoff(
      Duration firstDelay, double multiplier, int numAttempts) {
    return new ExponentialBackoff(firstDelay, multiplier, numAttempts) {
      @Override
      public String toString() {
        return MoreObjects.toStringHelper("exponentialBackoff")
            .add("firstDelayMs", firstDelay)
            .add("multiplier", multiplier)
            .add("tries", numAttempts)
            .toString();
      }
    };
  }

  /**
   * Backoff with some randomness. This can avoid a <a
   * href="http://en.wikipedia.org/wiki/Thundering_herd_problem">thundering herd problem</a>.
   *
   * <pre>{@code
   * randomWait = ( Math.random() - 0.5 ) * 2 * retryStrategy.getDelayMillis() * randomnessFactor;
   * delay = retryStrategy.getDelayMillis() + randomWait;
   * }</pre>
   *
   * <p>Example usage: exponentialBackoff(...).randomized(randomnessFactor);
   *
   * @param randomnessFactor Increases or decreases the randomness. Use a value between 0 and 1.0. 0
   *     means no randomness and 1 means a factor of 0x to 2x delay.
   * @deprecated Prefer {@link #withRandomization(double)}: When {@code randomized} is used on a
   *     {@linkplain #timed(Duration, Duration) timed} strategy, it produces a strategy that retries
   *     forever, rather than respecting the supplied timeout. (If you are <i>not</i> using a timed
   *     strategy, you can switch to {@code withRandomization} with no change in behavior.)
   */
  @Deprecated
  public RetryStrategy randomized(double randomnessFactor) {
    return new RetryStrategyWithRandomWait(this, randomnessFactor);
  }

  /**
   * Backoff with some randomness. This can avoid a <a
   * href="http://en.wikipedia.org/wiki/Thundering_herd_problem">thundering herd problem</a>.
   *
   * <pre>{@code
   * randomWait = ( Math.random() - 0.5 ) * 2 * retryStrategy.getDelayMillis() * randomnessFactor;
   * delay = retryStrategy.getDelayMillis() + randomWait;
   * }</pre>
   *
   * <p>Example usage: exponentialBackoff(...).withRandomization(randomnessFactor);
   *
   * @param randomnessFactor Increases or decreases the randomness. Use a value between 0 and 1.0. 0
   *     means no randomness and 1 means a factor of 0x to 2x delay.
   */
  public RetryStrategy withRandomization(double randomnessFactor) {
    return new RetryStrategyWithRandomWaitAndTimeout(this, randomnessFactor);
  }

  /**
   * Returns a new {@code RetryStrategy} that will continue to retry using {@code next} after {@code
   * this} is exhausted.. For example: {@code RetryStrategy.uniformDelay(1,
   * 2).then(exponentialBackoff(...));}
   */
  public RetryStrategy then(RetryStrategy next) {
    return new ChainedRetryStrategy(this, next);
  }

  private static class Immediate extends RetryStrategy {
    final int numAttempts;

    Immediate(int numAttempts) {
      this.numAttempts = checkPositive(numAttempts, "numAttempts");
    }

    @Override
    int getNumAttempts() {
      return numAttempts;
    }

    @Override
    public boolean tryAgain(int tries) {
      checkNotNegative(tries, "tries");
      return tries < numAttempts;
    }

    @Override
    public Duration getDelay(int tries) {
      return tryAgain(tries) ? Duration.ZERO : Duration.ofMillis(-1L);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj instanceof Immediate) {
        Immediate that = (Immediate) obj;
        return numAttempts == that.numAttempts;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return numAttempts;
    }
  }

  private static final class RetryStrategyNone extends Immediate {
    static final RetryStrategyNone INSTANCE = new RetryStrategyNone();

    RetryStrategyNone() {
      super(1);
    }

    @Override
    public String toString() {
      return "none";
    }
  }

  private static class ExponentialBackoff extends RetryStrategy {
    final int numAttempts;
    final Duration firstDelay;
    final double multiplier;

    ExponentialBackoff(Duration firstDelay, double multiplier, int numAttempts) {
      this.numAttempts = checkPositive(numAttempts, "numAttempts");
      this.firstDelay = checkPositive(firstDelay, "firstDelay");
      this.multiplier = checkPositive(multiplier, "multiplier");
    }

    @Override
    int getNumAttempts() {
      return numAttempts;
    }

    @Override
    public boolean tryAgain(int tries) {
      checkNotNegative(tries, "tries");
      return tries < numAttempts;
    }

    @Override
    public Duration getDelay(int tries) {
      if (tries == 0) {
        return Duration.ZERO;
      }
      if (!tryAgain(tries)) {
        return Duration.ofMillis(-1L);
      }
      return Duration.ofMillis(
          (long) (toMillisSaturated(firstDelay) * Math.pow(multiplier, tries - 1)));
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof ExponentialBackoff) {
        ExponentialBackoff other = (ExponentialBackoff) o;
        return firstDelay.equals(other.firstDelay)
            && multiplier == other.multiplier
            && numAttempts == other.numAttempts;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(numAttempts, firstDelay, multiplier);
    }
  }

  private static final class RetryStrategyWithRandomWait extends RetryStrategy {
    private final RetryStrategy retryStrategy;
    private final double randomnessFactor;

    RetryStrategyWithRandomWait(RetryStrategy retryStrategy, double randomnessFactor) {
      checkArgument(
          randomnessFactor >= 0.0, "randomnessFactor (%s) must be >= 0.0", randomnessFactor);
      checkArgument(
          randomnessFactor <= 1.0, "randomnessFactor (%s) must be <= 1.0", randomnessFactor);
      this.retryStrategy = retryStrategy;
      this.randomnessFactor = randomnessFactor;
    }

    @Override
    int getNumAttempts() {
      return retryStrategy.getNumAttempts();
    }

    @Override
    public Duration getDelay(int tries) {
      Duration delay = retryStrategy.getDelay(tries);
      if (delay.isNegative() || delay.isZero()) {
        // A negative value for delayMillis means to stop trying. So don't add randomness.
        return delay;
      }
      long delayMillis = toMillisSaturated(delay);
      long randomWaitMillis = (long) ((Math.random() - 0.5) * 2 * delayMillis * randomnessFactor);
      return Duration.ofMillis(saturatedAdd(delayMillis, randomWaitMillis));
    }

    @Override
    public String toString() {
      return retryStrategy + ".randomized(" + randomnessFactor + ')';
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (o instanceof RetryStrategyWithRandomWait) {
        RetryStrategyWithRandomWait other = (RetryStrategyWithRandomWait) o;
        return retryStrategy.equals(other.retryStrategy)
            && randomnessFactor == other.randomnessFactor;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(retryStrategy, randomnessFactor);
    }
  }

  private static final class RetryStrategyWithRandomWaitAndTimeout extends RetryStrategy {
    private final RetryStrategy retryStrategy;
    private final double randomnessFactor;

    RetryStrategyWithRandomWaitAndTimeout(RetryStrategy retryStrategy, double randomnessFactor) {
      checkArgument(
          randomnessFactor >= 0.0, "randomnessFactor (%s) must be >= 0.0", randomnessFactor);
      checkArgument(
          randomnessFactor <= 1.0, "randomnessFactor (%s) must be <= 1.0", randomnessFactor);
      this.retryStrategy = retryStrategy;
      this.randomnessFactor = randomnessFactor;
    }

    @Override
    int getNumAttempts() {
      return retryStrategy.getNumAttempts();
    }

    @Override
    public Duration getDelay(int tries) {
      Duration delay = retryStrategy.getDelay(tries);
      if (delay.isNegative() || delay.isZero()) {
        // A negative value for delayMillis means to stop trying. So don't add randomness.
        return delay;
      }
      return randomizeWait(delay);
    }

    @Override
    public Duration getDelay(int tries, Duration elapsedTime) {
      Duration delay = retryStrategy.getDelay(tries, elapsedTime);
      if (delay.isNegative()) {
        // A negative value for delay means to stop trying. So don't add randomness.
        return delay;
      }
      return randomizeWait(delay);
    }

    @Override
    public String toString() {
      return retryStrategy + ".withRandomization(" + randomnessFactor + ')';
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (o instanceof RetryStrategyWithRandomWaitAndTimeout) {
        RetryStrategyWithRandomWaitAndTimeout other = (RetryStrategyWithRandomWaitAndTimeout) o;
        return retryStrategy.equals(other.retryStrategy)
            && randomnessFactor == other.randomnessFactor;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(retryStrategy, randomnessFactor);
    }

    private Duration randomizeWait(Duration delay) {
      long delayMillis = toMillisSaturated(delay);
      long randomWaitMillis = (long) ((Math.random() - 0.5) * 2 * delayMillis * randomnessFactor);
      return Duration.ofMillis(saturatedAdd(delayMillis, randomWaitMillis));
    }
  }

  private static final class Timed extends RetryStrategy {
    private final Duration delay;
    private final Duration totalRetryTime;

    Timed(Duration delay, Duration totalRetryTime) {
      this.delay = checkNotNegative(delay, "delay");
      this.totalRetryTime = checkPositive(totalRetryTime, "totalRetryTime");
    }

    @Override
    int getNumAttempts() {
      return (int) Math.ceil((double) toMillisSaturated(totalRetryTime) / toMillisSaturated(delay));
    }

    @Override
    public Duration getDelay(int tries) {
      return getDelay(tries, Duration.ofMillis(saturatedMultiply(toMillisSaturated(delay), tries)));
    }

    @Override
    public Duration getDelay(int tries, Duration elapsedTime) {
      checkNotNegative(tries, "tries");
      checkNotNegative(elapsedTime, "elapsedTime");
      if (tries == 0) {
        return Duration.ZERO;
      }
      Duration remainingTime = totalRetryTime.minus(elapsedTime);
      if (remainingTime.isNegative() || remainingTime.isZero()) {
        return Duration.ofMillis(-1L);
      }
      return min(remainingTime, delay);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (o instanceof Timed) {
        Timed that = (Timed) o;
        return this.delay.equals(that.delay) && this.totalRetryTime.equals(that.totalRetryTime);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(delay, totalRetryTime);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper("timed")
          .add("delay", delay)
          .add("totalRetryTime", totalRetryTime)
          .toString();
    }
  }

  private static final class ChainedRetryStrategy extends RetryStrategy {
    private final RetryStrategy firstRetryStrategy;
    private final RetryStrategy secondRetryStrategy;
    private final int changeStrategyTries; // Try number at which firstRetryStrategy is exhausted

    ChainedRetryStrategy(RetryStrategy firstRetryStrategy, RetryStrategy secondRetryStrategy) {
      this.firstRetryStrategy = checkNotNull(firstRetryStrategy);
      this.secondRetryStrategy = checkNotNull(secondRetryStrategy);
      this.changeStrategyTries = firstRetryStrategy.getNumAttempts();
    }

    @Override
    int getNumAttempts() {
      return changeStrategyTries + secondRetryStrategy.getNumAttempts() - 1;
    }

    @Override
    public Duration getDelay(int tries) {
      if (tries < changeStrategyTries) {
        return firstRetryStrategy.getDelay(tries);
      }
      return secondRetryStrategy.getDelay(tries + 1 - changeStrategyTries);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (o instanceof ChainedRetryStrategy) {
        ChainedRetryStrategy other = (ChainedRetryStrategy) o;
        return firstRetryStrategy.equals(other.firstRetryStrategy)
            && secondRetryStrategy.equals(other.secondRetryStrategy);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(firstRetryStrategy, secondRetryStrategy);
    }
  }

  @CanIgnoreReturnValue
  private static int checkPositive(int value, String valueName) {
    checkArgument(value > 0, "%s (%s) must be > 0", valueName, value);
    return value;
  }

  @CanIgnoreReturnValue
  private static double checkPositive(double value, String valueName) {
    checkArgument(value > 0, "%s (%s) must be > 0", valueName, value);
    return value;
  }

  @CanIgnoreReturnValue
  private static Duration checkPositive(Duration value, String valueName) {
    checkArgument(!value.isNegative() && !value.isZero(), "%s (%s) must be > 0", valueName, value);
    return value;
  }

  @CanIgnoreReturnValue
  private static int checkNotNegative(int value, String valueName) {
    checkArgument(value >= 0, "%s (%s) must be >= 0", valueName, value);
    return value;
  }

  @CanIgnoreReturnValue
  private static Duration checkNotNegative(Duration value, String valueName) {
    checkArgument(!value.isNegative(), "%s (%s) must be >= 0", valueName, value);
    return value;
  }

  // Copied from com.google.common.time.Durations.toMillisSaturated(Duration):
  private static long toMillisSaturated(Duration duration) {
    if (duration.compareTo(Duration.ofMillis(Long.MAX_VALUE)) >= 0) {
      return Long.MAX_VALUE;
    }
    if (duration.compareTo(Duration.ofMillis(Long.MIN_VALUE)) <= 0) {
      return Long.MIN_VALUE;
    }
    // Still use a try/catch because different platforms have slightly different overflow edge cases
    try {
      return duration.toMillis();
    } catch (ArithmeticException tooBig) {
      return duration.isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
  }
}
