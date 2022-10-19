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

import static com.google.common.base.Strings.nullToEmpty;
import static java.util.Arrays.stream;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionClassType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.StackTrace;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.StackTraceElement;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.deviceinfra.api.error.DeviceInfraException;
import com.google.devtools.deviceinfra.api.error.id.DeviceInfraErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Error;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionSummary;
import com.google.devtools.mobileharness.shared.model.error.UnknownErrorId;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** For converting between new and old data models of exceptions/errors. */
public class ErrorModelConverter {
  private ErrorModelConverter() {}

  /** Converts a new {@linkplain DeviceInfraErrorId} to an old {@link ErrorId}. */
  public static ErrorId toOldErrorId(DeviceInfraErrorId newErrorId) {
    if (newErrorId instanceof ErrorId) {
      return (ErrorId) newErrorId;
    }
    return UnknownErrorId.of(newErrorId.code(), newErrorId.name(), newErrorId.type());
  }

  /** Converts a new {@link DeviceInfraException} to an old {@link MobileHarnessException}. */
  public static MobileHarnessException toOldException(DeviceInfraException exception) {
    MobileHarnessException result =
        new MobileHarnessException(
            toOldErrorId(exception.getErrorId()),
            exception.getMessage(),
            toOldExceptionIfPossible(exception.getCause()),
            /* addErrorIdToMessage= */ false);
    result.setStackTrace(exception.getStackTrace());
    for (Throwable suppressed : exception.getSuppressed()) {
      result.addSuppressed(toOldExceptionIfPossible(suppressed));
    }
    return result;
  }

  private static Throwable toOldExceptionIfPossible(Throwable exception) {
    return exception instanceof DeviceInfraException
        ? toOldException((DeviceInfraException) exception)
        : exception;
  }

  /**
   * @deprecated This method will set the NON_MH_ERROR as the ErrorId for any
   *     non-MobileHarnessException, including the {@linkplain
   *     com.google.devtools.common.metrics.stability.converter.DeserializedException}. Use
   *     {@linkplain
   *     com.google.devtools.common.metrics.stability.converter.ErrorModelConverter#toExceptionDetail(Throwable)}
   *     instead to preserve the real ErrorId.
   */
  @Deprecated
  public static ExceptionDetail toExceptionDetail(Throwable throwable) {
    return toExceptionDetail(throwable, null);
  }

  /**
   * Ignores stack trace in the returned ExceptionDetail if {@code addStackTrace} is false.
   *
   * @deprecated This method will set the NON_MH_ERROR as the ErrorId for any
   *     non-MobileHarnessException, including the {@linkplain
   *     com.google.devtools.common.metrics.stability.converter.DeserializedException}. Use
   *     {@linkplain
   *     com.google.devtools.common.metrics.stability.converter.ErrorModelConverter#toExceptionDetail(Throwable)}
   *     instead to preserve the real ErrorId.
   */
  @Deprecated
  public static ExceptionDetail toExceptionDetail(Throwable throwable, boolean addStackTrace) {
    return toExceptionDetail(throwable, null, addStackTrace);
  }

  /**
   * @param errorId No effect for MobileHarnessException. If specified, overrides the ErrorId of the
   *     given throwable only when it is NOT a MobileHarnessException.
   * @deprecated If the errorId param is not specified, this method will set the NON_MH_ERROR as the
   *     ErrorId for any non-MobileHarnessException, including the {@linkplain
   *     com.google.devtools.common.metrics.stability.converter.DeserializedException}. Use
   *     {@linkplain
   *     com.google.devtools.common.metrics.stability.converter.ErrorModelConverter#toExceptionDetail(Throwable)}
   *     instead to preserve the real ErrorId.
   */
  @Deprecated
  public static ExceptionDetail toExceptionDetail(Throwable throwable, @Nullable ErrorId errorId) {
    return toExceptionDetail(throwable, errorId, /* addStackTrace= */ true);
  }

  /**
   * @param errorId No effect for MobileHarnessException. If specified, overrides the ErrorId of the
   *     given throwable only when it is NOT a MobileHarnessException.
   * @deprecated If the errorId param is not specified, this method will set the NON_MH_ERROR as the
   *     ErrorId for any non-MobileHarnessException, including the {@linkplain
   *     com.google.devtools.common.metrics.stability.converter.DeserializedException}. Use
   *     {@linkplain
   *     com.google.devtools.common.metrics.stability.converter.ErrorModelConverter#toExceptionDetail(Throwable)}
   *     instead to preserve the real ErrorId.
   */
  @Deprecated
  static ExceptionDetail toExceptionDetail(
      Throwable throwable, @Nullable ErrorId errorId, boolean addStackTrace) {
    errorId = finalizeErrorId(throwable, errorId);

    ExceptionSummary.Builder summary =
        ExceptionSummary.newBuilder()
            .setErrorCode(errorId.code())
            .setErrorName(errorId.name())
            .setErrorType(errorId.type())
            .setMessage(nullToEmpty(throwable.getMessage()))
            .setClassName(throwable.getClass().getName());

    if (addStackTrace) {
      stream(throwable.getStackTrace())
          .map(
              stackTraceElement ->
                  Error.StackTraceElement.newBuilder()
                      .setDeclaringClass(stackTraceElement.getClassName())
                      .setMethodName(stackTraceElement.getMethodName())
                      .setFileName(Strings.nullToEmpty(stackTraceElement.getFileName()))
                      .setLineNumber(stackTraceElement.getLineNumber()))
          .forEach(summary::addStackTrace);
    }

    ExceptionDetail.Builder detail = ExceptionDetail.newBuilder().setSummary(summary);
    if (throwable.getCause() != null) {
      detail.setCause(toExceptionDetail(throwable.getCause(), null, addStackTrace));
    }
    for (Throwable suppressed : throwable.getSuppressed()) {
      detail.addSuppressed(toExceptionDetail(suppressed, null, addStackTrace));
    }
    return detail.build();
  }

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

  /**
   * Converts the ErrorId proto from the MH version to the devtools/common/metrics/stability
   * version. Will always set the namespace to MH.
   */
  public static ExceptionProto.ExceptionSummary toCommonExceptionSummary(
      ExceptionSummary mhExceptionSummary) {
    return ExceptionProto.ExceptionSummary.newBuilder()
        .setErrorId(toCommonErrorId(mhExceptionSummary))
        .setMessage(mhExceptionSummary.getMessage())
        .setClassType(
            ExceptionClassType.newBuilder().setClassName(mhExceptionSummary.getClassName()))
        .setStackTrace(
            StackTrace.newBuilder()
                .addAllElement(
                    mhExceptionSummary.getStackTraceList().stream()
                        .map(
                            mhStackTraceElement ->
                                StackTraceElement.newBuilder()
                                    .setClassName(mhStackTraceElement.getDeclaringClass())
                                    .setMethodName(mhStackTraceElement.getMethodName())
                                    .setFileName(mhStackTraceElement.getFileName())
                                    .setLineNumber(mhStackTraceElement.getLineNumber())
                                    .build())
                        .collect(Collectors.toList())))
        .build();
  }

  /**
   * Coverts the ExceptionDetail proto from the devtools/common/metrics/stability version to the MH
   * version.
   *
   * <p>Note this conversion will drop the error namespace info.
   */
  private static ExceptionSummary toExceptionSummaryWithoutNamespace(
      ExceptionProto.ExceptionSummaryOrBuilder commonExceptionSummary) {
    ErrorIdProto.ErrorId commonErrorId = commonExceptionSummary.getErrorId();
    ExceptionSummary.Builder summary =
        ExceptionSummary.newBuilder()
            .setErrorCode(commonErrorId.getCode())
            .setErrorName(commonErrorId.getName())
            .setErrorType(commonErrorId.getType())
            .setMessage(commonExceptionSummary.getMessage())
            .setClassName(commonExceptionSummary.getClassType().getClassName());
    if (commonExceptionSummary.hasStackTrace()) {
      commonExceptionSummary.getStackTrace().getElementList().stream()
          .map(
              stackTraceElement ->
                  com.google.devtools.mobileharness.api.model.proto.Error.StackTraceElement
                      .newBuilder()
                      .setDeclaringClass(stackTraceElement.getClassName())
                      .setMethodName(stackTraceElement.getMethodName())
                      .setFileName(stackTraceElement.getFileName())
                      .setLineNumber(stackTraceElement.getLineNumber()))
          .forEach(summary::addStackTrace);
    }

    return summary.build();
  }

  /**
   * Converts the ExceptionDetail proto from the MH version to the devtools/common/metrics/stability
   * version. Will always set the error namespace to MH.
   */
  public static ExceptionProto.ExceptionDetail toCommonExceptionDetail(
      ExceptionDetail mhExceptionDetail) {
    ExceptionProto.ExceptionDetail.Builder commonExceptionDetail =
        ExceptionProto.ExceptionDetail.newBuilder();
    if (mhExceptionDetail.hasSummary()) {
      commonExceptionDetail.setSummary(toCommonExceptionSummary(mhExceptionDetail.getSummary()));
    }
    if (mhExceptionDetail.hasCause()) {
      commonExceptionDetail.setCause(toCommonExceptionDetail(mhExceptionDetail.getCause()));
    }
    mhExceptionDetail
        .getSuppressedList()
        .forEach(
            mhSuppressed ->
                commonExceptionDetail.addSuppressed(toCommonExceptionDetail(mhSuppressed)));
    return commonExceptionDetail.build();
  }

  /**
   * Converts the ExceptionDetail proto from the devtools/common/metrics/stability version to the MH
   * version. This conversion will drop the error namespace.
   */
  public static ExceptionDetail toExceptionDetailWithoutNamespace(
      ExceptionProto.ExceptionDetail commonExceptionDetail) {
    ExceptionDetail.Builder mhExceptionDetail = ExceptionDetail.newBuilder();
    if (commonExceptionDetail.hasSummary()) {
      mhExceptionDetail.setSummary(
          toExceptionSummaryWithoutNamespace(commonExceptionDetail.getSummary()));
    }
    if (commonExceptionDetail.hasCause()) {
      mhExceptionDetail.setCause(
          toExceptionDetailWithoutNamespace(commonExceptionDetail.getCause()));
    }
    commonExceptionDetail
        .getSuppressedList()
        .forEach(
            commonSuppressed ->
                mhExceptionDetail.addSuppressed(
                    toExceptionDetailWithoutNamespace(commonSuppressed)));
    return mhExceptionDetail.build();
  }

  public static MobileHarnessException toMobileHarnessException(ExceptionDetail detail) {
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

  /** Converts an old MH exception to a new version. */
  public static MobileHarnessException upgradeMobileHarnessException(
      com.google.wireless.qa.mobileharness.shared.MobileHarnessException oldException) {
    if (oldException instanceof MobileHarnessException) {
      return (MobileHarnessException) oldException;
    }
    ErrorId errorId = UnknownErrorId.of(oldException.getErrorCodeEnum(), ErrorType.UNCLASSIFIED);
    MobileHarnessException newException =
        new MobileHarnessException(errorId, oldException.getMessage(), oldException.getCause());
    for (Throwable suppressed : oldException.getSuppressed()) {
      newException.addSuppressed(suppressed);
    }
    newException.setStackTrace(oldException.getStackTrace());
    return newException;
  }

  private static ErrorId getErrorId(ExceptionDetail detail) {
    ErrorId errorId;
    if (detail.hasSummary()) {
      ExceptionSummary summary = detail.getSummary();
      errorId =
          UnknownErrorId.of(summary.getErrorCode(), summary.getErrorName(), summary.getErrorType());
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
    } else if (throwable
        instanceof com.google.wireless.qa.mobileharness.shared.MobileHarnessException) {
      com.google.wireless.qa.mobileharness.shared.MobileHarnessException mhOldException =
          (com.google.wireless.qa.mobileharness.shared.MobileHarnessException) throwable;
      errorId =
          UnknownErrorId.of(
              mhOldException.getErrorCode(),
              mhOldException.getErrorName(),
              mhOldException.getErrorType());
    } else if (errorId == null) {
      errorId = BasicErrorId.NON_MH_EXCEPTION;
    }
    return errorId;
  }

  public static String getCompleteStackTrace(ExceptionDetail detail) {
    return Throwables.getStackTraceAsString(toMobileHarnessException(detail));
  }

  public static String getCompleteStackTrace(ExceptionProto.ExceptionDetail detail) {
    return Throwables.getStackTraceAsString(
        com.google.devtools.common.metrics.stability.converter.ErrorModelConverter
            .toDeserializedException(detail));
  }

  private static java.lang.StackTraceElement[] getStackTrace(ExceptionSummary summary) {
    return summary.getStackTraceList().stream()
        .map(
            stackTraceElement ->
                new java.lang.StackTraceElement(
                    stackTraceElement.getDeclaringClass(),
                    stackTraceElement.getMethodName(),
                    stackTraceElement.getFileName(),
                    stackTraceElement.getLineNumber()))
        .collect(Collectors.toList())
        .toArray(new java.lang.StackTraceElement[summary.getStackTraceCount()]);
  }

  private static String getErrorMessage(ErrorId errorId, ExceptionSummary exceptionSummary) {
    return BasicErrorId.NON_MH_EXCEPTION.equals(errorId)
        ? exceptionSummary.getMessage() + " (" + exceptionSummary.getClassName() + ")"
        : exceptionSummary.getMessage();
  }
}
