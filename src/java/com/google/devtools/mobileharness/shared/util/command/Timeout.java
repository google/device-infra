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

package com.google.devtools.mobileharness.shared.util.command;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Timeout/start timeout of a command.
 *
 * @see Command
 * @see Command#timeout(Timeout)
 * @see Command#startTimeout(Timeout)
 */
@AutoValue
public abstract class Timeout {

  /** A fixed timeout with specified duration. */
  public static Timeout fixed(Duration timeout) {
    return builder().period(timeout).build();
  }

  /** A timeout with a deadline. */
  public static Timeout deadline(CountDownTimer deadline) {
    return builder().deadline(deadline).build();
  }

  /** A timeout with a deadline. */
  public static Timeout deadline(Instant deadline) {
    return deadline(new FixedDeadline(deadline));
  }

  /** A timeout with a fixed duration and a deadline. */
  public static Timeout of(Duration fixedTimeout, CountDownTimer deadline) {
    return builder().period(fixedTimeout).deadline(deadline).build();
  }

  /** A timeout with a fixed duration and a deadline. */
  public static Timeout of(Duration fixedTimeout, Instant deadline) {
    return of(fixedTimeout, new FixedDeadline(deadline));
  }

  /**
   * Returns a timeout that behaves equivalently to this timeout, but with the specified fixed
   * duration in place of the current fixed duration.
   */
  public Timeout withFixed(Duration timeout) {
    return toBuilder().period(timeout).build();
  }

  /**
   * Returns a timeout that behaves equivalently to this timeout, but with the specified deadline in
   * place of the current deadline.
   */
  public Timeout withDeadline(CountDownTimer deadline) {
    return toBuilder().deadline(deadline).build();
  }

  /**
   * Returns a timeout that behaves equivalently to this timeout, but with the specified deadline in
   * place of the current deadline.
   */
  public Timeout withDeadline(Instant deadline) {
    return withDeadline(new FixedDeadline(deadline));
  }

  /**
   * Gets the remaining time before timeout, which means if a command is executed now with this
   * timeout object, the command will timeout and be killed after the remaining time.
   *
   * @throws MobileHarnessException if fails to get remaining time
   */
  public Duration getRemainingTime() throws MobileHarnessException {
    Duration remainingTime = null;
    Optional<CountDownTimer> deadline = getDeadline();
    if (deadline.isPresent()) {
      remainingTime = deadline.get().remainingTimeJava();
    }
    Optional<Duration> period = getPeriod();
    if (period.isPresent()
        && (remainingTime == null || period.get().compareTo(remainingTime) < 0)) {
      remainingTime = period.get();
    }
    return remainingTime;
  }

  public abstract Optional<Duration> getPeriod();

  public abstract Optional<CountDownTimer> getDeadline();

  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder period(Duration period);

    abstract Builder deadline(CountDownTimer countDownTimer);

    abstract Timeout autoBuild();

    private Timeout build() {
      Timeout timeout = autoBuild();
      checkState(
          timeout.getPeriod().isPresent() || timeout.getDeadline().isPresent(),
          "Timeout should have period or deadline");
      return timeout;
    }
  }

  abstract Builder toBuilder();

  private static Builder builder() {
    return new AutoValue_Timeout.Builder();
  }

  @VisibleForTesting
  static class FixedDeadline implements CountDownTimer {

    private final Instant deadline;

    @VisibleForTesting
    FixedDeadline(Instant deadline) {
      this.deadline = deadline;
    }

    @Override
    public Instant expireTime() {
      return deadline;
    }

    @Override
    public boolean isExpired() {
      return deadline.isBefore(Instant.now());
    }

    @Override
    public Duration remainingTimeJava() {
      return Duration.between(Instant.now(), deadline);
    }
  }
}
