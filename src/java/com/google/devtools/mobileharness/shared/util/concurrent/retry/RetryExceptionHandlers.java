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

package com.google.devtools.mobileharness.shared.util.concurrent.retry;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.flogger.FluentLogger;
import java.time.Duration;
import java.util.logging.Level;

/** Default {@link RetryExceptionHandler} implementations. */
public final class RetryExceptionHandlers {

  private static final ExceptionSwallower EXCEPTION_SWALLOWER = new ExceptionSwallower();

  /** Returns a handler that simply swallows the exception without doing anything. */
  public static RetryExceptionHandler<Throwable> swallowException() {
    return EXCEPTION_SWALLOWER;
  }

  /** Returns a handler that logs the caught exception at {@code level}. */
  public static RetryExceptionHandler<Throwable> logException(Level level) {
    return new ExceptionLogger(level);
  }

  private static final class ExceptionSwallower implements RetryExceptionHandler<Throwable> {

    @Override
    public void aboutToRetry(Throwable e, int attemptsSoFar, Duration timeBeforeRetry) {}

    @Override
    public void interrupted(InterruptedException e, int attemptsSoFar) {}
  }

  private static final class ExceptionLogger implements RetryExceptionHandler<Throwable> {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final Level level;

    ExceptionLogger(Level level) {
      this.level = checkNotNull(level);
    }

    @Override
    public void aboutToRetry(Throwable e, int attemptsSoFar, Duration timeBeforeRetry) {
      logger.at(level).withCause(e).log(
          "Exception #%s. Retrying after %s delay.", attemptsSoFar, timeBeforeRetry);
    }

    @Override
    public void interrupted(InterruptedException e, int attemptsSoFar) {
      logger.at(level).withCause(e).log("Interrupted after %s attempts.", attemptsSoFar);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof ExceptionLogger) {
        ExceptionLogger o = (ExceptionLogger) obj;
        return level.equals(o.level);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return level.hashCode();
    }

    @Override
    public String toString() {
      return String.format("ExceptionLogger{%s}", level);
    }
  }

  private RetryExceptionHandlers() {}
}
