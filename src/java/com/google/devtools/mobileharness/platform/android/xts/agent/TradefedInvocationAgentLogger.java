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

package com.google.devtools.mobileharness.platform.android.xts.agent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** The logger for the TradefedInvocationAgent. */
public class TradefedInvocationAgentLogger {
  private static final Map<String, Logger> configuredLoggers = new ConcurrentHashMap<>();

  private static final Formatter TRADEFED_FORMATTER = new TradefedFormatter();

  public static void init() {
    Logger rootLogger = getLoggerByName("");
    for (Handler handler : rootLogger.getHandlers()) {
      handler.setFormatter(TRADEFED_FORMATTER);
    }
  }

  private static Logger getLoggerByName(String loggerName) {
    Logger logger = Logger.getLogger(loggerName);
    configuredLoggers.put(loggerName, logger);
    return logger;
  }

  private TradefedInvocationAgentLogger() {}

  private static class TradefedFormatter extends Formatter {

    private static final DateTimeFormatter SIMPLIFIED_DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord logRecord) {
      return String.format(
          "%s %s/%s: %s\n%s",
          SIMPLIFIED_DATE_TIME_FORMATTER.format(logRecord.getInstant()),
          logRecord.getLevel().toString().charAt(0),
          getLoggerSimpleName(logRecord.getLoggerName()),
          logRecord.getMessage(),
          printThrowable(logRecord.getThrown()));
    }
  }

  private static String getLoggerSimpleName(@Nullable String loggerName) {
    String name = loggerName == null ? "" : loggerName;
    return name.substring(name.lastIndexOf('.') + 1);
  }

  private static String printThrowable(@Nullable Throwable e) {
    if (e == null) {
      return "";
    }
    StringWriter stringWriter = new StringWriter();
    e.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString();
  }
}
