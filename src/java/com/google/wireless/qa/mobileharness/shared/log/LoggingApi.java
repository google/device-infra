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

import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Mobile Harness logging API for logging a message in the {@linkplain LogCollector log collector}.
 *
 * <p>The cause of the message and extra loggers can be specified by {@link #withCause} and {@link
 * #alsoTo} in a call chain. Each chain should be terminated by {@link #log(String)}.
 *
 * @see LogCollector
 */
@CheckReturnValue
public interface LoggingApi<API extends LoggingApi<API>> {

  /**
   * Specifies the cause of the message.
   *
   * <p>If the cause is {@code null} then this method has no effect.
   *
   * <p>If this method is called multiple times, the last invocation will take precedence.
   */
  API withCause(@Nullable Throwable cause);

  /**
   * Specifies that the stack trace of the {@linkplain #withCause(Throwable) cause} will also be
   * logged.
   *
   * <p>If there is no cause finally this method has no effect.
   */
  API withCauseStack();

  /**
   * Specifies the extra {@link Logger} to which the message will also be logged.
   *
   * <p>If the logger is {@code null} then this method has no effect.
   *
   * <p>If this method is called multiple times, the last invocation will take precedence.
   */
  API alsoTo(@Nullable Logger logger);

  /**
   * Specifies the extra {@link FluentLogger} to which the message will also be logged.
   *
   * <p>If the logger is {@code null} then this method has no effect.
   *
   * <p>If this method is called multiple times, the last invocation will take precedence.
   */
  API alsoTo(@Nullable FluentLogger logger);

  /**
   * Specifies whether the message will be logged to the log collector itself. By default, it is
   * {@code true}.
   *
   * <p>If {@code false}, the message will only be logged to the extra Google logger (if any).
   *
   * <p>This method can avoid some {@code if} statements. For example,
   *
   * <pre>{@code if (someCondition) {
   *   testInfo.log().atInfo().alsoTo(logger).log("Foo");
   * } else {
   *   logger.atInfo().log("Foo");
   * }}</pre>
   *
   * can be changed to
   *
   * <pre>{@code testInfo.log().atInfo().alsoTo(logger).toLogCollector(someCondition).log("Foo");}
   * </pre>
   *
   * <p>If this method is called multiple times, the last invocation will take precedence.
   */
  API toLogCollector(boolean toLogCollector);

  /**
   * Logs a message.
   *
   * <p>NOTE: Use {@link #withCause(Throwable)} rather than containing the error in the message. For
   * example: <code>logCollector.atWarning().withCause(e).log("Failed to read file");</code>
   *
   * <p>This method should be the last operation on the API.
   */
  void log(@CompileTimeConstant @Nullable String message);

  /**
   * Logs a message with arguments.
   *
   * <p>NOTE: Use {@link #withCause(Throwable)} rather than containing the error in the message. For
   * example: <code>logCollector.atWarning().withCause(e).log("Failed to read file %s", fileName);
   * </code>
   *
   * <p>This method should be the last operation on the API.
   *
   * @throws NullPointerException if the message is {@code null}
   */
  @FormatMethod
  void log(@FormatString String message, Object... args);
}
