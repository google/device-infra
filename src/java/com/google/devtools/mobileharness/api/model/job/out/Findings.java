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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.util.ErrorIdComparator;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Diagnostic;
import com.google.devtools.mobileharness.api.model.proto.Diagnostic.Finding.Severity;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Findings of the job/test occur during the execution.
 *
 * <p>TODO: This class doesn't integrate with {@code TouchableTiming}. We should either integrate
 * with it or remove TouchableTiming(preferred).
 */
public class Findings {

  /** Job/Test findings. */
  private final ConcurrentLinkedDeque<Finding> findings = new ConcurrentLinkedDeque<>();

  /** The log of the job/test. */
  private final LogCollector<?> log;

  /** Creates the findings segment of a job/test. */
  public Findings(LogCollector<?> log) {
    this.log = log;
  }

  /**
   * Creates the findings segment of a job/test by the given collection of {@link Findings}s.
   *
   * <p>Note: please don't make this public at any time.
   */
  Findings(LogCollector<?> log, Collection<Finding> findings) {
    this.log = log;
    this.findings.addAll(findings);
  }

  /**
   * Records a finding.
   *
   * @return the finding that was added.
   */
  @CanIgnoreReturnValue
  @VisibleForTesting
  Finding add(Finding finding) {
    findings.add(finding);
    return finding;
  }

  /**
   * Records the finding.
   *
   * @return the finding that was added.
   */
  @CanIgnoreReturnValue
  public Finding add(Diagnostic.Finding findingProto) {
    return add(new Finding(findingProto));
  }

  /**
   * Records the exception as a finding.
   *
   * @return the finding that was added.
   */
  @CanIgnoreReturnValue
  public Finding add(Severity severity, ExceptionDetail exceptionDetail) {
    return add(new Finding(severity, exceptionDetail));
  }

  /**
   * Records the exception as a finding.
   *
   * @return the finding that was added.
   */
  @CanIgnoreReturnValue
  public Finding add(Severity severity, MobileHarnessException e) {
    return add(severity, ErrorModelConverter.toExceptionDetail(e));
  }

  /**
   * Records a finding with the given {@link
   * com.google.devtools.mobileharness.api.model.error.ErrorId} and message.
   *
   * @return the finding that was added.
   */
  @CanIgnoreReturnValue
  public Finding add(
      Severity severity,
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage) {
    return add(
        severity,
        ErrorModelConverter.toExceptionDetail(new MobileHarnessException(errorId, errorMessage)));
  }

  /**
   * Records a finding with the given {@link
   * com.google.devtools.mobileharness.api.model.error.ErrorId}, message and cause exception.
   *
   * @return the finding that was added.
   */
  @CanIgnoreReturnValue
  public Finding add(
      Severity severity,
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable Throwable cause) {
    return add(
        severity,
        ErrorModelConverter.toExceptionDetail(
            new MobileHarnessException(errorId, errorMessage, cause)));
  }

  /** Records all the findings. */
  @CanIgnoreReturnValue
  public Findings addAll(Severity severity, Collection<ExceptionDetail> exceptionInfos) {
    for (ExceptionDetail exceptionDetail : exceptionInfos) {
      add(severity, exceptionDetail);
    }
    return this;
  }

  /** Records the finding. Also logs the finding to the log buffer. */
  @CanIgnoreReturnValue
  public Findings addAndLog(Severity severity, ExceptionDetail exceptionDetail) {
    return addAndLog(severity, exceptionDetail, null);
  }

  /** Records the finding. Also logs the finding to the logger. */
  @CanIgnoreReturnValue
  public Findings addAndLog(
      Severity severity, ExceptionDetail exceptionDetail, @Nullable FluentLogger logger) {
    add(severity, exceptionDetail);
    log(severity, ErrorModelConverter.toDeserializedException(exceptionDetail), logger);
    return this;
  }

  /** Records the finding. Also logs the finding to the log buffer. */
  @CanIgnoreReturnValue
  public Findings addAndLog(Severity severity, MobileHarnessException e) {
    return addAndLog(severity, e, null);
  }

  /** Records the finding. Also logs the finding to the logger. */
  @CanIgnoreReturnValue
  public Findings addAndLog(
      Severity severity, MobileHarnessException e, @Nullable FluentLogger logger) {
    add(severity, ErrorModelConverter.toExceptionDetail(e));
    log(severity, e, logger);
    return this;
  }

  /** Saves and logs the finding. */
  @CanIgnoreReturnValue
  public Findings addAndLog(
      Severity severity,
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage) {
    return addAndLog(severity, new MobileHarnessException(errorId, errorMessage));
  }

  /** Saves and logs the finding. */
  @CanIgnoreReturnValue
  public Findings addAndLog(
      Severity severity,
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable Throwable cause) {
    return addAndLog(severity, new MobileHarnessException(errorId, errorMessage, cause));
  }

  /** Saves and logs the finding. */
  @CanIgnoreReturnValue
  public Findings addAndLog(
      Severity severity,
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable FluentLogger logger) {
    return addAndLog(severity, new MobileHarnessException(errorId, errorMessage), logger);
  }

  /** Saves and logs the finding. */
  @CanIgnoreReturnValue
  public Findings addAndLog(
      Severity severity,
      com.google.devtools.mobileharness.api.model.error.ErrorId errorId,
      String errorMessage,
      @Nullable Throwable cause,
      @Nullable FluentLogger logger) {
    return addAndLog(severity, new MobileHarnessException(errorId, errorMessage, cause), logger);
  }

  /** Returns all findings. */
  public ImmutableList<Finding> getAll() {
    return ImmutableList.copyOf(findings);
  }

  /** Returns the findings with the given error ID. */
  public List<Finding> get(ErrorId errorId) {
    return findings.stream()
        .filter(
            finding ->
                ErrorIdComparator.equal(finding.getDetail().getSummary().getErrorId(), errorId))
        .collect(Collectors.toList());
  }

  /** Cleans up all findings. */
  @CanIgnoreReturnValue
  public Findings clear() {
    findings.clear();
    return this;
  }

  /** Returns the size of the finding list. */
  public int size() {
    return findings.size();
  }

  /** Returns whether the finding list is empty. */
  public boolean isEmpty() {
    return findings.isEmpty();
  }

  private void log(Severity severity, Throwable throwable, @Nullable FluentLogger logger) {
    Level logLevel =
        switch (severity) {
          case INFO, SUGGESTION -> Level.INFO;
          case SEVERE -> Level.SEVERE;
          case WARNING, SEVERITY_UNSPECIFIED, UNRECOGNIZED -> Level.WARNING;
        };
    log.at(logLevel).alsoTo(logger).withCauseStack().withCause(throwable).log(null);
  }
}
