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

package com.google.devtools.mobileharness.infra.controller.test.util;

import com.google.auto.value.AutoValue;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipJobException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.wireless.qa.mobileharness.client.api.event.JobEvent;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;

/** For logging the exception in subscribers. Will log to test log if it is a test event. */
public class SubscriberExceptionLoggingHandler implements SubscriberExceptionHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Subscriber exception with context. */
  @AutoValue
  public abstract static class SubscriberException {

    private static SubscriberException of(Throwable exception, SubscriberExceptionContext context) {
      return new AutoValue_SubscriberExceptionLoggingHandler_SubscriberException(
          exception, context);
    }

    public abstract Throwable exception();

    public abstract SubscriberExceptionContext context();
  }

  private final boolean saveException;

  /**
   * Whethher the plugins are provided by users. If yes, will convert all their exceptions to set
   * their ErrorType to CUSTOMER_ISSUE.
   */
  private final boolean isUserPlugin;

  @GuardedBy("itself")
  private final List<SubscriberException> exceptions = new ArrayList<>();

  public SubscriberExceptionLoggingHandler() {
    this(false /* saveException */, false /*isUserPlugin*/);
  }

  public SubscriberExceptionLoggingHandler(boolean saveException, boolean isUserPlugin) {
    this.saveException = saveException;
    this.isUserPlugin = isUserPlugin;
  }

  @SuppressWarnings("FloggerWithoutCause")
  @Override
  public void handleException(Throwable exception, SubscriberExceptionContext context) {
    if (saveException) {
      synchronized (exceptions) {
        exceptions.add(SubscriberException.of(exception, context));
      }
    }

    if (exception instanceof SkipTestException || exception instanceof SkipJobException) {
      // Handles exception for skipping test/job.
      handleSkipException(exception, context);
    } else {
      // Handles unexpected exception.
      // InterruptedException is treated as unexpected exception here because it is deprecated for
      // skipping test/job and it may be thrown because of timeout rather than plugin itself.
      handleUnexpectedException(exception, context, isUserPlugin);
    }
  }

  /**
   * Gets all subscribers handled by this handler and removes them.
   *
   * <p>You need to set <tt>saveException</tt> to <tt>true</tt> when constructing the handler to use
   * this feature.
   */
  public List<SubscriberException> pollExceptions() {
    ImmutableList<SubscriberException> result;
    synchronized (exceptions) {
      result = ImmutableList.copyOf(exceptions);
      exceptions.clear();
    }
    return result;
  }

  private static void handleSkipException(Throwable exception, SubscriberExceptionContext context) {
    Object event = context.getEvent();
    if (isTestEvent(event)) {
      TestInfo testInfo = getTestInfoFromTestEvent(event);
      if (exception instanceof SkipTestException) {
        SkipTestException skipTestException = (SkipTestException) exception;
        if (shouldPrintDetailForSkipException(skipTestException.testResult())) {
          testInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      InfraErrorId.TR_PLUGIN_USER_SKIP_TEST,
                      String.format(
                          "Plugin [%s] wants to skip test and set test to [%s]. "
                              + "Check test log for more details.",
                          context.getSubscriber().getClass().getSimpleName(),
                          skipTestException.testResult()),
                      exception),
                  logger);
        } else {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Plugin [%s] wants to skip test and set test to [%s] by [%s]",
                  context.getSubscriber().getClass().getSimpleName(),
                  skipTestException.testResult(),
                  exception);
        }
      } else if (exception instanceof SkipJobException) {
        testInfo
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    InfraErrorId.TR_PLUGIN_USER_SKIP_JOB_IN_TEST_EVENT,
                    String.format(
                        "SkipJobException is thrown in a test event %s and it will NOT work. "
                            + "SkipJobException only works in JobStartEvent. "
                            + "Do you mean SkipTestException?",
                        event.getClass().getSimpleName()),
                    exception),
                logger);
      }
    } else if (isJobEvent(event)) {
      JobInfo jobInfo = getJobInfoFromJobEvent(event);
      if (exception instanceof SkipJobException) {
        TestResult desiredJobResult = ((SkipJobException) exception).jobResult();
        if (shouldPrintDetailForSkipException(desiredJobResult)) {
          jobInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      InfraErrorId.TR_PLUGIN_USER_SKIP_JOB,
                      String.format(
                          "Plugin [%s] wants to skip job and set job to [%s]. "
                              + "Check job log for more details.",
                          context.getSubscriber().getClass().getSimpleName(), desiredJobResult),
                      exception),
                  logger);
        } else {
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Plugin [%s] wants to skip job and set job to [%s] by [%s]",
                  context.getSubscriber().getClass().getSimpleName(), desiredJobResult, exception);
        }
      } else if (exception instanceof SkipTestException) {
        jobInfo
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    InfraErrorId.TR_PLUGIN_USER_SKIP_TEST_IN_JOB_EVENT,
                    String.format(
                        "SkipTestException is thrown in a job event %s and it will NOT work. "
                            + "SkipTestException only works in test events. "
                            + "Do you mean SkipJobException?",
                        event.getClass().getSimpleName()),
                    exception),
                logger);
      }
    } else {
      logger.atWarning().withCause(exception).log(
          "%s is thrown in neither TestEvent or JobEvent so it will NOT work",
          exception.getClass().getSimpleName());
    }
  }

  private static void handleUnexpectedException(
      Throwable exception, SubscriberExceptionContext context, boolean isUserPlugin) {
    String message = "Error occurred in:\n" + context.getSubscriberMethod();
    logger.atWarning().withCause(exception).log("%s", message);
    Object event = context.getEvent();
    String stackTrace = Throwables.getStackTraceAsString(exception);
    if (isTestEvent(event)) {
      TestInfo testInfo = getTestInfoFromTestEvent(event);
      if (isUserPlugin) {
        testInfo
            .warnings()
            .add(
                new MobileHarnessException(
                    InfraErrorId.TR_PLUGIN_USER_TEST_ERROR, "User test plugin error", exception));
      } else {
        if (exception instanceof com.google.wireless.qa.mobileharness.shared.MobileHarnessException
            && ((com.google.wireless.qa.mobileharness.shared.MobileHarnessException) exception)
                    .getErrorCode()
                != ErrorCode.UNKNOWN.code()) {
          // Uses the error code in MobileHarnessException.
          testInfo
              .errors()
              .add((com.google.wireless.qa.mobileharness.shared.MobileHarnessException) exception);
        } else {
          testInfo
              .warnings()
              .add(
                  InfraErrorId.TR_PLUGIN_UNKNOWN_TEST_ERROR,
                  "Unexpected exception from test plugin",
                  exception);
        }
      }
      testInfo.log().atInfo().log("%s", stackTrace);
      logger.atWarning().log(
          "Error in the plugin of test %s: %n%s", testInfo.locator().getId(), stackTrace);
    } else if (isJobEvent(event)) {
      JobInfo jobInfo = getJobInfoFromJobEvent(event);
      if (isUserPlugin) {
        jobInfo
            .warnings()
            .add(
                new MobileHarnessException(
                    InfraErrorId.TR_PLUGIN_USER_JOB_ERROR, "User job plugin error", exception));
      } else {
        if (exception instanceof com.google.wireless.qa.mobileharness.shared.MobileHarnessException
            && ((com.google.wireless.qa.mobileharness.shared.MobileHarnessException) exception)
                    .getErrorCode()
                != ErrorCode.UNKNOWN.code()) {
          // Uses the error code in MobileHarnessException.
          jobInfo
              .errors()
              .add((com.google.wireless.qa.mobileharness.shared.MobileHarnessException) exception);
        } else {
          jobInfo
              .warnings()
              .add(
                  InfraErrorId.TR_PLUGIN_UNKNOWN_JOB_ERROR,
                  "Unexpected exception from job plugin",
                  exception);
        }
      }
      logger.atWarning().log(
          "Error in the plugin of job %s: %n%s", jobInfo.locator().getId(), stackTrace);
    }
  }

  private static boolean isTestEvent(Object event) {
    return event instanceof TestEvent
        || event instanceof com.google.devtools.mobileharness.api.testrunner.event.test.TestEvent;
  }

  private static TestInfo getTestInfoFromTestEvent(Object testEvent) {
    return testEvent instanceof TestEvent
        ? ((TestEvent) testEvent).getTest()
        : ((com.google.devtools.mobileharness.api.testrunner.event.test.TestEvent) testEvent)
            .getTest();
  }

  private static boolean isJobEvent(Object event) {
    return event instanceof JobEvent;
  }

  private static JobInfo getJobInfoFromJobEvent(Object jobEvent) {
    return ((JobEvent) jobEvent).getJob();
  }

  private static boolean shouldPrintDetailForSkipException(TestResult desiredResult) {
    return !(TestResult.PASS.equals(desiredResult) || TestResult.SKIP.equals(desiredResult));
  }
}
