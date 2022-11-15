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

package com.google.wireless.qa.mobileharness.shared.controller.event.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipJobException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler.SubscriberException;
import com.google.devtools.mobileharness.shared.util.comparator.ErrorTypeComparator;
import com.google.devtools.mobileharness.shared.util.comparator.TestResultComparator;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Handler for {@code SubscriberException} which is one of the following types: {@link
 * SkipTestException}, {@link SkipJobException}, {@link InterruptedException}. *
 */
public final class SkipInformationHandler {

  private SkipInformationHandler() {}

  /**
   * Subscriber exception with context. The exception can only be {@link SkipTestException}, {@link
   * SkipJobException}, or {@link InterruptedException}.
   */
  @AutoValue
  public abstract static class SkipInformation {
    static SkipInformation of(
        Throwable exception, SubscriberExceptionContext context, boolean isJob) {
      checkArgument(
          exception instanceof SkipJobException
              || exception instanceof SkipTestException
              || exception instanceof InterruptedException,
          "Exception for SkipInformation can only be instanceof SkipJobException, SkipTestException"
              + " or InterruptedException, but was %s.",
          exception.getClass());
      return new AutoValue_SkipInformationHandler_SkipInformation(exception, context, isJob);
    }

    public abstract Throwable exception();

    public abstract SubscriberExceptionContext context();

    /** True when the SkipInformation is about a job, false about a test. */
    public abstract boolean isJob();

    @Memoized
    ErrorType errorType() {
      return criticalErrorId().type();
    }

    @Memoized
    int errorCode() {
      return criticalErrorId().code();
    }

    // Returns the 1st error id with non-(UNCLASSIFIED/UNDETERMINED) error type in the exception()
    // and its exception chain. If no non-(UNCLASSIFIED/UNDETERMINED) error type exists, returns
    // the error id of the exception().
    @Memoized
    ErrorId criticalErrorId() {
      if (exception() instanceof InterruptedException) {
        return isJob()
            ? BasicErrorId.USER_PLUGIN_SKIP_JOB_BY_INTERRUPTED_EXCEPTION
            : BasicErrorId.USER_PLUGIN_SKIP_TEST_BY_INTERRUPTED_EXCEPTION;
      }

      ErrorId errorId =
          exception() instanceof SkipJobException
              ? ((SkipJobException) exception()).errorId()
              : ((SkipTestException) exception()).errorId();
      Throwable cause = exception().getCause();
      while (cause != null) {
        if (errorId.type() != ErrorType.UNCLASSIFIED && errorId.type() != ErrorType.UNDETERMINED) {
          return errorId;
        }
        if (cause instanceof MobileHarnessException) {
          errorId = ((MobileHarnessException) cause).getErrorId();
        }
        cause = cause.getCause();
      }
      return errorId;
    }

    @Memoized
    TestResult getTestResult() {
      if (exception() instanceof SkipJobException) {
        return ((SkipJobException) exception()).jobResult();
      } else if (exception() instanceof SkipTestException) {
        return ((SkipTestException) exception()).testResult();
      } else {
        return TestResult.FAIL;
      }
    }
  }

  /** Stores the result, cause and messages we want to log. */
  @AutoValue
  public abstract static class SkipResultWithCause {

    // cause only nullable when result is PASS.
    private static SkipResultWithCause of(
        TestResult result, @Nullable MobileHarnessException cause, String report) {
      return new AutoValue_SkipInformationHandler_SkipResultWithCause(
          ResultTypeWithCause.create(result, cause), report);
    }

    public abstract ResultTypeWithCause resultWithCause();

    /** Report with the calculated test result and context. */
    public abstract String report();
  }

  public static Optional<SkipInformation> convertIfSkipJobRunning(SubscriberException se) {
    if (se.exception() instanceof InterruptedException
        || se.exception() instanceof SkipJobException) {
      return Optional.of(SkipInformation.of(se.exception(), se.context(), /* isJob= */ true));
    } else {
      return Optional.empty();
    }
  }

  public static Optional<SkipInformation> convertIfSkipTestRunning(SubscriberException se) {
    if (se.exception() instanceof InterruptedException
        || se.exception() instanceof SkipTestException) {
      return Optional.of(SkipInformation.of(se.exception(), se.context(), /* isJob= */ false));
    } else {
      return Optional.empty();
    }
  }

  // Package visible class to store the internal status of selected top SkipInformation,
  // the remaining SkipInformations, and the result.
  @AutoValue
  abstract static class InternalSplittedSkipInfos {
    private static InternalSplittedSkipInfos of(
        SkipInformation skipReason,
        ImmutableList<SkipInformation> otherSkipReasons,
        TestResult result) {
      return new AutoValue_SkipInformationHandler_InternalSplittedSkipInfos(
          skipReason, otherSkipReasons, result);
    }

    abstract SkipInformation skipReason();

    abstract ImmutableList<SkipInformation> otherSkipReasons();

    abstract TestResult result();
  }

  /**
   * Creates the {@code MobileHarnessException} as the exception that causes the job/test result.
   */
  private static MobileHarnessException createResultCauseException(
      InternalSplittedSkipInfos splittedSkipInfos, String messageWithResult, boolean isJob) {
    Throwable skipReasonException = splittedSkipInfos.skipReason().exception();
    ErrorId resultCauseExceptionErrorId =
        getResultCauseExceptionErrorId(skipReasonException, isJob);
    MobileHarnessException resultCauseException =
        new MobileHarnessException(
            resultCauseExceptionErrorId, messageWithResult, skipReasonException);
    for (SkipInformation otherReason : splittedSkipInfos.otherSkipReasons()) {
      resultCauseException.addSuppressed(otherReason.exception());
    }
    return resultCauseException;
  }

  private static ErrorId getResultCauseExceptionErrorId(Throwable exception, boolean isSkipJob) {
    if (isSkipJob) {
      return exception instanceof SkipJobException
          ? ((SkipJobException) exception).errorId()
          : BasicErrorId.USER_PLUGIN_SKIP_JOB_BY_INTERRUPTED_EXCEPTION;
    } else {
      return exception instanceof SkipTestException
          ? ((SkipTestException) exception).errorId()
          : BasicErrorId.USER_PLUGIN_SKIP_TEST_BY_INTERRUPTED_EXCEPTION;
    }
  }

  private static InternalSplittedSkipInfos splitSkipInfos(List<SkipInformation> skipInfos) {
    SkipInformation skipReason = getTopSkipInformation(skipInfos);
    // The other skipInfos which is not top stored in another list.
    // Note that skipReason is one of the element in skipInfos, so we use != instead of !equals().
    ImmutableList<SkipInformation> remainedSkipInfos =
        skipInfos.stream().filter(skipInfo -> skipInfo != skipReason).collect(toImmutableList());
    TestResult result = skipReason.getTestResult();
    return InternalSplittedSkipInfos.of(skipReason, remainedSkipInfos, result);
  }

  public static SkipResultWithCause getJobResult(List<SkipInformation> skipInfos) {
    InternalSplittedSkipInfos splittedSkipInfos = splitSkipInfos(skipInfos);
    String messageWithResult = createReport(splittedSkipInfos, /* isSkipJob= */ true);
    if (splittedSkipInfos.result().equals(TestResult.PASS)) {
      return SkipResultWithCause.of(splittedSkipInfos.result(), null, messageWithResult);
    } else {
      MobileHarnessException resultCauseException =
          createResultCauseException(splittedSkipInfos, messageWithResult, /* isJob= */ true);
      return SkipResultWithCause.of(
          splittedSkipInfos.result(), resultCauseException, messageWithResult);
    }
  }

  public static SkipResultWithCause getTestResult(List<SkipInformation> skipInfos) {
    InternalSplittedSkipInfos splittedSkipInfos = splitSkipInfos(skipInfos);
    String messageWithResult = createReport(splittedSkipInfos, /*isSkipJob*/ false);
    if (splittedSkipInfos.result().equals(TestResult.PASS)) {
      return SkipResultWithCause.of(TestResult.PASS, null, messageWithResult);
    } else {
      MobileHarnessException resultCauseException =
          createResultCauseException(splittedSkipInfos, messageWithResult, /* isJob= */ false);
      return SkipResultWithCause.of(
          splittedSkipInfos.result(), resultCauseException, messageWithResult);
    }
  }

  // Return a string report that contains all context of the information list and the result.
  // @param isSkipJob indicates we are dealing with jobs skipping or tests skipping.
  private static String createReport(InternalSplittedSkipInfos skipInfos, boolean isSkipJob) {
    String pattern = "Plugin [%s] throws [%s]";
    String prefix =
        isSkipJob
            ? "Plugins try to skip the job running and mark the job and its top-level tests as %s"
            : "Plugins try to skip the test running and mark the test as %s";
    return skipInfos.otherSkipReasons().isEmpty()
        ? String.format(
            prefix + " because of critical reason: %s",
            skipInfos.result(),
            String.format(
                pattern,
                skipInfos.skipReason().context().getSubscriber().getClass().getSimpleName(),
                skipInfos.skipReason().exception()))
        : String.format(
            prefix + " because of critical reason: %s, and suppressed other reasons: %s",
            skipInfos.result(),
            String.format(
                pattern,
                skipInfos.skipReason().context().getSubscriber().getClass().getSimpleName(),
                skipInfos.skipReason().exception()),
            skipInfos.otherSkipReasons().stream()
                .map(
                    exception ->
                        String.format(
                            pattern,
                            exception.context().getSubscriber().getClass().getSimpleName(),
                            exception.exception()))
                .collect(toImmutableList()));
  }

  /**
   * Returns the most important SkipInformation. The caller should make sure the information list is
   * not empty.
   *
   * <p>The priority order: comparing TestResult first, and then errorType, and then errorId.
   */
  private static SkipInformation getTopSkipInformation(List<SkipInformation> skipInfos) {
    return skipInfos.stream()
        .min(
            Comparator.comparing(SkipInformation::getTestResult, new TestResultComparator())
                .thenComparing(SkipInformation::errorType, ErrorTypeComparator.getInstance())
                .thenComparing(SkipInformation::errorCode))
        .orElseThrow(IllegalArgumentException::new);
  }
}
