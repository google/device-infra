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

package com.google.devtools.deviceaction.common.utils;

import static com.google.common.math.LongMath.checkedAdd;

import com.google.protobuf.util.Durations;
import java.time.Duration;

/** A utility class for time. */
public class TimeUtils {
  private static final int NANOS_PER_SECOND = 1000000000;

  private TimeUtils() {}

  /** Checks if the duration is positive. */
  public static boolean isPositive(Duration duration) {
    return !duration.isNegative() && !duration.isZero();
  }

  /** Converts proto duration to java duration. */
  public static Duration fromProtoDuration(com.google.protobuf.Duration protoDuration) {
    protoDuration = normalizedDuration(protoDuration.getSeconds(), protoDuration.getNanos());
    return Duration.ofSeconds(protoDuration.getSeconds(), protoDuration.getNanos());
  }

  /** Converts java duration to proto duration. */
  public static com.google.protobuf.Duration toProtoDuration(Duration duration) {
    return normalizedDuration(duration.getSeconds(), duration.getNano());
  }

  private static com.google.protobuf.Duration normalizedDuration(long seconds, int nanos) {
    if (nanos <= -NANOS_PER_SECOND || nanos >= NANOS_PER_SECOND) {
      seconds = checkedAdd(seconds, nanos / NANOS_PER_SECOND);
      nanos %= NANOS_PER_SECOND;
    }
    if (seconds > 0 && nanos < 0) {
      nanos += NANOS_PER_SECOND; // no overflow since nanos is negative (and we're adding)
      seconds--; // no overflow since seconds is positive (and we're decrementing)
    }
    if (seconds < 0 && nanos > 0) {
      nanos -= NANOS_PER_SECOND; // no overflow since nanos is positive (and we're subtracting)
      seconds++; // no overflow since seconds is negative (and we're incrementing)
    }
    com.google.protobuf.Duration duration =
        com.google.protobuf.Duration.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    return Durations.checkValid(duration);
  }
}
