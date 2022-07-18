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

package com.google.devtools.deviceinfra.shared.util.command;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import java.time.Clock;
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
  public static Timeout deadline(Instant deadline) {
    return builder().deadline(deadline).build();
  }

  /** A timeout with a fixed duration and a deadline. */
  public static Timeout of(Duration fixedTimeout, Instant deadline) {
    return builder().period(fixedTimeout).deadline(deadline).build();
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
  public Timeout withDeadline(Instant deadline) {
    return toBuilder().deadline(deadline).build();
  }

  /**
   * Gets the remaining time before timeout, which means if a command is executed now with this
   * timeout object, the command will timeout and be killed after the remaining time.
   */
  public Duration getRemainingTime() {
    Duration remainingTime = null;
    Optional<Instant> deadline = getDeadline();
    if (deadline.isPresent()) {
      remainingTime = Duration.between(Clock.systemUTC().instant(), deadline.get());
    }
    Optional<Duration> period = getPeriod();
    if (period.isPresent()
        && (remainingTime == null || period.get().compareTo(remainingTime) < 0)) {
      remainingTime = period.get();
    }
    return remainingTime;
  }

  public abstract Optional<Duration> getPeriod();

  public abstract Optional<Instant> getDeadline();

  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder period(Duration period);

    abstract Builder deadline(Instant deadline);

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
}
