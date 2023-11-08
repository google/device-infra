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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.NoOpDriverSpec;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;

/**
 * The driver that does nothing but sleeping for specified time and allowing users run their
 * customized plugin directly.
 */
@DriverAnnotation(help = "Do nothing in the driver but sleeping for specified time.")
@TestAnnotation(
    required = false,
    help =
        "Any words. Each word will create one run. If this tests field "
            + "is empty by default Mobile Harness will create one run.")
public class NoOpDriver extends BaseDriver implements SpecConfigable<NoOpDriverSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MESSAGE_NAMESPACE = "mobileharness:driver:NoOpDriver";

  private final Sleeper sleeper;

  private volatile Test.TestResult testResultFromMessage;
  private volatile com.google.devtools.mobileharness.api.model.error.MobileHarnessException
      testResultCauseFromMessage;

  @VisibleForTesting
  @Inject
  public NoOpDriver(Device device, TestInfo testInfo, Sleeper sleeper) {
    super(device, testInfo);
    this.sleeper = sleeper;
  }

  @Override
  public void run(TestInfo testInfo) throws InterruptedException, MobileHarnessException {
    NoOpDriverSpec spec = testInfo.jobInfo().combinedSpec(this);
    int sleepTimeSec = spec.getSleepTimeSec();
    testInfo.log().atInfo().alsoTo(logger).log("Sleep for %d seconds", sleepTimeSec);
    try {
      sleeper.sleep(Duration.ofSeconds(sleepTimeSec));
    } catch (IllegalArgumentException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Failed to sleep for %d seconds: %s", sleepTimeSec, e.getMessage());
    } finally {
      // Sets the test result to:
      // 1. The result from the last test message.
      // 2. The result from job params.
      // 3. PASS.
      Test.TestResult testResultFromMessage = this.testResultFromMessage;
      com.google.devtools.mobileharness.api.model.error.MobileHarnessException
          testResultCauseFromMessage = this.testResultCauseFromMessage;
      if (testResultFromMessage == null) {
        if (spec.hasTestResult()) {
          if (spec.getTestResult() == TestResult.PASS) {
            testInfo.resultWithCause().setPass();
          } else {
            testInfo
                .resultWithCause()
                .setNonPassing(
                    Test.TestResult.valueOf(spec.getTestResult().name()),
                    new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                        ExtErrorId.NO_OP_DRIVER_NON_PASSING_RESULT_SET_BY_PARAM,
                        String.format(
                            "NoOpDriver non-passing result set by param \"test_result\""
                                + ", reason=[%s]",
                            spec.getTestResultReason())));
          }
        } else {
          testInfo.resultWithCause().setPass();
        }
      } else {
        if (testResultFromMessage == Test.TestResult.PASS) {
          testInfo.resultWithCause().setPass();
        } else {
          testInfo
              .resultWithCause()
              .setNonPassing(testResultFromMessage, testResultCauseFromMessage);
        }
      }
    }
  }

  /**
   * Gets the result setting message of the given test result.
   *
   * <p>{@linkplain
   * com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil#sendMessageToTest(TestInfo,
   * Map) Sends the message} to the test from client / lab side before the driver ends to set the
   * test result.
   *
   * @see com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil
   */
  public static ImmutableMap<String, String> getSetResultMessage(TestResult testResult) {
    checkNotNull(testResult);
    return ImmutableMap.of(
        "namespace",
        MESSAGE_NAMESPACE,
        "type",
        "set_result",
        "result",
        testResult.name().toLowerCase(Locale.ROOT));
  }

  @Subscribe
  private void onTestMessage(TestMessageEvent testMessageEvent) {
    Map<String, String> message = testMessageEvent.getMessage();
    if (MESSAGE_NAMESPACE.equals(message.get("namespace"))) {
      String resultString = message.get("result");
      if ("set_result".equals(message.get("type")) && resultString != null) {
        testMessageEvent
            .getTest()
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Set result to: [%s]", resultString);
        try {
          testResultFromMessage = Test.TestResult.valueOf(resultString.toUpperCase(Locale.ROOT));
          testResultCauseFromMessage =
              new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                  ExtErrorId.NO_OP_DRIVER_NON_PASSING_RESULT_SET_BY_MESSAGE,
                  "NoOpDriver non-passing result set by test message");
        } catch (IllegalArgumentException e) {
          testMessageEvent
              .getTest()
              .errors()
              .addAndLog(
                  new MobileHarnessException(
                      ErrorCode.ILLEGAL_ARGUMENT,
                      "Failed to set result to [" + resultString + "]",
                      e),
                  logger);
        }
      } else {
        testMessageEvent
            .getTest()
            .errors()
            .addAndLog(
                new MobileHarnessException(
                    ErrorCode.TEST_MESSAGE_ERROR, "Invalid NoOpDriver test message: " + message),
                logger);
      }
    }
  }
}
