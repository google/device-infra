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

import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.ErrorIdProvider;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionClassType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.StackTrace;
import java.util.Arrays;
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
    return toStackTraceProto(Arrays.stream(throwable.getStackTrace()));
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

  private ErrorModelConverter() {}
}
