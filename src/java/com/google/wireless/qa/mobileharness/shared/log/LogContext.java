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

package com.google.wireless.qa.mobileharness.shared.log;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LogSites;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Base context of a log, which implements {@link LoggingApi}.
 *
 * <p>It can be used for implementing {@link LogCollector}. Implementations of {@link LogCollector}
 * can implement {@link LogCollector#at(Level)} by providing an new instance of an subclass of
 * {@link LogContext} which associates with {@link LogCollectorBackend}.
 *
 * @see LogCollector
 * @see LogCollectorBackend
 */
@CheckReturnValue
@NotThreadSafe
public abstract class LogContext<Api extends LoggingApi<Api>, Data extends LogData>
    implements LoggingApi<Api>, LogData {

  private final Level level;

  private Throwable cause;
  private String message;
  private Object[] args;
  private String formattedMessage = "";
  private boolean withCauseStack;
  private boolean toLogCollector = true;

  private Logger logger;
  private FluentLogger googleLogger;

  protected LogContext(Level level) {
    this.level = checkNotNull(level);
  }

  @Override
  public final Api withCause(@Nullable Throwable cause) {
    if (cause != null) {
      this.cause = cause;
    }
    return api();
  }

  @Override
  public final Api withCauseStack() {
    this.withCauseStack = true;
    return api();
  }

  @Override
  public final Api alsoTo(@Nullable Logger logger) {
    if (logger != null) {
      this.logger = logger;
    }
    return api();
  }

  @Override
  public final Api alsoTo(@Nullable FluentLogger logger) {
    if (logger != null) {
      googleLogger = logger;
    }
    return api();
  }

  @Override
  public final Api toLogCollector(boolean toLogCollector) {
    this.toLogCollector = toLogCollector;
    return api();
  }

  @Override
  public final void log(@CompileTimeConstant @Nullable String message) {
    this.message = message;
    args = null;
    formattedMessage = nullToEmpty(message);
    if (toLogCollector) {
      getBackend().log(data());
    }
    if (logger != null) {
      logger.log(level, message, cause);
    }
    if (googleLogger != null) {
      googleLogger
          .at(level)
          .withCause(cause)
          .withInjectedLogSite(LogSites.callerOf(LogContext.class))
          .log(message);
    }
  }

  @Override
  @FormatMethod
  public final void log(@FormatString String message, Object... args) {
    this.message = message;
    this.args = args;
    formattedMessage = formatMessage(message, args);
    if (toLogCollector) {
      getBackend().log(data());
    }
    if (logger != null) {
      logger.log(level, formattedMessage, cause);
    }
    if (googleLogger != null) {
      googleLogger
          .at(level)
          .withCause(cause)
          .withInjectedLogSite(LogSites.callerOf(LogContext.class))
          .logVarargs(message, args);
    }
  }

  @Override
  public final Level getLevel() {
    return level;
  }

  @Override
  public final Optional<Throwable> getCause() {
    return Optional.ofNullable(cause);
  }

  @Override
  public final boolean getWithCauseStack() {
    return withCauseStack;
  }

  @Override
  public final Optional<String> getMessage() {
    return Optional.ofNullable(message);
  }

  @Override
  public final Optional<Object[]> getArgs() {
    return Optional.ofNullable(args);
  }

  @Override
  public final String getFormattedMessage() {
    return formattedMessage;
  }

  /** Returns the log collector backend. */
  protected abstract LogCollectorBackend<Data> getBackend();

  /** Returns the logging API instance itself. */
  protected abstract Api api();

  /** Returns the log data instance itself. */
  protected abstract Data data();

  @FormatMethod
  private String formatMessage(@FormatString String message, Object... args) {
    try {
      return String.format(message, args);
    } catch (RuntimeException e) {
      return "Logging error, message=<"
          + message
          + ">, args="
          + Arrays.toString(args)
          + ", error="
          + e;
    }
  }
}
