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
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.shared.model.error.UnknownErrorId;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.proto.Common.ErrorInfo;
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

  /** Returns the new data model which has the same backend of this object. */
  @Beta
  public Warnings toWarnings() {
    synchronized (lock) {
      return warnings;
    }
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

  /** Returns all warnings. */
  public ImmutableList<ErrorInfo> getAll() {
    synchronized (lock) {
      ImmutableList.Builder<ErrorInfo> result = ImmutableList.builder();
      for (ExceptionProto.ExceptionDetail detail : warnings.getAll()) {
        result.add(toLegacyErrorInfo(detail));
      }
      return result.build();
    }
  }

  /** Returns the size of the error list. */
  public int size() {
    synchronized (lock) {
      return warnings.size();
    }
  }

  private static ErrorInfo toLegacyErrorInfo(ExceptionProto.ExceptionDetail detail) {
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
        .setStackTrace(ErrorModelConverter.getCompleteStackTrace(detail))
        .build();
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
                        .setType(legacyErrorInfo.getType())
                        .setNamespace(
                            legacyErrorInfo.hasNamespace()
                                ? legacyErrorInfo.getNamespace()
                                : Namespace.MH))
                .setMessage(legacyErrorInfo.getMessage()))
        .build();
  }
}
