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

package com.google.devtools.mobileharness.api.model.error;

import com.google.devtools.common.metrics.stability.model.ErrorIdProvider;
import javax.annotation.Nullable;

/** Base class of all Mobile Harness exceptions. */
@SuppressWarnings("OverrideThrowableToString")
public class MobileHarnessException extends Exception implements ErrorIdProvider<ErrorId> {

  private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];

  private final ErrorId errorId;

  /**
   * An error message that is safe to display to external users or in environments with PII
   * restrictions.
   *
   * <p><b>WARNING:</b> This message MUST NOT contain any Personally Identifiable Information (PII),
   * user data, or Google-internal sensitive information, code names, or internal system details.
   * Treat this message as if it will be publicly visible.
   */
  @Nullable private final String externalErrorMessage;

  public MobileHarnessException(ErrorId errorId, String message) {
    this(errorId, message, /* cause= */ (Throwable) null);
  }

  public MobileHarnessException(ErrorId errorId, String message, @Nullable Throwable cause) {
    this(
        errorId,
        message,
        cause,
        !message.endsWith(getMessageSuffix(errorId)),
        /* clearStackTrace= */ false,
        /* externalErrorMessage= */ null);
  }

  /**
   * Constructs a new MobileHarnessException.
   *
   * @param externalErrorMessage An error message safe for external display. MUST NOT contain PII or
   *     Google-sensitive information. See warning on the {@link #externalErrorMessage} field.
   */
  private MobileHarnessException(
      ErrorId errorId, String message, @Nullable String externalErrorMessage) {
    this(errorId, message, /* cause= */ null, externalErrorMessage);
  }

  /**
   * Constructs a new MobileHarnessException.
   *
   * @param externalErrorMessage An error message safe for external display. MUST NOT contain PII or
   *     Google-sensitive information. See warning on the {@link #externalErrorMessage} field.
   */
  private MobileHarnessException(
      ErrorId errorId,
      String message,
      @Nullable Throwable cause,
      @Nullable String externalErrorMessage) {
    this(
        errorId,
        message,
        cause,
        !message.endsWith(getMessageSuffix(errorId)),
        /* clearStackTrace= */ false,
        externalErrorMessage);
  }

  /** Do NOT make it public. */
  MobileHarnessException(
      ErrorId errorId,
      String message,
      @Nullable Throwable cause,
      boolean addErrorIdToMessage,
      boolean clearStackTrace,
      @Nullable String externalErrorMessage) {
    super(addErrorIdToMessage ? message + getMessageSuffix(errorId) : message, cause);
    this.errorId = errorId;
    this.externalErrorMessage = externalErrorMessage;
    if (clearStackTrace) {
      setStackTrace(EMPTY_STACK_TRACE);
    }
  }

  /**
   * Creates a new MobileHarnessException with an error message safe for external display.
   *
   * <p><b>WARNING:</b> The {@code externalErrorMessage} MUST NOT contain PII or Google-sensitive
   * information. See warning on the {@link #externalErrorMessage} field.
   */
  public static MobileHarnessException createWithExternalMessage(
      ErrorId errorId, String message, @Nullable String externalErrorMessage) {
    return new MobileHarnessException(errorId, message, externalErrorMessage);
  }

  /**
   * Creates a new MobileHarnessException with an error message safe for external display.
   *
   * <p><b>WARNING:</b> The {@code externalErrorMessage} MUST NOT contain PII or Google-sensitive
   * information. See warning on the {@link #externalErrorMessage} field.
   */
  public static MobileHarnessException createWithExternalMessage(
      ErrorId errorId,
      String message,
      @Nullable Throwable cause,
      @Nullable String externalErrorMessage) {
    return new MobileHarnessException(errorId, message, cause, externalErrorMessage);
  }

  @Override
  public ErrorId getErrorId() {
    return errorId;
  }

  /**
   * Returns the error message that is safe to display to external users.
   *
   * <p><b>WARNING:</b> Ensure that the creation of this message adheres to the restrictions
   * outlined on the {@link #externalErrorMessage} field.
   */
  @Nullable
  public String getExternalErrorMessage() {
    return externalErrorMessage;
  }

  @Override
  public String toString() {
    String classSimpleName = getClass().getSimpleName();
    String message = getLocalizedMessage();
    return message == null ? classSimpleName : classSimpleName + ": " + message;
  }

  private static String getMessageSuffix(ErrorId errorId) {
    return " " + errorId;
  }
}
