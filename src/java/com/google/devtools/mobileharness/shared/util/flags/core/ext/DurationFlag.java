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

package com.google.devtools.mobileharness.shared.util.flags.core.ext;

import static com.google.devtools.mobileharness.shared.util.flags.core.Flag.Validator.greaterThan;
import static com.google.devtools.mobileharness.shared.util.flags.core.Flag.Validator.greaterThanOrEqualTo;
import static com.google.devtools.mobileharness.shared.util.flags.core.Flag.Validator.nonNull;

import com.google.devtools.mobileharness.shared.util.flags.core.Flag;
import java.time.Duration;

/** Flags extensions for {@link Duration}. */
public final class DurationFlag {

  /** Creates a Duration {@code Flag} instance with the specified default value. */
  public static Flag<Duration> value(Duration defaultValue) {
    return Flag.value(defaultValue, nonNull());
  }

  /** Creates a Duration {@code Flag} instance without a default value. */
  public static Flag<Duration> nullValue() {
    return Flag.value((Duration) null, /* validator= */ null);
  }

  /** Creates a Duration {@code Flag} instance with default value ZERO. */
  public static Flag<Duration> zero() {
    return Flag.value(Duration.ZERO, nonNull());
  }

  /** Creates a non-negative Duration {@code Flag} instance. */
  public static Flag<Duration> nonNegativeValue(Duration defaultValue) {
    return Flag.value(defaultValue, greaterThanOrEqualTo(Duration.ZERO));
  }

  /** Creates a positive Duration {@code Flag} instance. */
  public static Flag<Duration> positiveValue(Duration defaultValue) {
    return Flag.value(defaultValue, greaterThan(Duration.ZERO));
  }

  /** Creates a Duration {@code Flag} instance with interval checking. */
  public static Flag<Duration> interval(Duration lower, Duration upper, Duration defaultValue) {
    return Flag.value(defaultValue, Flag.Validator.interval(lower, upper));
  }

  /** Creates a Duration {@code Flag} instance with default value in days. */
  public static Flag<Duration> days(long defaultValue) {
    return value(Duration.ofDays(defaultValue));
  }

  /** Creates a Duration {@code Flag} instance with default value in hours. */
  public static Flag<Duration> hours(long defaultValue) {
    return value(Duration.ofHours(defaultValue));
  }

  /** Creates a Duration {@code Flag} instance with default value in minutes. */
  public static Flag<Duration> minutes(long defaultValue) {
    return value(Duration.ofMinutes(defaultValue));
  }

  /** Creates a Duration {@code Flag} instance with default value in seconds. */
  public static Flag<Duration> seconds(long defaultValue) {
    return value(Duration.ofSeconds(defaultValue));
  }

  private DurationFlag() {}
}
