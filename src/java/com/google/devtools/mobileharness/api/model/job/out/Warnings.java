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

package com.google.devtools.mobileharness.api.model.job.out;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.util.ErrorIdComparator;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Warnings of the job/test occur during the execution. */
public class Warnings {
  /** Job/Test warnings. */
  private final ConcurrentLinkedDeque<ExceptionDetail> warnings = new ConcurrentLinkedDeque<>();

  /** The log of the job/test. */
  private final LogCollector<?> log;

  /** The time records of the job/test. */
  private final TouchableTiming timing;

  /** Creates the warning segment of a job/test. */
  public Warnings(LogCollector<?> log, TouchableTiming timing) {
    this.log = log;
    this.timing = timing;
  }

  /**
   * Creates the warning segment of a job/test by the given collection of {@link ExceptionDetail}s.
   * Note: please don't make this public at any time.
   */
  Warnings(
      LogCollector<?> log, TouchableTiming timing, Collection<ExceptionDetail> exceptionDetails) {
    this.log = log;
    this.timing = timing;
    this.warnings.addAll(exceptionDetails);
  }

  /** Records the exception as a warning. */
  @CanIgnoreReturnValue
  public Warnings add(ExceptionDetail exceptionDetail) {
    warnings.add(exceptionDetail);
    timing.touch();
    return this;
  }

  /** Records the exception as a warning. */
  @CanIgnoreReturnValue
  public Warnings add(MobileHarnessException e) {
    return add(ErrorModelConverter.toExceptionDetail(e));
  }

  /**
   * Records a warning with the given {@link
   * com.google.devtools.mobileharness.api.model.error.ErrorId} and message.
   */
  @CanIgnoreReturnValue
  public Warnings add(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId, String errorMessage) {
    return add(
        ErrorModelConverter.toExceptionDetail(new MobileHarnessException(errorId, errorMessage)));
  }

  /**
   * Records a warning with the given {@link
   * com.google.devtools.mobileharness.api.model.error.ErrorId}, message and cause exception.
   */
  @CanIgnoreReturnValue
  public Warnings add(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable Throwable cause) {
    return add(
        ErrorModelConverter.toExceptionDetail(
            new MobileHarnessException(errorId, errorMessage, cause)));
  }

  /** Records all the warnings. */
  @CanIgnoreReturnValue
  public Warnings addAll(Collection<ExceptionDetail> exceptionInfos) {
    for (ExceptionDetail exceptionDetail : exceptionInfos) {
      add(exceptionDetail);
    }
    return this;
  }

  /** Records the warning. Also logs the warning to the log buffer. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(ExceptionDetail exceptionDetail) {
    return addAndLog(exceptionDetail, null);
  }

  /** Records the warning. Also logs the warning to the logger. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(ExceptionDetail exceptionDetail, @Nullable FluentLogger logger) {
    add(exceptionDetail);
    log(ErrorModelConverter.toDeserializedException(exceptionDetail), logger, null /* errorId */);
    return this;
  }

  /** Records the warning. Also logs the warning to the log buffer. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(MobileHarnessException e) {
    return addAndLog(e, null);
  }

  /** Records the warning. Also logs the warning to the logger. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(MobileHarnessException e, @Nullable FluentLogger logger) {
    add(ErrorModelConverter.toExceptionDetail(e));
    log(e, logger, null /* errorId */);
    return this;
  }

  /** Saves and logs the warning. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId, String errorMessage) {
    return addAndLog(new MobileHarnessException(errorId, errorMessage));
  }

  /** Saves and logs the warning. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable Throwable cause) {
    return addAndLog(new MobileHarnessException(errorId, errorMessage, cause));
  }

  /** Saves and logs the warning. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable FluentLogger logger) {
    return addAndLog(new MobileHarnessException(errorId, errorMessage), logger);
  }

  /** Saves and logs the warning. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable Throwable cause,
      @Nullable FluentLogger logger) {
    return addAndLog(new MobileHarnessException(errorId, errorMessage, cause), logger);
  }

  /** Returns all warnings. */
  public ImmutableList<ExceptionDetail> getAll() {
    return ImmutableList.copyOf(warnings);
  }

  /** Returns the warnings with the given warning ID. */
  public List<ExceptionDetail> get(ErrorId errorId) {
    return warnings.stream()
        .filter(
            exceptionDetail ->
                ErrorIdComparator.equal(exceptionDetail.getSummary().getErrorId(), errorId))
        .collect(Collectors.toList());
  }

  /** Cleans up all warnings. */
  @CanIgnoreReturnValue
  public Warnings clear() {
    warnings.clear();
    timing.touch();
    return this;
  }

  /** Returns the size of the warning list. */
  public int size() {
    return warnings.size();
  }

  /** Returns whether the warning list is empty. */
  public boolean isEmpty() {
    return warnings.isEmpty();
  }

  private void log(Throwable throwable, @Nullable FluentLogger logger, @Nullable ErrorId errorId) {
    if (errorId == null) {
      log.atWarning().alsoTo(logger).withCauseStack().withCause(throwable).log(null);
    } else {
      log.atWarning()
          .alsoTo(logger)
          .withCauseStack()
          .withCause(throwable)
          .log("Error %s(%d)[%s]", errorId.name(), errorId.code(), errorId.namespace());
    }
  }
}
