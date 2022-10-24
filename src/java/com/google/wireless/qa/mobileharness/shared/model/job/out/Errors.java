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

package com.google.wireless.qa.mobileharness.shared.model.job.out;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.shared.model.error.UnknownErrorId;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.proto.Common.ErrorInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Please use {@link Warnings} instead.
 *
 * <p>Warnings of the job/test occur during the execution.
 */
public class Errors {

  private final Object lock = new Object();

  @GuardedBy("lock")
  private final Warnings warnings;

  @GuardedBy("lock")
  private final Map<ExceptionDetailIdentifier, String> legacyErrorInfoStackTraces =
      new ConcurrentHashMap<>();

  /** Creates the error segment of a job/test. */
  public Errors(LogCollector<?> log, Timing timing) {
    warnings = new Warnings(log, timing.toNewTiming());
  }

  /**
   * Creates the error segment of a job/test by the given api {@link Warnings}. Note: please don't
   * make this public at any time.
   */
  Errors(Warnings warnings) {
    synchronized (lock) {
      this.warnings = warnings;
    }
  }

  /**
   * @return the new data model which has the same backend of this object.
   */
  @Beta
  public Warnings toWarnings() {
    synchronized (lock) {
      return warnings;
    }
  }

  /**
   * Records the error.
   *
   * <p>Please use {@link #add(MobileHarnessException)} or {@link #toWarnings()} instead. Because
   * now MH records structured stack traces of errors/warnings however {@link ErrorInfo} can not
   * provide them. Nowadays this method works in a very tricky way only for backward compatibility.
   */
  @CanIgnoreReturnValue
  public Errors add(ErrorInfo errorInfo) {
    ExceptionProto.ExceptionDetail exceptionDetail = toExceptionDetail(errorInfo);
    synchronized (lock) {
      warnings.add(exceptionDetail);
      legacyErrorInfoStackTraces.put(
          new ExceptionDetailIdentifier(exceptionDetail), errorInfo.getStackTrace());
    }
    return this;
  }

  /** Records the exception. */
  @CanIgnoreReturnValue
  public Errors add(MobileHarnessException e) {
    synchronized (lock) {
      if (e instanceof com.google.devtools.mobileharness.api.model.error.MobileHarnessException) {
        warnings.add((com.google.devtools.mobileharness.api.model.error.MobileHarnessException) e);
      } else {
        warnings.add(
            ErrorModelConverter.toCommonExceptionDetail(
                ErrorModelConverter.toExceptionDetail(
                    e, UnknownErrorId.of(e.getErrorCodeEnum(), e.getErrorType()))));
      }
    }
    return this;
  }

  /** Records the exception and override the error code. */
  @CanIgnoreReturnValue
  public Errors add(ErrorCode errorCode, Throwable throwable) {
    ErrorId errorId = getErrorId(errorCode, throwable);
    ExceptionProto.ExceptionDetail commonExceptionDetail =
        ErrorModelConverter.toCommonExceptionDetail(
            ErrorModelConverter.toExceptionDetail(throwable, errorId));
    synchronized (lock) {
      warnings.add(commonExceptionDetail);
    }
    return this;
  }

  /** Records the exception and override the error code. */
  @CanIgnoreReturnValue
  public Errors add(ErrorCode errorCode, String errorMessage) {
    ErrorId errorId = UnknownErrorId.of(errorCode, ErrorType.UNCLASSIFIED);
    synchronized (lock) {
      warnings.add(errorId, errorMessage);
    }
    return this;
  }

  /** Records all the warnings. */
  @CanIgnoreReturnValue
  public Errors addAll(Collection<ErrorInfo> errorInfos) {
    synchronized (lock) {
      for (ErrorInfo errorInfo : errorInfos) {
        add(errorInfo);
      }
    }
    return this;
  }

  /** Records the error. Also logs the error to the log buffer. */
  @CanIgnoreReturnValue
  public Errors addAndLog(ErrorInfo errorInfo) {
    return addAndLog(errorInfo, null);
  }

  /** Records the error. Also logs the error to the logger. */
  @CanIgnoreReturnValue
  public Errors addAndLog(ErrorInfo errorInfo, @Nullable FluentLogger logger) {
    ExceptionProto.ExceptionDetail exceptionDetail = toExceptionDetail(errorInfo);
    synchronized (lock) {
      warnings.addAndLog(exceptionDetail, logger);
      legacyErrorInfoStackTraces.put(
          new ExceptionDetailIdentifier(exceptionDetail), errorInfo.getStackTrace());
    }
    return this;
  }

  /** Records the error. Also logs the error to the log buffer. */
  @CanIgnoreReturnValue
  public Errors addAndLog(MobileHarnessException e) {
    return addAndLog(e, null);
  }

  /** Records the error. Also logs the error to the logger. */
  @CanIgnoreReturnValue
  public Errors addAndLog(MobileHarnessException e, @Nullable FluentLogger logger) {
    synchronized (lock) {
      if (e instanceof com.google.devtools.mobileharness.api.model.error.MobileHarnessException) {
        warnings.addAndLog(
            (com.google.devtools.mobileharness.api.model.error.MobileHarnessException) e, logger);
      } else {
        warnings.addAndLog(
            UnknownErrorId.of(e.getErrorCodeEnum(), e.getErrorType()),
            e.getMessage(),
            e.getCause(),
            logger);
      }
    }
    return this;
  }

  /** Saves and logs the error. */
  @CanIgnoreReturnValue
  public Errors addAndLog(ErrorCode errorCode, Throwable throwable) {
    synchronized (lock) {
      warnings.addAndLog(getErrorId(errorCode, throwable), throwable.getMessage(), throwable);
    }
    return this;
  }

  /** Saves and logs the error. */
  @CanIgnoreReturnValue
  public Errors addAndLog(ErrorCode errorCode, Throwable throwable, @Nullable FluentLogger logger) {
    synchronized (lock) {
      warnings.addAndLog(
          getErrorId(errorCode, throwable), throwable.getMessage(), throwable, logger);
    }
    return this;
  }

  /** Saves and logs the error. */
  @CanIgnoreReturnValue
  public Errors addAndLog(ErrorCode errorCode, String errorMessage) {
    synchronized (lock) {
      warnings.addAndLog(UnknownErrorId.of(errorCode, ErrorType.UNCLASSIFIED), errorMessage);
    }
    return this;
  }

  /** Saves and logs the error. */
  @CanIgnoreReturnValue
  public Errors addAndLog(ErrorCode errorCode, String errorMessage, @Nullable FluentLogger logger) {
    synchronized (lock) {
      warnings.addAndLog(
          UnknownErrorId.of(errorCode, ErrorType.UNCLASSIFIED), errorMessage, logger);
    }
    return this;
  }

  /** Returns all warnings. */
  public ImmutableList<ErrorInfo> getAll() {
    synchronized (lock) {
      ImmutableList.Builder<ErrorInfo> result = ImmutableList.builder();
      for (ExceptionProto.ExceptionDetail detail : warnings.getAll()) {
        result.add(
            toLegacyErrorInfo(
                detail, legacyErrorInfoStackTraces.get(new ExceptionDetailIdentifier(detail))));
      }
      return result.build();
    }
  }

  /** Returns the warnings with the given error code. */
  public List<ErrorInfo> get(ErrorCode errorCode) {
    synchronized (lock) {
      List<ErrorInfo> result = new ArrayList<>();
      for (ExceptionProto.ExceptionDetail detail : warnings.getAll()) {
        if (detail.getSummary().getErrorId().getCode() == errorCode.code()) {
          result.add(
              toLegacyErrorInfo(
                  detail, legacyErrorInfoStackTraces.get(new ExceptionDetailIdentifier(detail))));
        }
      }
      return result;
    }
  }

  /** Cleans up all warnings. */
  @CanIgnoreReturnValue
  public Errors clear() {
    synchronized (lock) {
      warnings.clear();
      legacyErrorInfoStackTraces.clear();
    }
    return this;
  }

  /** Returns the size of the error list. */
  public int size() {
    synchronized (lock) {
      return warnings.size();
    }
  }

  /** Returns whether the error list is empty. */
  public boolean isEmpty() {
    synchronized (lock) {
      return warnings.isEmpty();
    }
  }

  private ErrorId getErrorId(ErrorCode errorCode, Throwable throwable) {
    ErrorId errorId;
    if (throwable
        instanceof com.google.devtools.mobileharness.api.model.error.MobileHarnessException) {
      errorId =
          UnknownErrorId.of(
              errorCode,
              ((com.google.devtools.mobileharness.api.model.error.MobileHarnessException) throwable)
                  .getErrorId()
                  .type());
    } else if (throwable instanceof MobileHarnessException) {
      errorId = UnknownErrorId.of(errorCode, ((MobileHarnessException) throwable).getErrorType());
    } else {
      errorId = UnknownErrorId.of(errorCode, ErrorType.UNCLASSIFIED);
    }
    return errorId;
  }

  private static ErrorInfo toLegacyErrorInfo(
      ExceptionProto.ExceptionDetail detail, @Nullable String legacyErrorInfoStackTrace) {
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
    errorInfo.setStackTrace(
        legacyErrorInfoStackTrace == null
            ? ErrorModelConverter.getCompleteStackTrace(detail)
            : legacyErrorInfoStackTrace);
    return errorInfo.build();
  }

  @VisibleForTesting
  public static ExceptionProto.ExceptionDetail toExceptionDetail(ErrorInfo legacyErrorInfo) {
    return ExceptionDetail.newBuilder()
        .setSummary(
            ExceptionSummary.newBuilder()
                .setErrorId(
                    ErrorIdProto.ErrorId.newBuilder()
                        .setCode(legacyErrorInfo.getCode())
                        .setName(legacyErrorInfo.getName())
                        .setType(ErrorType.UNCLASSIFIED)
                        .setNamespace(Namespace.MH))
                .setMessage(legacyErrorInfo.getMessage()))
        .build();
  }

  private static class ExceptionDetailIdentifier {

    private final ExceptionProto.ExceptionDetail exceptionDetail;

    private ExceptionDetailIdentifier(ExceptionProto.ExceptionDetail exceptionDetail) {
      this.exceptionDetail = exceptionDetail;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(exceptionDetail);
    }

    @SuppressWarnings("ReferenceEquality")
    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExceptionDetailIdentifier
          && ((ExceptionDetailIdentifier) obj).exceptionDetail == this.exceptionDetail;
    }
  }
}
