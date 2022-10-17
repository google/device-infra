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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Utility class for common date/time operations. */
public final class TimeUtil {
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yy/MM/dd HH:mm:ss:SSS z");

  public static final DateTimeFormatter SHORT_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss:SSS z");

  /** Converts the given time into a readable local date time string. */
  public static String toLocalDate(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).format(DATE_FORMAT);
  }

  /** Gets the readable local data time string of the current time. */
  public static String currentLocalDate() {
    return toLocalDate(Clock.systemUTC().instant());
  }

  /** Converts the given time into a readable local short date time string. */
  public static String toShortLocalDate(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).format(SHORT_TIME_FORMATTER);
  }

  /** Gets the readable local data time string of the current time. */
  public static String currentShortLocalDate() {
    return toShortLocalDate(Clock.systemUTC().instant());
  }

  private TimeUtil() {}
}
