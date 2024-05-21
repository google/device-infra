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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.TimeZone;

/** Contains time related utility methods. */
public class TimeUtil {

  // only static methods, don't allow construction
  private TimeUtil() {}

  /** Gets a prettified version of the given elapsed time in milliseconds. */
  public static String formatElapsedTime(long elapsedTimeMs) {
    if (elapsedTimeMs < 1000) {
      return String.format("%d ms", elapsedTimeMs);
    }
    long seconds = Duration.ofMillis(elapsedTimeMs).getSeconds() % 60;
    long minutes = Duration.ofMillis(elapsedTimeMs).toMinutes() % 60;
    long hours = Duration.ofMillis(elapsedTimeMs).toHours();
    StringBuilder time = new StringBuilder();
    if (hours > 0) {
      time.append(hours);
      time.append("h ");
    }
    if (minutes > 0) {
      time.append(minutes);
      time.append("m ");
    }
    time.append(seconds);
    time.append("s");

    return time.toString();
  }

  /**
   * Gets a readable formatted version of the given epoch time.
   *
   * @param epochTime the epoch time in milliseconds
   * @return a user readable string
   */
  public static String formatTimeStamp(long epochTime) {
    return formatTimeStamp(epochTime, null);
  }

  /**
   * Internal helper to print a time with the given date format, or a default format if none is
   * provided.
   */
  private static String formatTimeStamp(long epochTime, SimpleDateFormat format) {
    if (format == null) {
      format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }
    return format.format(new Date(epochTime));
  }

  /**
   * Gets a readable formatted version of the given epoch time in GMT time instead of the local
   * timezone.
   *
   * @param epochTime the epoch time in milliseconds
   * @return a user readable string
   */
  public static String formatTimeStampGmt(long epochTime) {
    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    return formatTimeStamp(epochTime, timeFormat);
  }
}
