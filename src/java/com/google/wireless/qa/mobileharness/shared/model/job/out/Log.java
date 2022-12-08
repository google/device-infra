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

package com.google.wireless.qa.mobileharness.shared.model.job.out;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.log.LogCollectorBackend;
import com.google.wireless.qa.mobileharness.shared.log.LogContext;
import com.google.wireless.qa.mobileharness.shared.log.LogData;
import com.google.wireless.qa.mobileharness.shared.log.LoggingApi;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Output log of the job/test. */
public class Log implements LogCollector<Api> {

  /** See {@link LoggingApi}. */
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
              + cause.map(c -> formatCause(c, data.getWithCauseStack())).orElse("")
              + "\n");
    }

    private String formatCause(Throwable cause, boolean withCauseStack) {
      if (withCauseStack) {
        return Throwables.getStackTraceAsString(cause);
      } else if (cause
          instanceof com.google.devtools.mobileharness.api.model.error.MobileHarnessException) {
        return cause.getMessage();
      } else if (cause instanceof MobileHarnessException) {
        return ((MobileHarnessException) cause).getErrorCodeEnum() + ": " + cause.getMessage();
      } else {
        return cause.getClass().getSimpleName()
            + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
      }
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
          .withZone(TimeZone.getTimeZone("America/Los_Angeles").toZoneId());

  /** Output logs will be appended to this buffer while running this job/test. */
  private final StringBuffer buffer = new StringBuffer();

  /** The time records of the job/test. */
  private final Timing timing;

  private final LogCollectorBackend<LogData> backend = new LogCollectorBackendImpl();

  /** Creates the output log segment of a job/test. */
  public Log(Timing timing) {
    this.timing = timing;
  }

  /** Appends new log. */
  @CanIgnoreReturnValue
  public Log append(String message) {
    buffer.append(message);
    timing.touch();
    return this;
  }

  /** Appends new logs. Also log with the given logger. */
  @CanIgnoreReturnValue
  public Log append(@CompileTimeConstant String message, FluentLogger logger) {
    append(message);
    logger.atInfo().log("%s", message);
    return this;
  }

  /**
   * Appends new log. Will append '\n' at the end of the message.
   *
   * @deprecated use {@link #atInfo()}{@linkplain LoggingApi#log(String) .log(String)} instead
   */
  @CanIgnoreReturnValue
  @Deprecated
  public Log ln(@CompileTimeConstant String message) {
    atInfo().log(message);
    return this;
  }

  /**
   * Appends new logs. Will append '\n' at the end of the message. Also log with the given logger.
   *
   * @deprecated use {@link #atInfo()}{@linkplain LoggingApi#alsoTo(Logger)
   *     .alsoTo(Logger)}{@linkplain LoggingApi#log(String) .log(String)} instead
   */
  @CanIgnoreReturnValue
  @Deprecated
  public Log ln(@CompileTimeConstant String message, @Nullable FluentLogger logger) {
    atInfo().alsoTo(logger).log(message);
    return this;
  }

  /**
   * Gets the log from the given offset to the end of the log buffer. If the offset is larger than
   * the current log length, an empty string is returned.
   */
  public String get(int offset) {
    if (offset >= buffer.length()) {
      return "";
    }
    return buffer.substring(offset);
  }

  /** Returns the current length of the log. */
  public int size() {
    return buffer.length();
  }

  /**
   * This method is for MH infra internal use only.
   *
   * <p>Attempts to reduce storage used for the character sequence of the log. If the buffer is
   * larger than necessary to hold its current sequence of characters, then it may be resized to
   * become more space efficient.
   *
   * <p>Only call this method after the log is finalized and do <b>NOT</b> add more logs after
   * calling this method.
   */
  @CanIgnoreReturnValue
  public Log shrink() {
    buffer.trimToSize();
    return this;
  }

  @CheckReturnValue
  @Override
  public Api at(Level level) {
    return new LoggingApiImpl(level);
  }

  public LogCollectorBackend<LogData> getLogCollectorBackend() {
    return backend;
  }
}
