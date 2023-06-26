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

package com.google.devtools.common.metrics.stability.converter;

import static com.google.common.base.Strings.nullToEmpty;
import static java.util.Arrays.stream;

import com.google.common.base.Throwables;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.ErrorIdProvider;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionClassType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.FlattenedExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.StackTrace;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Converter for converting UDCluster exception data models.
 *
 * <p>This class is only for UDCluster error handling system.
 */
public class ErrorModelConverter {
  /**
   * Converts a {@link Throwable} to a {@link ExceptionDetail}.
   *
   * <p>Note that the result proto contains full stack trace information, which does not work for
   * gRPC exception payload.
   */
  public static ExceptionDetail toExceptionDetail(Throwable throwable) {
    ExceptionDetail.Builder result = ExceptionDetail.newBuilder();
    result.setSummary(getExceptionSummary(throwable));
    if (throwable.getCause() != null) {
      result.setCause(toExceptionDetail(throwable.getCause()));
    }
    for (Throwable suppressed : throwable.getSuppressed()) {
      result.addSuppressed(toExceptionDetail(suppressed));
    }
    return result.build();
  }

  /** Converts a {@link ExceptionDetail} to a {@link DeserializedException}. */
  public static DeserializedException toDeserializedException(ExceptionDetail exceptionDetail) {
    DeserializedException result = toSingleDeserializedException(exceptionDetail.getSummary());
    if (exceptionDetail.hasCause()) {
      result.initCause(toDeserializedException(exceptionDetail.getCause()));
    } else {
      result.initCause(null);
    }
    for (ExceptionDetail suppressed : exceptionDetail.getSuppressedList()) {
      result.addSuppressed(toDeserializedException(suppressed));
    }
    return result;
  }

  /** Converts the Java ErrorId class to proto. */
  public static ErrorIdProto.ErrorId toErrorIdProto(ErrorId errorId) {
    return ErrorIdProto.ErrorId.newBuilder()
        .setCode(errorId.code())
        .setName(errorId.name())
        .setType(errorId.type())
        .setNamespace(errorId.namespace())
        .build();
  }

  private static ExceptionSummary getExceptionSummary(Throwable throwable) {
    ExceptionSummary.Builder result = ExceptionSummary.newBuilder();
    result.setErrorId(toErrorIdProto(finalizeErrorId(throwable)));
    result.setMessage(nullToEmpty(throwable.getMessage()));
    result.setClassType(getExceptionClassType(throwable));
    result.setStackTrace(getStackTrace(throwable));
    return result.build();
  }

  private static ErrorId finalizeErrorId(Throwable throwable) {
    if (throwable instanceof ErrorIdProvider) {
      return ((ErrorIdProvider<?>) throwable).getErrorId();
    } else {
      return UnknownErrorId.NOT_DEFINED;
    }
  }

  private static ExceptionClassType.Builder getExceptionClassType(Throwable throwable) {
    return ExceptionClassType.newBuilder().setClassName(throwable.getClass().getName());
  }

  private static StackTrace getStackTrace(Throwable throwable) {
    return toStackTraceProto(stream(throwable.getStackTrace()));
  }

  public static StackTrace toStackTraceProto(Stream<StackTraceElement> stackTraceElements) {
    StackTrace.Builder result = StackTrace.newBuilder();
    stackTraceElements
        .map(
            element ->
                ExceptionProto.StackTraceElement.newBuilder()
                    .setClassName(element.getClassName())
                    .setMethodName(element.getMethodName())
                    .setFileName(nullToEmpty(element.getFileName()))
                    .setLineNumber(element.getLineNumber()))
        .forEach(result::addElement);
    return result.build();
  }

  private static DeserializedException toSingleDeserializedException(
      ExceptionSummary exceptionSummary) {
    DeserializedException result =
        new DeserializedException(
            DeserializedErrorId.of(exceptionSummary.getErrorId()),
            exceptionSummary.getMessage(),
            exceptionSummary.getClassType().getClassName());
    setStackTrace(result, exceptionSummary.getStackTrace());
    return result;
  }

  private static void setStackTrace(Throwable throwable, StackTrace stackTrace) {
    StackTraceElement[] result = new StackTraceElement[stackTrace.getElementCount()];
    for (int i = 0; i < stackTrace.getElementCount(); i++) {
      ExceptionProto.StackTraceElement element = stackTrace.getElement(i);
      result[i] =
          new StackTraceElement(
              element.getClassName(),
              element.getMethodName(),
              element.getFileName(),
              element.getLineNumber());
    }
    throwable.setStackTrace(result);
  }

  /** Converts an {@link ExceptionDetail} to {@link FlattenedExceptionDetail}. */
  public static FlattenedExceptionDetail toFlattenedExceptionDetail(
      ExceptionDetail exceptionDetail) {
    ErrorIdProto.ErrorId criticalErrorId = getCriticalErrorId(exceptionDetail);
    ErrorIdProto.ErrorId userFacingCriticalErrorId =
        getUserFacingCriticalError(exceptionDetail).getSummary().getErrorId();
    ErrorIdProto.ErrorId errorId = exceptionDetail.getSummary().getErrorId();
    FlattenedExceptionDetail.Builder flattenedExceptionDetail =
        FlattenedExceptionDetail.newBuilder()
            .setSummary(exceptionDetail.getSummary())
            .setCompleteStackTrace(getCompleteStackTrace(exceptionDetail))
            .setCriticalErrorId(criticalErrorId)
            .setUserFacingCriticalErrorId(userFacingCriticalErrorId);
    StringBuilder errorNameStackTrace = new StringBuilder(errorId.getName());
    StringBuilder errorCodeStackTrace = new StringBuilder(String.valueOf(errorId.getCode()));
    ExceptionDetail cause = exceptionDetail;
    while (cause.hasCause()) {
      cause = cause.getCause();
      flattenedExceptionDetail.addCause(cause.getSummary().toBuilder().clearStackTrace());
      errorNameStackTrace.append("|").append(cause.getSummary().getErrorId().getName());
      errorCodeStackTrace.append("|").append(cause.getSummary().getErrorId().getCode());
    }

    return flattenedExceptionDetail
        .setErrorNameStackTrace(errorNameStackTrace.toString())
        .setErrorCodeStackTrace(errorCodeStackTrace.toString())
        .build();
  }

  public static String getCompleteStackTrace(ExceptionDetail detail) {
    return Throwables.getStackTraceAsString(toDeserializedException(detail));
  }

  /**
   * Returns the first non-(UNCLASSIFIED/UNDETERMINED) error type from the summary to all causes in
   * the cause chain, if any, or returns the summary's error type, as the aggregated error type of
   * an exception detail.
   */
  public static ErrorIdProto.ErrorId getCriticalErrorId(ExceptionDetail detail) {
    return getCriticalError(detail).getSummary().getErrorId();
  }

  /**
   * Returns the first non-(UNCLASSIFIED/UNDETERMINED) error from the summary to all causes in the
   * cause chain. If not exists, the return value is the same to the pass-in argument.
   */
  public static ExceptionDetail getCriticalError(ExceptionDetail detail) {
    Optional<ErrorType> aggregatedErrorType;
    ExceptionDetail current = detail;
    while (true) {
      aggregatedErrorType = getDeterminedErrorType(current.getSummary().getErrorId().getType());
      if (aggregatedErrorType.isPresent() || !current.hasCause()) {
        break;
      }
      current = current.getCause();
    }
    if (aggregatedErrorType.isEmpty()) {
      current = detail;
    }
    return current;
  }

  public static ErrorIdProto.ErrorId getUserFacingCriticalErrorId(
      ExceptionProto.ExceptionDetail detail) {
    return getUserFacingCriticalError(detail).getSummary().getErrorId();
  }

  /*
   * Returns the left-most INFRA_ISSUE or CUSTOMER_ISSUE error id, otherwise the right-most
   * non-(UNCLASSIFIED/UNDETERMINED) error id.
   */
  public static ExceptionDetail getUserFacingCriticalError(ExceptionDetail detail) {
    ExceptionDetail current = detail;
    ExceptionDetail prevDetermined = detail;
    while (true) {
      ErrorType currentType = current.getSummary().getErrorId().getType();
      if (currentType == ErrorType.INFRA_ISSUE || currentType == ErrorType.CUSTOMER_ISSUE) {
        return current;
      }
      if (!current.hasCause()) { // The tail cause
        return isDeterminedErrorType(currentType) ? current : prevDetermined;
      }
      if (isDeterminedErrorType(currentType)) { // always keep the last determined errorid
        prevDetermined = current;
      }
      current = current.getCause();
    }
  }

  /** Gets the determined error type if exists. */
  private static Optional<ErrorType> getDeterminedErrorType(ErrorType errorType) {
    return isDeterminedErrorType(errorType) ? Optional.of(errorType) : Optional.empty();
  }

  /** Whether the {@code errorType} is classfied or detemined. */
  private static boolean isDeterminedErrorType(ErrorType errorType) {
    return errorType != ErrorType.UNCLASSIFIED && errorType != ErrorType.UNDETERMINED;
  }

  /**
   * Return whether the given error is INFRA_ISSUE, or any of its cause/suppressed errors is
   * INFRA_ISSUE.
   */
  public static boolean hasInfraIssue(ExceptionProto.ExceptionDetail detail) {
    return detail.getSummary().getErrorId().getType() == ErrorType.INFRA_ISSUE
        || Stream.concat(
                detail.hasCause() ? Stream.of(detail.getCause()) : Stream.empty(),
                detail.getSuppressedList().stream())
            .anyMatch(ErrorModelConverter::hasInfraIssue);
  }

  private ErrorModelConverter() {}
}
