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

package com.google.devtools.mobileharness.infra.ats.dda.sessionplugin;

import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.AllocationExitStrategy;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirement;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.testrunner.event.test.TestEndedEvent;
import com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent;
import com.google.devtools.mobileharness.infra.ats.dda.proto.SessionPluginProto.AtsDdaSessionNotification;
import com.google.devtools.mobileharness.infra.ats.dda.proto.SessionPluginProto.AtsDdaSessionNotification.NotificationCase;
import com.google.devtools.mobileharness.infra.ats.dda.proto.SessionPluginProto.AtsDdaSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.dda.proto.SessionPluginProto.AtsDdaSessionPluginOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionNotificationEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import java.time.Duration;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/** Session plugin for ATS DDA. */
public class AtsDdaSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String JOB_NAME = "ats_dda_job";
  private static final String JOB_USER = "ats-dda";
  private static final String TEST_NAME = "ats_dda_test";
  private static final String DRIVER_NAME = "NoOpDriver";
  private static final Duration JOB_TIMEOUT = Duration.ofHours(1L);
  private static final Duration START_TIMEOUT = Duration.ofMinutes(1L);
  private static final Duration TEST_TIMEOUT = JOB_TIMEOUT.minusMinutes(1L);
  private static final Duration DRIVER_SLEEP_TIME = TEST_TIMEOUT.minusMinutes(1L);
  private static final ImmutableMap<String, String> CANCEL_TEST_MESSAGE =
      ImmutableMap.of("namespace", "mobileharness:driver:NoOpDriver", "type", "wake_up");

  private final SessionInfo sessionInfo;
  private final TestMessageUtil testMessageUtil;

  private final Object cancelSessionLock = new Object();

  @GuardedBy("cancelSessionLock")
  private boolean sessionCancelled;

  @GuardedBy("cancelSessionLock")
  @Nullable
  private String startedTestId;

  @GuardedBy("cancelSessionLock")
  private boolean cancelTestMessageSent;

  /** Set in {@link #onSessionStarting(SessionStartingEvent)}. */
  private volatile AtsDdaSessionPluginConfig config;

  @Inject
  AtsDdaSessionPlugin(SessionInfo sessionInfo, TestMessageUtil testMessageUtil) {
    this.sessionInfo = sessionInfo;
    this.testMessageUtil = testMessageUtil;
  }

  @Subscribe
  private void onSessionStarting(SessionStartingEvent event)
      throws MobileHarnessException, InvalidProtocolBufferException {
    config =
        event
            .sessionInfo()
            .getSessionPluginExecutionConfig()
            .getConfig()
            .unpack(AtsDdaSessionPluginConfig.class);
    logger.atInfo().log("Config: %s", shortDebugString(config));

    synchronized (cancelSessionLock) {
      if (sessionCancelled) {
        logger.atInfo().log(
            "Skip creating job since the session [%s] has been cancelled",
            sessionInfo.getSessionId());
      } else {
        JobInfo jobInfo = createJobInfo();
        sessionInfo.addJob(jobInfo);
        logger.atInfo().log(
            "Added job [%s] to session [%s]", jobInfo.locator(), sessionInfo.getSessionId());
      }
    }
  }

  private JobInfo createJobInfo() throws MobileHarnessException {
    DeviceRequirement deviceRequirement = config.getDeviceRequirement();
    // TODO: Adds default decorators here.
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator(JOB_NAME))
            .setJobUser(JobUser.newBuilder().setActualUser(JOB_USER).setRunAs(JOB_USER).build())
            .setType(
                JobType.newBuilder()
                    .setDriver(DRIVER_NAME)
                    .setDevice(deviceRequirement.getDeviceType())
                    .addAllDecorator(Lists.reverse(deviceRequirement.getDecoratorList()))
                    .build())
            .setSetting(
                JobSetting.newBuilder()
                    .setTimeout(
                        Timeout.newBuilder()
                            .setJobTimeoutMs(JOB_TIMEOUT.toMillis())
                            .setStartTimeoutMs(START_TIMEOUT.toMillis())
                            .setTestTimeoutMs(TEST_TIMEOUT.toMillis())
                            .build())
                    .setRetry(Retry.newBuilder().setTestAttempts(1).build())
                    .setPriority(Priority.MAX)
                    .setAllocationExitStrategy(AllocationExitStrategy.FAIL_FAST_NO_IDLE)
                    .build())
            .build();
    jobInfo.dimensions().addAll(deviceRequirement.getDimensionsMap());
    jobInfo.params().add("sleep_time_sec", Long.toString(DRIVER_SLEEP_TIME.toSeconds()));
    jobInfo.tests().add(TEST_NAME);
    return jobInfo;
  }

  /** TODO: Uses lab side TestStartingEvent instead. */
  @Subscribe
  private void onTestStarting(TestStartingEvent event) {
    DeviceInfo deviceInfo = event.getAllDeviceInfos().get(0);
    logger.atInfo().log("New device allocation: [%s]", shortDebugString(deviceInfo));
    sessionInfo.setSessionPluginOutput(
        oldOutput -> AtsDdaSessionPluginOutput.newBuilder().setAllocatedDevice(deviceInfo).build(),
        AtsDdaSessionPluginOutput.class);

    // Sends cached cancel test message if any.
    Optional<String> testIdToSendCancelTestMessage;
    synchronized (cancelSessionLock) {
      startedTestId = event.getTest().locator().getId();
      testIdToSendCancelTestMessage = getTestIdToSendCancelTestMessage();
      if (testIdToSendCancelTestMessage.isPresent()) {
        cancelTestMessageSent = true;
      }
    }
    testIdToSendCancelTestMessage.ifPresent(this::sendCancelTestMessage);
  }

  @Subscribe
  private void onTestEnded(TestEndedEvent event) throws MobileHarnessException {
    Optional<MobileHarnessException> testError =
        event.getTest().resultWithCause().get().causeException();
    if (testError.isPresent()) {
      throw testError.get();
    }
  }

  @Subscribe
  private void onSessionNotification(SessionNotificationEvent event)
      throws InvalidProtocolBufferException {
    AtsDdaSessionNotification notification =
        event.sessionNotification().getNotification().unpack(AtsDdaSessionNotification.class);
    logger.atInfo().log("Notification: %s", shortDebugString(notification));

    if (notification.getNotificationCase() == NotificationCase.CANCEL_SESSION) {
      // Sends cancel test message.
      Optional<String> testIdToSendCancelTestMessage;
      synchronized (cancelSessionLock) {
        sessionCancelled = true;
        testIdToSendCancelTestMessage = getTestIdToSendCancelTestMessage();
        if (testIdToSendCancelTestMessage.isPresent()) {
          cancelTestMessageSent = true;
        }
      }
      testIdToSendCancelTestMessage.ifPresent(this::sendCancelTestMessage);
    }
  }

  @GuardedBy("cancelSessionLock")
  private Optional<String> getTestIdToSendCancelTestMessage() {
    if (sessionCancelled && startedTestId != null && !cancelTestMessageSent) {
      return Optional.of(startedTestId);
    } else {
      return Optional.empty();
    }
  }

  private void sendCancelTestMessage(String testId) {
    logger.atInfo().log(
        "Sending cancel test message to test [%s], message=%s", testId, CANCEL_TEST_MESSAGE);
    try {
      testMessageUtil.sendMessageToTest(testId, CANCEL_TEST_MESSAGE);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to send cancel test message to test [%s]", testId);
    }
  }
}
