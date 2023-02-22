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

package com.google.devtools.mobileharness.shared.util.logging;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import javax.annotation.Nullable;

/** Standard log formatter of Mobile Harness code. */
public final class MobileHarnessLogFormatter {

  private static final ImmutableSet<String> SIMPLIFIED_MODE_SOURCE_CLASS_NAMES = ImmutableSet.of();

  /** The default formatter for printing the date time in the log. */
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS z")
          .withZone(ZoneId.of("America/Los_Angeles"));

  /** The formatter for each item of logs. */
  private static final Formatter FORMATTER =
      new Formatter() {

        @Override
        public String format(LogRecord logRecord) {
          return SIMPLIFIED_MODE_SOURCE_CLASS_NAMES.contains(logRecord.getSourceClassName())
              ? String.format(
                  "%s\n%s", logRecord.getMessage(), printThrowable(logRecord.getThrown()))
              : String.format(
                  "%s %s %s [%s] %s\n%s",
                  DATE_TIME_FORMATTER.format(logRecord.getInstant()),
                  logRecord.getLevel().toString().charAt(0),
                  logRecord.getLoggerName(),
                  logRecord.getSourceMethodName(),
                  logRecord.getMessage(),
                  printThrowable(logRecord.getThrown()));
        }
      };

  /** Returns the default formatter for printing the date time in the log. */
  public static DateTimeFormatter getDateTimeFormatter() {
    return DATE_TIME_FORMATTER;
  }

  /** Returns the default log formatter. */
  public static Formatter getDefaultFormatter() {
    return FORMATTER;
  }

  private static String printThrowable(@Nullable Throwable e) {
    return e == null ? "" : Throwables.getStackTraceAsString(e);
  }

  private MobileHarnessLogFormatter() {}
}
