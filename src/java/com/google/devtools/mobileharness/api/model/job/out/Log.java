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

package com.google.devtools.mobileharness.api.model.job.out;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.devtools.mobileharness.api.model.job.out.Log.Api;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.log.LogCollectorBackend;
import com.google.wireless.qa.mobileharness.shared.log.LogContext;
import com.google.wireless.qa.mobileharness.shared.log.LogData;
import com.google.wireless.qa.mobileharness.shared.log.LoggingApi;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;

/** Output log of the job/test. */
public class Log implements LogCollector<Api> {

  /**
   * @see LoggingApi
   */
  public interface Api extends LoggingApi<Api> {}

  private class LogCollectorBackendImpl implements LogCollectorBackend<LogData> {

    @Override
    public void log(LogData data) {
      // Example: I xx:xx:xx Failed to read file: IOException: This is the error message
      String message = data.getFormattedMessage();
      Optional<Throwable> cause = data.getCause();
      append(
          DATE_FORMAT.format(timing.getClock().instant())
              + " "
              + data.getLevel().toString().charAt(0)
              + " "
              + message
              + (!message.isEmpty() && cause.isPresent() ? ": " : "")
              + cause
                  .map(
                      c ->
                          data.getWithCauseStack()
                              ? Throwables.getStackTraceAsString(c)
                              : (c instanceof MobileHarnessException
                                  ? ((MobileHarnessException) c).getErrorName()
                                      + ": "
                                      + c.getMessage()
                                  : c.getClass().getSimpleName()
                                      + (c.getMessage() == null ? "" : ": " + c.getMessage())))
                  .orElse("")
              + "\n");
    }
  }

  private class LoggingApiImpl extends LogContext<Api, LogData> implements Api {

    private LoggingApiImpl(Level level) {
      super(level);
    }

    @Override
    protected LogCollectorBackend<LogData> getBackend() {
      return backend;
    }

    @CanIgnoreReturnValue
    @Override
    protected Api api() {
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    protected LogData data() {
      return this;
    }
  }

  /** Date time format of log message time stamp. Timezone will be configured to MTV timezone. */
  @VisibleForTesting
  static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS z")
          .withZone(ZoneId.of("America/Los_Angeles"));

  /** Output logs will be appended to this buffer while running this job/test. */
  @GuardedBy("this")
  private final StringBuilder buffer = new StringBuilder();

  /** The time records of the job/test. */
  private final TouchableTiming timing;

  private final LogCollectorBackend<LogData> backend = new LogCollectorBackendImpl();

  /** Creates the output log segment of a job/test. */
  public Log(TouchableTiming timing) {
    this.timing = timing;
  }

  /** Appends new log. */
  @VisibleForTesting
  synchronized void append(String message) {
    buffer.append(message);
    timing.touch();
  }

  /** Appends new logs. Also log with the given logger. */
  @VisibleForTesting
  void append(String message, Logger logger) {
    append(message);
    logger.info(message);
  }

  /**
   * Gets the log from the given offset to the end of the log buffer. If the offset is larger than
   * the current log length, an empty string is returned.
   */
  public synchronized String get(int offset) {
    if (offset >= buffer.length()) {
      return "";
    }
    return buffer.substring(offset);
  }

  /**
   * Attempts to reduce storage used for the character sequence of the log. If the buffer is larger
   * than necessary to hold its current sequence of characters, then it may be resized to become
   * more space efficient.
   */
  public synchronized void shrink() {
    buffer.trimToSize();
  }

  @Override
  public Api at(Level level) {
    return new LoggingApiImpl(level);
  }

  public LogCollectorBackend<LogData> getLogCollectorBackend() {
    return backend;
  }
}
