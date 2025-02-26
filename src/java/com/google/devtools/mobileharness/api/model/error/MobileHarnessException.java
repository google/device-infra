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
public class MobileHarnessException
    extends com.google.wireless.qa.mobileharness.shared.MobileHarnessException
    implements ErrorIdProvider<ErrorId> {

  private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];

  private final ErrorId errorId;

  public MobileHarnessException(ErrorId errorId, String message) {
    this(errorId, message, /* cause= */ null);
  }

  public MobileHarnessException(ErrorId errorId, String message, @Nullable Throwable cause) {
    this(
        errorId,
        message,
        cause,
        !message.endsWith(getMessageSuffix(errorId)),
        /* clearStackTrace= */ false);
  }

  /** Do NOT make it public. */
  MobileHarnessException(
      ErrorId errorId,
      String message,
      @Nullable Throwable cause,
      boolean addErrorIdToMessage,
      boolean clearStackTrace) {
    super(addErrorIdToMessage ? message + getMessageSuffix(errorId) : message, cause);
    this.errorId = errorId;
    if (clearStackTrace) {
      setStackTrace(EMPTY_STACK_TRACE);
    }
  }

  @Override
  public ErrorId getErrorId() {
    return errorId;
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
