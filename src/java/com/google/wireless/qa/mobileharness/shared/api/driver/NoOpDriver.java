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
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
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
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.annotation.concurrent.GuardedBy;
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

  private enum WakeupReason {
    RECEIVE_MESSAGE,
    LEASE_EXPIRE,
  }

  private static final String MESSAGE_NAMESPACE = "mobileharness:driver:NoOpDriver";

  private final SettableFuture<WakeupReason> wakeupFuture = SettableFuture.create();

  private final Object leaseLock = new Object();

  @GuardedBy("leaseLock")
  private Duration leaseExpirationTime;

  @GuardedBy("leaseLock")
  private ListeningScheduledExecutorService scheduledThreadPool;

  @GuardedBy("leaseLock")
  private ListenableFuture<?> leaseExpirationFuture;

  private volatile Test.TestResult testResultFromMessage;
  private volatile com.google.devtools.mobileharness.api.model.error.MobileHarnessException
      testResultCauseFromMessage;

  @VisibleForTesting
  @Inject
  public NoOpDriver(Device device, TestInfo testInfo) {
    super(device, testInfo);
  }

  @Override
  public void run(TestInfo testInfo) throws InterruptedException, MobileHarnessException {
    NoOpDriverSpec spec = testInfo.jobInfo().combinedSpec(this);

    if (spec.hasLeaseExpirationTimeSec()) {
      Duration leaseExpirationTime = Duration.ofSeconds(spec.getLeaseExpirationTimeSec());
      testInfo.log().atInfo().alsoTo(logger).log("Lease expiration time: %s", leaseExpirationTime);
      synchronized (leaseLock) {
        this.leaseExpirationTime = leaseExpirationTime;
        scheduledThreadPool =
            ThreadPools.createStandardScheduledThreadPool(
                /* threadNamePrefix= */ "no-op-driver-lease-monitor-" + testInfo.locator().getId(),
                /* corePoolSize= */ 1);
        extendLease();
      }
    }

    try {
      int sleepTimeSec = spec.getSleepTimeSec();
      testInfo.log().atInfo().alsoTo(logger).log("Sleep for %d seconds", sleepTimeSec);
      WakeupReason wakeupReason = wakeupFuture.get(sleepTimeSec, SECONDS);
      testInfo.log().atInfo().alsoTo(logger).log("Wake up from sleep, reason=%s", wakeupReason);
    } catch (ExecutionException e) {
      throw new AssertionError(e); // The future will never be set with an exception.
    } catch (TimeoutException e) {
      // Does nothing. No wake_up message received and the lease is not expired.
    } finally {
      synchronized (leaseLock) {
        if (leaseExpirationFuture != null) {
          leaseExpirationFuture.cancel(/* mayInterruptIfRunning= */ false);
          leaseExpirationFuture = null;
        }
        if (scheduledThreadPool != null) {
          scheduledThreadPool.shutdown();
          scheduledThreadPool = null;
        }
      }

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
  private void onTestMessage(TestMessageEvent event) {
    TestInfo testInfo = event.getTest();
    Map<String, String> message = event.getMessage();
    if (MESSAGE_NAMESPACE.equals(message.get("namespace"))) {
      if (Objects.equals(message.get("type"), "wake_up")) {
        wakeupFuture.set(WakeupReason.RECEIVE_MESSAGE);
      } else if (Objects.equals(message.get("type"), "extend_lease")) {
        testInfo.log().atInfo().alsoTo(logger).log("Lease is extended");
        synchronized (leaseLock) {
          if (leaseExpirationTime != null
              && scheduledThreadPool != null
              && leaseExpirationFuture != null) {
            leaseExpirationFuture.cancel(/* mayInterruptIfRunning= */ false);
            extendLease();
          }
        }
      } else if (Objects.equals(message.get("type"), "set_result")
          && message.containsKey("result")) {
        String resultString = message.get("result");
        testInfo.log().atInfo().alsoTo(logger).log("Set result to: [%s]", resultString);
        try {
          testResultFromMessage = Test.TestResult.valueOf(resultString.toUpperCase(Locale.ROOT));
          testResultCauseFromMessage =
              new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                  ExtErrorId.NO_OP_DRIVER_NON_PASSING_RESULT_SET_BY_MESSAGE,
                  "NoOpDriver non-passing result set by test message");
        } catch (IllegalArgumentException e) {
          testInfo
              .errors()
              .addAndLog(
                  new MobileHarnessException(
                      ErrorCode.ILLEGAL_ARGUMENT,
                      "Failed to set result to [" + resultString + "]",
                      e),
                  logger);
        }
      } else {
        testInfo
            .errors()
            .addAndLog(
                new MobileHarnessException(
                    ErrorCode.TEST_MESSAGE_ERROR,
                    "Unrecognized NoOpDriver test message: " + message),
                logger);
      }
    }
  }

  @GuardedBy("leaseLock")
  private void extendLease() {
    if (leaseExpirationFuture != null) {
      leaseExpirationFuture.cancel(/* mayInterruptIfRunning= */ false);
    }
    leaseExpirationFuture =
        scheduledThreadPool.schedule(this::onLeaseExpiration, leaseExpirationTime);
  }

  private void onLeaseExpiration() {
    wakeupFuture.set(WakeupReason.LEASE_EXPIRE);
  }
}
