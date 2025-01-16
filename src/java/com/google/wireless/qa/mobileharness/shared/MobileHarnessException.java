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

package com.google.wireless.qa.mobileharness.shared;

import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;

/**
 * Deprecated. Use {@link com.google.devtools.mobileharness.api.model.error.MobileHarnessException}
 * or {@link com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions} instead.
 */
public class MobileHarnessException extends Exception {

  /**
   * Deprecated. Use {@link com.google.devtools.mobileharness.api.model.proto.Error.ErrorType}
   * instead.
   */
  @Deprecated
  public enum ErrorType {
    // Default type, job/test result is very likely ERROR.
    UNCLASSIFIED_ERROR,
    // Errors caused by MH infrastructure, job/test result is very likely INFRA_ERROR.
    INFRA_ERROR,
    // Errors caused by users, job/test result is very likely FAILED.
    USERS_FAILURE;
  }

  private final ErrorCode errorCode;

  private volatile ErrorType errorType;

  /**
   * @deprecated Please create a new {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException} instead.
   */
  @Deprecated
  public MobileHarnessException(ErrorCode errorCode, ErrorType errorType, String message) {
    super(message);
    this.errorCode = errorCode;
    this.errorType = errorType;
  }

  /**
   * @deprecated Please create a new {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException} instead.
   */
  @Deprecated
  public MobileHarnessException(ErrorCode errorCode, String message) {
    this(errorCode, ErrorType.UNCLASSIFIED_ERROR, message);
  }

  /**
   * @deprecated Please create a new {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException} instead.
   */
  @Deprecated
  public MobileHarnessException(int errorCode, String message) {
    this(ErrorCode.enumOf(errorCode), message);
  }

  /**
   * @deprecated Please create a new {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException} instead.
   */
  @Deprecated
  public MobileHarnessException(
      ErrorCode errorCode, ErrorType errorType, String errorDetail, Throwable cause) {
    this(errorCode, errorType, errorDetail, cause, /* addCauseToMessage= */ true);
  }

  /**
   * @deprecated Please create a new {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException} instead.
   */
  @Deprecated
  public MobileHarnessException(
      ErrorCode errorCode,
      ErrorType errorType,
      String errorDetail,
      Throwable cause,
      boolean addCauseToMessage) {
    super(
        errorDetail
            + (cause == null || !addCauseToMessage
                ? ""
                : ": "
                    + cause.getClass().getSimpleName()
                    + (cause.getMessage() == null ? "" : ": " + cause.getMessage())),
        cause);
    this.errorCode = errorCode;
    this.errorType = errorType;
  }

  /**
   * @deprecated Please create a new {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException} instead.
   */
  @Deprecated
  public MobileHarnessException(ErrorCode errorCode, String errorDetail, Throwable cause) {
    this(errorCode, ErrorType.UNCLASSIFIED_ERROR, errorDetail, cause);
  }

  @SuppressWarnings("unused")
  @Deprecated
  private MobileHarnessException() {
    this(ErrorCode.UNKNOWN, "");
  }

  /**
   * @deprecated Please create a new {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException} instead.
   */
  @Deprecated
  public MobileHarnessException(String message) {
    this(ErrorCode.UNKNOWN, message);
  }

  /**
   * @deprecated Use {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException#getErrorId()#getErrorCode()}
   *     instead.
   */
  @Deprecated
  public int getErrorCode() {
    return errorCode.code();
  }

  /**
   * @deprecated Use {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException#getErrorId()#getErrorName()}
   *     instead.
   */
  @Deprecated
  public String getErrorName() {
    return errorCode.name();
  }

  /**
   * We are migrating MobileHarnessException to the new version in
   * jcg/devtools/mobileharness/api/model/error. Will use the new ErrorId to replace the ErrorCode
   * enums. Before fully migrated, please use {@link #getErrorCode()}, {@link #getErrorName()},
   * {@link #getErrorType()} when you are still using this old MobileHarnessException, and avoid of
   * the new ErrorIds.
   */
  @Deprecated
  public ErrorCode getErrorCodeEnum() {
    return errorCode;
  }

  /**
   * @deprecated Use {@link
   *     com.google.devtools.mobileharness.api.model.error.MobileHarnessException#getErrorId()#getErrorType()}
   *     instead.
   */
  @Deprecated
  public ErrorType getErrorType() {
    return errorType;
  }
}
