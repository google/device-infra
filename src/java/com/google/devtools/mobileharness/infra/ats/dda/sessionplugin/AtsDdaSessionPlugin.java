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

import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaDuration;
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
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.WithProto;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
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
@WithProto({
  AtsDdaSessionPluginConfig.class,
  AtsDdaSessionPluginOutput.class,
  AtsDdaSessionNotification.class
})
public class AtsDdaSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String JOB_NAME = "ats_dda_job";
  private static final String JOB_USER = "ats-dda";
  private static final String TEST_NAME = "ats_dda_test";
  private static final String DRIVER_NAME = "NoOpDriver";
  private static final Duration MAX_DRIVER_SLEEP_TIME = Duration.ofHours(2L);
  private static final Duration START_TIMEOUT = Duration.ofMinutes(1L);
  private static final String TEST_MESSAGE_NAMESPACE = "mobileharness:driver:NoOpDriver";
  private static final ImmutableMap<String, String> CANCEL_TEST_MESSAGE =
      ImmutableMap.of("namespace", TEST_MESSAGE_NAMESPACE, "type", "wake_up");
  private static final ImmutableMap<String, String> HEARTBEAT_TEST_MESSAGE =
      ImmutableMap.of("namespace", TEST_MESSAGE_NAMESPACE, "type", "extend_lease");

  private final SessionInfo sessionInfo;
  private final TestMessageUtil testMessageUtil;

  private final Object sessionLock = new Object();

  @GuardedBy("sessionLock")
  private boolean sessionCancelled;

  @GuardedBy("sessionLock")
  @Nullable
  private String startedTestId;

  @GuardedBy("sessionLock")
  private boolean cancelTestMessageSent;

  @GuardedBy("sessionLock")
  private boolean testEnded;

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

    synchronized (sessionLock) {
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
    // Calculates timeout.
    Duration ddaTimeout =
        finalizeDdaTimeout(config.hasDdaTimeout() ? toJavaDuration(config.getDdaTimeout()) : null);
    Duration testTimeout = ddaTimeout.plusMinutes(1L);
    Duration jobTimeout = testTimeout.plus(START_TIMEOUT);

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
                            .setJobTimeoutMs(jobTimeout.toMillis())
                            .setStartTimeoutMs(START_TIMEOUT.toMillis())
                            .setTestTimeoutMs(testTimeout.toMillis())
                            .build())
                    .setRetry(Retry.newBuilder().setTestAttempts(1).build())
                    .setPriority(Priority.MAX)
                    .setAllocationExitStrategy(AllocationExitStrategy.FAIL_FAST_NO_IDLE)
                    .build())
            .build();
    jobInfo.dimensions().addAll(deviceRequirement.getDimensionsMap());
    jobInfo.params().add("sleep_time_sec", Long.toString(ddaTimeout.toSeconds()));
    jobInfo
        .params()
        .add(
            "lease_expiration_time_sec",
            Long.toString(Flags.instance().atsDdaLeaseExpirationTime.getNonNull().toSeconds()));
    jobInfo.params().add("cache_device_in_driver", Boolean.TRUE.toString());
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
    synchronized (sessionLock) {
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
    synchronized (sessionLock) {
      testEnded = true;
    }
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
      synchronized (sessionLock) {
        sessionCancelled = true;
        testIdToSendCancelTestMessage = getTestIdToSendCancelTestMessage();
        if (testIdToSendCancelTestMessage.isPresent()) {
          cancelTestMessageSent = true;
        }
      }
      testIdToSendCancelTestMessage.ifPresent(this::sendCancelTestMessage);
    } else if (notification.getNotificationCase() == NotificationCase.HEARTBEAT_SESSION) {
      // Sends heartbeat test message.
      String testIdToSendHeartbeatTestMessage;
      synchronized (sessionLock) {
        testIdToSendHeartbeatTestMessage = testEnded ? null : startedTestId;
      }
      if (testIdToSendHeartbeatTestMessage != null) {
        sendHeartbeatTestMessage(testIdToSendHeartbeatTestMessage);
      }
    }
  }

  @GuardedBy("sessionLock")
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

  private void sendHeartbeatTestMessage(String testId) {
    logger.atInfo().log(
        "Sending heartbeat test message to test [%s], message=%s", testId, HEARTBEAT_TEST_MESSAGE);
    try {
      testMessageUtil.sendMessageToTest(testId, HEARTBEAT_TEST_MESSAGE);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to send heartbeat test message to test [%s]", testId);
    }
  }

  private static Duration finalizeDdaTimeout(@Nullable Duration ddaTimeout) {
    if (ddaTimeout == null) {
      return MAX_DRIVER_SLEEP_TIME;
    } else if (ddaTimeout.compareTo(MAX_DRIVER_SLEEP_TIME) < 0) {
      return ddaTimeout;
    } else {
      return MAX_DRIVER_SLEEP_TIME;
    }
  }
}
