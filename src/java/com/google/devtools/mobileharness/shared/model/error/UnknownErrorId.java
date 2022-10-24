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

package com.google.devtools.mobileharness.shared.model.error;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.util.ErrorIdFormatter;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.UserErrorId;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import java.util.Objects;
import java.util.Optional;

/** For looking up ErrorId from given error code/name/type. */
public class UnknownErrorId implements ErrorId {
  private final int code;
  private final String name;
  private final ErrorType type;

  @VisibleForTesting
  UnknownErrorId(int code, String name, ErrorType type) {
    this.code = code;
    this.name = name;
    this.type = type;
  }

  @Override
  public int code() {
    return code;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public ErrorType type() {
    return type;
  }

  @Override
  public String toString() {
    return ErrorIdFormatter.formatErrorId(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !(o instanceof ErrorId)) {
      return false;
    }
    ErrorId that = (ErrorId) o;
    return code == that.code() && Objects.equals(name, that.name()) && type == that.type();
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, name, type);
  }

  /**
   * Looks up an existing {@link BasicErrorId}, {@link InfraErrorId}, {@link ExtErrorId}, {@link
   * AndroidErrorId}, and {@link UserErrorId} enum type according to the given code, name and type.
   * Otherwise, returns an {@link UnknownErrorId} instance.
   */
  public static ErrorId of(int code, String name, ErrorType type) {
    Optional<ErrorId> errorId = Optional.empty();
    if (BasicErrorId.MIN_CODE <= code && code <= BasicErrorId.MAX_CODE) {
      errorId = findMatchedErrorId(code, name, type, BasicErrorId.values());
    } else if (InfraErrorId.MIN_CODE <= code && code <= InfraErrorId.MAX_CODE) {
      errorId = findMatchedErrorId(code, name, type, InfraErrorId.values());
    } else if (ExtErrorId.MIN_CODE <= code && code <= ExtErrorId.MAX_CODE) {
      errorId = findMatchedErrorId(code, name, type, ExtErrorId.values());
    } else if (AndroidErrorId.MIN_CODE <= code && code <= AndroidErrorId.MAX_CODE) {
      errorId = findMatchedErrorId(code, name, type, AndroidErrorId.values());
    } else if (UserErrorId.MIN_CODE <= code && code <= UserErrorId.MAX_CODE) {
      errorId = findMatchedErrorId(code, name, type, UserErrorId.values());
    }
    return errorId.orElse(new UnknownErrorId(code, name, type));
  }

  /**
   * Looks up an existing {@link BasicErrorId}, {@link InfraErrorId}, {@link ExtErrorId} or {@link
   * AndroidErrorId} enum type according to the given code, name and type. Otherwise, returns an
   * {@link UnknownErrorId} instance.
   */
  public static ErrorId of(int code, String name, MobileHarnessException.ErrorType oldType) {
    ErrorType newType = ErrorType.UNCLASSIFIED;
    if (oldType == MobileHarnessException.ErrorType.INFRA_ERROR) {
      newType = ErrorType.INFRA_ISSUE;
    } else if (oldType == MobileHarnessException.ErrorType.USERS_FAILURE) {
      newType = ErrorType.CUSTOMER_ISSUE;
    }
    return of(code, name, newType);
  }

  /** Creates an {@link UnknownErrorId} instance for the legacy {@link ErrorCode} */
  public static ErrorId of(ErrorCode errorCode, ErrorType errorType) {
    return of(errorCode.code(), errorCode.name(), errorType);
  }

  /** Creates an {@link UnknownErrorId} instance for the legacy {@link ErrorCode} */
  public static ErrorId of(ErrorCode errorCode, MobileHarnessException.ErrorType errorType) {
    return of(errorCode.code(), errorCode.name(), errorType);
  }

  private static Optional<ErrorId> findMatchedErrorId(
      int code, String name, ErrorType type, ErrorId[] errorIds) {
    for (ErrorId errorId : errorIds) {
      if (errorId.code() == code) {
        if (errorId.name().equals(name) && errorId.type() == type) {
          return Optional.of(errorId);
        } else {
          break;
        }
      }
    }
    return Optional.empty();
  }
}
