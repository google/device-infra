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

package com.google.devtools.mobileharness.shared.util.time;

import static com.google.common.math.LongMath.checkedAdd;
import static com.google.common.math.LongMath.checkedSubtract;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** A utility class for time. */
public class TimeUtils {

  private static final int NANOS_PER_SECOND = 1000000000;

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yy/MM/dd HH:mm:ss:SSS z");

  private static final DateTimeFormatter SHORT_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss:SSS z");

  /** Checks if the duration is positive. */
  public static boolean isDurationPositive(Duration duration) {
    return !duration.isNegative() && !duration.isZero();
  }

  /** Converts the given time into a readable short date time string. */
  public static String toDateShortString(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).format(SHORT_DATE_TIME_FORMATTER);
  }

  /** Converts the given time into a readable date time string. */
  public static String toDateString(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
  }

  /** Converts proto duration to Java duration. */
  public static Duration toJavaDuration(com.google.protobuf.Duration protoDuration) {
    protoDuration = normalizedDuration(protoDuration.getSeconds(), protoDuration.getNanos());
    return Duration.ofSeconds(protoDuration.getSeconds(), protoDuration.getNanos());
  }

  /** Converts proto instant to Java instant. */
  public static Instant toJavaInstant(Timestamp protoTimestamp) {
    protoTimestamp = normalizedTimestamp(protoTimestamp.getSeconds(), protoTimestamp.getNanos());
    return Instant.ofEpochSecond(protoTimestamp.getSeconds(), protoTimestamp.getNanos());
  }

  /** Converts Java duration to proto duration. */
  public static com.google.protobuf.Duration toProtoDuration(Duration duration) {
    return normalizedDuration(duration.getSeconds(), duration.getNano());
  }

  /** Converts Java instant to proto timestamp. */
  public static Timestamp toProtoTimestamp(Instant instant) {
    return normalizedTimestamp(instant.getEpochSecond(), instant.getNano());
  }

  /** Formats a (positive) duration to a string like "1h 23m 45s". */
  public static String toReadableDurationString(Duration duration) {
    if (duration.compareTo(Duration.ofSeconds(1L)) < 0) {
      return String.format("%d ms", duration.toMillis());
    }

    StringBuilder result = new StringBuilder();

    @SuppressWarnings("TimeUnitMismatch")
    long secondsPart = duration.toSecondsPart();
    long minutesPart = duration.toMinutesPart();
    long hours = duration.toHours();

    if (hours > 0) {
      result.append(hours);
      result.append("h ");
    }
    if (minutesPart > 0) {
      result.append(minutesPart);
      result.append("m ");
    }
    result.append(secondsPart);
    result.append("s");

    return result.toString();
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

  private static Timestamp normalizedTimestamp(long seconds, int nanos) {
    if (nanos <= -NANOS_PER_SECOND || nanos >= NANOS_PER_SECOND) {
      seconds = checkedAdd(seconds, nanos / NANOS_PER_SECOND);
      nanos = nanos % NANOS_PER_SECOND;
    }
    if (nanos < 0) {
      nanos = nanos + NANOS_PER_SECOND; // no overflow since nanos is negative (and we're adding)
      seconds = checkedSubtract(seconds, 1);
    }
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    return Timestamps.checkValid(timestamp);
  }

  private TimeUtils() {}
}
