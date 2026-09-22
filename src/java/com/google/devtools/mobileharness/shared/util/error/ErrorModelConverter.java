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

import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.model.error.UnknownErrorId;
import java.util.Objects;

/** For converting between new and old data models of exceptions/errors. */
public class ErrorModelConverter {

  private ErrorModelConverter() {}

  public static MobileHarnessException toMobileHarnessException(
      ExceptionProto.ExceptionDetail detail) {
    ErrorId errorId = getErrorId(detail);
    String errorMessage = getErrorMessage(errorId, detail.getSummary());
    MobileHarnessException cause = null;
    if (detail.hasCause()) {
      cause = toMobileHarnessException(detail.getCause());
    }
    MobileHarnessException result = new MobileHarnessException(errorId, errorMessage, cause);
    detail
        .getSummary()
        .getMetadataList()
        .forEach(metadata -> result.addMetadata(metadata.getKey(), metadata.getValue()));
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
