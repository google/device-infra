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

package com.google.devtools.mobileharness.shared.util.error;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Throwables;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionSummary;
import com.google.devtools.mobileharness.shared.model.error.UnknownErrorId;
import com.google.wireless.qa.mobileharness.shared.proto.Common.ErrorInfo;
import java.util.Objects;
import javax.annotation.Nullable;

/** For converting between new and old data models of exceptions/errors. */
public class ErrorModelConverter {

  private ErrorModelConverter() {}

  /**
   * Converts the ErrorId proto from the MH version to the devtools/common/metrics/stability
   * version. Will always set the namespace to MH.
   */
  public static ErrorIdProto.ErrorId toCommonErrorId(ExceptionSummary mhExceptionSummary) {
    return ErrorIdProto.ErrorId.newBuilder()
        .setCode(mhExceptionSummary.getErrorCode())
        .setName(mhExceptionSummary.getErrorName())
        .setType(mhExceptionSummary.getErrorType())
        .setNamespace(Namespace.MH)
        .build();
  }

  public static MobileHarnessException toMobileHarnessException(
      ExceptionProto.ExceptionDetail detail) {
    ErrorId errorId = getErrorId(detail);
    String errorMessage = getErrorMessage(errorId, detail.getSummary());
    MobileHarnessException cause = null;
    if (detail.hasCause()) {
      cause = toMobileHarnessException(detail.getCause());
    }
    MobileHarnessException result = new MobileHarnessException(errorId, errorMessage, cause);
    result.setStackTrace(getStackTrace(detail.getSummary()));
    detail
        .getSuppressedList()
        .forEach(suppressed -> result.addSuppressed(toMobileHarnessException(suppressed)));
    return result;
  }

  private static ErrorId getErrorId(ExceptionProto.ExceptionDetail detail) {
    ErrorId errorId;
    if (detail.hasSummary()) {
      ExceptionProto.ExceptionSummary summary = detail.getSummary();
      errorId =
          UnknownErrorId.of(
              summary.getErrorId().getCode(),
              summary.getErrorId().getName(),
              summary.getErrorId().getType());
    } else {
      errorId = BasicErrorId.NON_MH_EXCEPTION;
    }
    return errorId;
  }

  /**
   * Returns the ErrorId of the MobileHarnessException if the given Throwable is a MobileHarness and
   * ignores the given ErrorId. If the given Throwable is not a MobileHarnessException, uses the
   * given ErrorId if not null, or use {@link BasicErrorId#NON_MH_EXCEPTION} if null.
   */
  static ErrorId finalizeErrorId(Throwable throwable, @Nullable ErrorId errorId) {
    if (throwable instanceof MobileHarnessException) {
      MobileHarnessException mhException = (MobileHarnessException) throwable;
      errorId = mhException.getErrorId();
    } else if (errorId == null) {
      errorId = BasicErrorId.NON_MH_EXCEPTION;
    }
    return errorId;
  }

  private static String getCompleteStackTrace(ExceptionProto.ExceptionDetail detail) {
    return Throwables.getStackTraceAsString(
        com.google.devtools.common.metrics.stability.converter.ErrorModelConverter
            .toDeserializedException(detail));
  }

  public static ErrorInfo toLegacyErrorInfo(ExceptionProto.ExceptionDetail detail) {
    ExceptionProto.ExceptionSummary summary = detail.getSummary();
    ErrorInfo.Builder errorInfo = ErrorInfo.newBuilder();
    if (summary.getErrorId().getCode() != 0) {
      errorInfo.setCode(summary.getErrorId().getCode());
    }
    if (!summary.getErrorId().getName().isEmpty()) {
      errorInfo.setName(summary.getErrorId().getName());
    }
    if (!summary.getMessage().isEmpty()) {
      errorInfo.setMessage(summary.getMessage());
    }
    return errorInfo
        .setType(summary.getErrorId().getType())
        .setNamespace(summary.getErrorId().getNamespace())
        .setStackTrace(getCompleteStackTrace(detail))
        .build();
  }

  private static StackTraceElement[] getStackTrace(ExceptionProto.ExceptionSummary summary) {
    return summary.getStackTrace().getElementList().stream()
        .map(
            stackTraceElement ->
                new StackTraceElement(
                    stackTraceElement.getClassName(),
                    stackTraceElement.getMethodName(),
                    stackTraceElement.getFileName(),
                    stackTraceElement.getLineNumber()))
        .collect(toImmutableList())
        .toArray(new StackTraceElement[summary.getStackTrace().getElementCount()]);
  }

  private static String getErrorMessage(
      ErrorId errorId, ExceptionProto.ExceptionSummary exceptionSummary) {
    return Objects.equals(errorId, BasicErrorId.NON_MH_EXCEPTION)
        ? exceptionSummary.getMessage()
            + " ("
            + exceptionSummary.getClassType().getClassName()
            + ")"
        : exceptionSummary.getMessage();
  }
}
