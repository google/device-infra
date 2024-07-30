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

import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.shared.context.InvocationContext;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationInfo;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationType;
import com.google.devtools.mobileharness.shared.util.command.linecallback.CommandOutputLogger;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import javax.annotation.Nullable;

/** Standard log formatter of Mobile Harness code. */
public final class MobileHarnessLogFormatter {

  /**
   * Log messages from these classes will be printed directly without formatting.
   *
   * <p>Usually for printing logs from a sub process.
   */
  private static final ImmutableSet<String> DIRECT_MODE_SOURCE_CLASS_NAMES =
      ImmutableSet.of(CommandOutputLogger.class.getName());

  private static final ZoneId ZONE_ID = ZoneId.of("America/Los_Angeles");
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS z").withZone(ZONE_ID);
  private static final DateTimeFormatter SIMPLIFIED_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("MM-dd HH:mm:ss z").withZone(ZONE_ID);

  private static class DefaultFormatter extends Formatter {

    private final boolean simplified;

    private DefaultFormatter(boolean simplified) {
      this.simplified = simplified;
    }

    @Override
    public String format(LogRecord logRecord) {
      if (DIRECT_MODE_SOURCE_CLASS_NAMES.contains(logRecord.getSourceClassName())) {
        return String.format(
            "%s\n%s", logRecord.getMessage(), printThrowable(logRecord.getThrown()));
      } else if (simplified) {
        return String.format(
            "%s %s/%s: %s%s\n%s",
            SIMPLIFIED_DATE_TIME_FORMATTER.format(logRecord.getInstant()),
            logRecord.getLevel().toString().charAt(0),
            getLoggerSimpleName(logRecord.getLoggerName()),
            logRecord.getMessage(),
            getContext(),
            printThrowable(logRecord.getThrown()));
      } else {
        return String.format(
            "%s %s %s [%s] %s%s\n%s",
            DATE_TIME_FORMATTER.format(logRecord.getInstant()),
            logRecord.getLevel().toString().charAt(0),
            logRecord.getLoggerName(),
            logRecord.getSourceMethodName(),
            logRecord.getMessage(),
            getContext(),
            printThrowable(logRecord.getThrown()));
      }
    }

    private static String getLoggerSimpleName(@Nullable String loggerName) {
      String name = nullToEmpty(loggerName);
      return name.substring(name.lastIndexOf('.') + 1);
    }

    private static String getContext() {
      Map<InvocationType, InvocationInfo> context = InvocationContext.getCurrentContext();
      return context.isEmpty() ? "" : " " + context;
    }
  }

  private static final Formatter FORMATTER = new DefaultFormatter(/* simplified= */ false);
  private static final Formatter SIMPLIFIED_FORMATTER =
      new DefaultFormatter(/* simplified= */ true);

  /** Returns the default formatter for printing the date time in the log. */
  public static DateTimeFormatter getDateTimeFormatter() {
    return DATE_TIME_FORMATTER;
  }

  /** Returns the default log formatter. */
  public static Formatter getDefaultFormatter() {
    return Flags.instance().simplifiedLogFormat.getNonNull() ? SIMPLIFIED_FORMATTER : FORMATTER;
  }

  private static String printThrowable(@Nullable Throwable e) {
    return e == null ? "" : Throwables.getStackTraceAsString(e);
  }

  private MobileHarnessLogFormatter() {}
}
