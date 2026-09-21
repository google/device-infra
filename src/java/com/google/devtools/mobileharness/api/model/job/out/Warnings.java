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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Diagnostic.Finding.Severity;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Warnings of the job/test occur during the execution.
 *
 * <p>This class is a thin wrapper of {@link Findings}: every warning is stored as a {@link Finding}
 * with severity {@link Severity#WARNING}. Prefer using {@link Findings} directly in new code.
 *
 * <p>Note that {@link #getAll()} and {@link #get(ErrorId)} rebuild the {@link ExceptionDetail}s
 * from the {@linkplain com.google.devtools.common.metrics.stability.model.proto.ExceptionProto
 * .FlattenedExceptionDetail flattened} ones stored in the findings, so the stack traces of the
 * cause exceptions and the suppressed exceptions are not preserved.
 */
public class Warnings {

  /** Job/Test findings which back the warnings. All of them have severity {@code WARNING}. */
  private final Findings findings;

  /** The time records of the job/test. */
  private final TouchableTiming timing;

  /**
   * DO NOT USE. It creates a new {@link Findings} instance internally. It breaks the assumption
   * that the warnings of a job/test are stored in a single {@link Findings} instance.
   *
   * <p>This is used by some unit tests for backward compatibility. This will be removed after all
   * usages are migrated to the new constructor.
   */
  @Deprecated
  public Warnings(LogCollector<?> log, TouchableTiming timing) {
    this(timing, new Findings(log));
  }

  /** Creates the warning segment of a job/test. */
  public Warnings(TouchableTiming timing, Findings findings) {
    this.findings = findings;
    this.timing = timing;
  }

  /** Records the exception as a warning. */
  @CanIgnoreReturnValue
  public Warnings add(ExceptionDetail exceptionDetail) {
    findings.add(Severity.WARNING, exceptionDetail);
    timing.touch();
    return this;
  }

  /** Records the exception as a warning. */
  @CanIgnoreReturnValue
  public Warnings add(MobileHarnessException e) {
    findings.add(Severity.WARNING, e);
    timing.touch();
    return this;
  }

  /**
   * Records a warning with the given {@link
   * com.google.devtools.mobileharness.api.model.error.ErrorId} and message.
   */
  @CanIgnoreReturnValue
  public Warnings add(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId, String errorMessage) {
    return add(errorId, errorMessage, null);
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
    findings.add(Severity.WARNING, errorId, errorMessage, cause);
    timing.touch();
    return this;
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
    findings.addAndLog(Severity.WARNING, exceptionDetail, logger);
    timing.touch();
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
    findings.addAndLog(Severity.WARNING, e, logger);
    timing.touch();
    return this;
  }

  /** Saves and logs the warning. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId, String errorMessage) {
    findings.addAndLog(Severity.WARNING, errorId, errorMessage);
    timing.touch();
    return this;
  }

  /** Saves and logs the warning. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable Throwable cause) {
    findings.addAndLog(Severity.WARNING, errorId, errorMessage, cause);
    timing.touch();
    return this;
  }

  /** Saves and logs the warning. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable FluentLogger logger) {
    findings.addAndLog(Severity.WARNING, errorId, errorMessage, logger);
    timing.touch();
    return this;
  }

  /** Saves and logs the warning. */
  @CanIgnoreReturnValue
  public Warnings addAndLog(
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable Throwable cause,
      @Nullable FluentLogger logger) {
    findings.addAndLog(Severity.WARNING, errorId, errorMessage, cause, logger);
    timing.touch();
    return this;
  }

  /** Returns all warnings. */
  public ImmutableList<ExceptionDetail> getAll() {
    return toExceptionDetails(findings.getAll(Severity.WARNING));
  }

  /** Returns the warnings with the given warning ID. */
  public List<ExceptionDetail> get(ErrorId errorId) {
    return findings.get(errorId).stream()
        .filter(finding -> finding.getSeverity().equals(Severity.WARNING))
        .map(finding -> ErrorModelConverter.toExceptionDetail(finding.getDetail()))
        .collect(toImmutableList());
  }

  /** Cleans up all warnings. */
  @CanIgnoreReturnValue
  public Warnings clear() {
    findings.clear(Severity.WARNING);
    timing.touch();
    return this;
  }

  /** Returns the size of the warning list. */
  public int size() {
    return findings.size(Severity.WARNING);
  }

  /** Returns whether the warning list is empty. */
  public boolean isEmpty() {
    return findings.isEmpty(Severity.WARNING);
  }

  private static ImmutableList<ExceptionDetail> toExceptionDetails(Collection<Finding> findings) {
    return findings.stream()
        .map(finding -> ErrorModelConverter.toExceptionDetail(finding.getDetail()))
        .collect(toImmutableList());
  }
}
