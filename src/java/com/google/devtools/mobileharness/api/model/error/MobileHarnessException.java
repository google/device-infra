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
import com.google.devtools.deviceinfra.api.error.DeviceInfraException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import javax.annotation.Nullable;

/** Base class of all Mobile Harness exceptions. */
public class MobileHarnessException
    extends com.google.wireless.qa.mobileharness.shared.MobileHarnessException
    implements ErrorIdProvider<ErrorId> {

  private final ErrorId errorId;

  public MobileHarnessException(ErrorId errorId, String message) {
    this(errorId, message, /* cause= */ null);
  }

  public MobileHarnessException(ErrorId errorId, String message, @Nullable Throwable cause) {
    this(errorId, message, cause, !message.endsWith(getMessageSuffix(errorId)));
  }

  public MobileHarnessException(
      ErrorId errorId, String message, @Nullable Throwable cause, boolean addErrorIdToMessage) {
    super(
        ErrorCode.NEXT_GEN_ERROR,
        ErrorType.UNCLASSIFIED_ERROR,
        addErrorIdToMessage ? message + getMessageSuffix(errorId) : message,
        cause,
        false /* don't add cause to message */);
    this.errorId = errorId;
  }

  @Override
  public ErrorId getErrorId() {
    return errorId;
  }

  public static <T> T checkNotNull(T reference, ErrorId errorId, @Nullable Object message)
      throws MobileHarnessException {
    if (reference == null) {
      throw new MobileHarnessException(errorId, String.valueOf(message));
    }
    return reference;
  }

  /**
   * {@inheritDoc}
   *
   * @deprecated Please use {@link #getErrorId()}.code()
   */
  @Override
  @Deprecated
  public int getErrorCode() {
    return errorId.code();
  }

  /**
   * {@inheritDoc}
   *
   * <p>@deprecated Please use {@link #getErrorId()}.name()
   */
  @Override
  @Deprecated
  public String getErrorName() {
    return errorId.name();
  }

  /**
   * {@inheritDoc}
   *
   * <p>@deprecated Please use {@link #getErrorId()}.type()
   */
  @Override
  @Deprecated
  public ErrorType getErrorType() {
    switch (errorId.type()) {
      case INFRA_ISSUE:
      case DEPENDENCY_ISSUE:
        return ErrorType.INFRA_ERROR;
      case CUSTOMER_ISSUE:
        return ErrorType.USERS_FAILURE;
      case UNCLASSIFIED:
      case UNDETERMINED:
      case UNRECOGNIZED:
        break;
    }
    return ErrorType.UNCLASSIFIED_ERROR;
  }

  /** Converts an old {@link MobileHarnessException} to a new {@link DeviceInfraException}. */
  public DeviceInfraException toNewException() {
    DeviceInfraException result =
        new DeviceInfraException(getErrorId(), getMessage(), /* addErrorIdToMessage= */ false);
    result.setStackTrace(getStackTrace());
    Throwable cause = getCause();
    if (cause != null) {
      result.initCause(toNewExceptionIfPossible(cause));
    }
    for (Throwable suppressed : getSuppressed()) {
      result.addSuppressed(toNewExceptionIfPossible(suppressed));
    }
    return result;
  }

  private static Throwable toNewExceptionIfPossible(Throwable exception) {
    return exception instanceof MobileHarnessException
        ? ((MobileHarnessException) exception).toNewException()
        : exception;
  }

  private static String getMessageSuffix(ErrorId errorId) {
    return " " + errorId;
  }
}
