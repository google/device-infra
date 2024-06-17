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

package com.google.devtools.mobileharness.infra.ats.server.sessionplugin;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandAttemptDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest.RequestCase;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.WithProto;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfigs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.LocalSessionStub;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import javax.inject.Inject;

/** Session Plugin to serve test requests coming from ATS server. */
@WithProto({SessionRequest.class, RequestDetail.class})
final class AtsServerSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String TRADEFED_DRIVER_NAME = "XtsTradefedTest";
  private static final String SESSION_PLUGIN_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.ats.server.sessionplugin.AtsServerSessionPlugin";
  private static final String SESSION_MODULE_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.ats.server.sessionplugin.AtsServerSessionPluginModule";
  private static final String SESSION_PLUGIN_LABEL = "AtsServerSessionPlugin";

  /** Set in {@link #onSessionStarting}. */
  private volatile SessionRequest request;

  private final Object requestDetailLock = new Object();

  // The source of truth for this session's request states.
  @GuardedBy("requestDetailLock")
  private final RequestDetail.Builder requestDetail = RequestDetail.newBuilder();

  @GuardedBy("requestDetailLock")
  private int runningTradefedJobCount = 0;

  private final SessionInfo sessionInfo;
  private final NewMultiCommandRequestHandler newMultiCommandRequestHandler;
  private final LocalSessionStub localSessionStub;
  private final Clock clock;

  @Inject
  AtsServerSessionPlugin(
      SessionInfo sessionInfo,
      NewMultiCommandRequestHandler newMultiCommandRequestHandler,
      LocalSessionStub localSessionStub,
      Clock clock) {
    this.sessionInfo = sessionInfo;
    this.newMultiCommandRequestHandler = newMultiCommandRequestHandler;
    this.localSessionStub = localSessionStub;
    this.clock = clock;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws InvalidProtocolBufferException, InterruptedException, MobileHarnessException {
    request =
        sessionInfo.getSessionPluginExecutionConfig().getConfig().unpack(SessionRequest.class);
    if (request.getRequestCase().equals(RequestCase.NEW_MULTI_COMMAND_REQUEST)) {
      synchronized (requestDetailLock) {
        NewMultiCommandRequest newMultiCommandRequest = request.getNewMultiCommandRequest();
        requestDetail
            .setCreateTime(Timestamps.fromMillis(clock.millis()))
            .setStartTime(Timestamps.fromMillis(clock.millis()))
            .setId(sessionInfo.getSessionId())
            .setState(RequestState.RUNNING)
            .setOriginalRequest(newMultiCommandRequest)
            .setMaxRetryOnTestFailures(newMultiCommandRequest.getMaxRetryOnTestFailures())
            .addAllCommandInfos(newMultiCommandRequest.getCommandsList());
        try {
          newMultiCommandRequestHandler.addTradefedJobs(
              newMultiCommandRequest, sessionInfo, requestDetail);
          runningTradefedJobCount = sessionInfo.getAllJobs().size();
          // If no tradefed job was created and the request does not contain any error that cause it
          // to cancel, create non tradefed jobs directly.
          if (requestDetail.getCommandDetailsCount() == 0
              && requestDetail.getState() == RequestState.RUNNING) {
            createNonTradefedJobs();
          }
        } finally {
          updateSessionPluginOutput();
        }
      }
    }
  }

  @Subscribe
  public void onJobEnded(JobEndEvent jobEndEvent)
      throws InterruptedException, MobileHarnessException {
    JobInfo jobInfo = jobEndEvent.getJob();

    // Generate a new commandAttempt for the finished job, and update the command status.
    updateCommandDetail(jobInfo);

    // If all tradefed jobs have ended, create non tradefed jobs.
    synchronized (requestDetailLock) {
      // If a non-tradefed tests ended, that means all non-tradefed tests had already been created
      // and no need to create more.
      if (!jobInfo.type().getDriver().equals(TRADEFED_DRIVER_NAME)) {
        return;
      }
      runningTradefedJobCount -= 1;
      // Skip if there are still TF jobs running.
      if (runningTradefedJobCount > 0) {
        return;
      }
      createNonTradefedJobs();
    }
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event) throws InterruptedException {
    synchronized (requestDetailLock) {
      try {
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
      } finally {
        // Set final state if not in terminal state.
        if (requestDetail.getState().equals(RequestState.RUNNING)
            || requestDetail.getState().equals(RequestState.UNKNOWN)) {
          requestDetail.setState(
              hasSessionPassed(requestDetail.build())
                  ? RequestState.COMPLETED
                  : RequestState.ERROR);
        }
        updateSessionPluginOutput();
      }

      if (requestDetail.getState().equals(RequestState.ERROR)
          && requestDetail.getMaxRetryOnTestFailures() > 0) {
        try {
          retrySession();
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log("Failed to trigger retry session.");
        }
      }
    }
  }

  @GuardedBy("requestDetailLock")
  private void retrySession() throws MobileHarnessException {
    NewMultiCommandRequest.Builder retryRequestBuilder =
        requestDetail.getOriginalRequest().toBuilder();
    retryRequestBuilder.setRetryPreviousSessionId(sessionInfo.getSessionId());
    // TODO: customize retry type
    retryRequestBuilder.setMaxRetryOnTestFailures(requestDetail.getMaxRetryOnTestFailures() - 1);
    SessionPluginConfig retryConfig =
        SessionPluginConfig.newBuilder()
            .setExecutionConfig(
                sessionInfo.getSessionPluginExecutionConfig().toBuilder()
                    .setConfig(
                        Any.pack(
                            SessionRequest.newBuilder()
                                .setNewMultiCommandRequest(retryRequestBuilder.build())
                                .build())))
            .setLoadingConfig(
                SessionPluginLoadingConfig.newBuilder()
                    .setPluginClassName(SESSION_PLUGIN_CLASS_NAME)
                    .setPluginModuleClassName(SESSION_MODULE_CLASS_NAME))
            .setExplicitLabel(SessionPluginLabel.newBuilder().setLabel(SESSION_PLUGIN_LABEL))
            .build();
    CreateSessionRequest createSessionRequest =
        CreateSessionRequest.newBuilder()
            .setSessionConfig(
                SessionConfig.newBuilder()
                    .setSessionPluginConfigs(
                        SessionPluginConfigs.newBuilder().addSessionPluginConfig(retryConfig)))
            .build();
    String nextAttemptSessionId =
        localSessionStub.createSession(createSessionRequest).getSessionId().getId();
    requestDetail.setNextAttemptSessionId(nextAttemptSessionId);
    updateSessionPluginOutput();
  }

  private void createNonTradefedJobs() throws MobileHarnessException, InterruptedException {
    synchronized (requestDetailLock) {
      for (CommandInfo commandInfo : requestDetail.getOriginalRequest().getCommandsList()) {
        Optional<CommandDetail> commandDetail =
            newMultiCommandRequestHandler.addNonTradefedJobs(
                requestDetail.getOriginalRequest(), commandInfo, sessionInfo);
        commandDetail.ifPresent(detail -> requestDetail.putCommandDetails(detail.getId(), detail));
      }
      updateSessionPluginOutput();
    }
  }

  private CommandAttemptDetail generateCommandAttemptDetail(JobInfo jobInfo, TestInfo testInfo) {
    CommandAttemptDetail.Builder builder =
        CommandAttemptDetail.newBuilder()
            .setId(testInfo.locator().getId())
            .setRequestId(sessionInfo.getSessionId())
            .setCommandId(newMultiCommandRequestHandler.getCommandIdOfJob(jobInfo));
    // ATS server requests use device UUIDs to schedule tests.
    ImmutableList<String> deviceUuids =
        jobInfo.subDeviceSpecs().getAllSubDevices().stream()
            .filter(subDeviceSpec -> subDeviceSpec.dimensions().get("uuid") != null)
            .map(subDeviceSpec -> subDeviceSpec.dimensions().get("uuid"))
            .collect(toImmutableList());
    if (!deviceUuids.isEmpty()) {
      builder.addAllDeviceSerials(deviceUuids);
    }

    // Tradefed test result.
    if (testInfo.properties().has(XtsConstants.TRADEFED_TESTS_PASSED)) {
      builder.setPassedTestCount(
          Long.parseLong(testInfo.properties().get(XtsConstants.TRADEFED_TESTS_PASSED)));
    }
    if (testInfo.properties().has(XtsConstants.TRADEFED_TESTS_FAILED)) {
      builder.setFailedTestCount(
          Long.parseLong(testInfo.properties().get(XtsConstants.TRADEFED_TESTS_FAILED)));
    }

    // Non-tradefed test result.
    if (testInfo.properties().has(MoblyTestInfoMapHelper.MOBLY_TESTS_PASSED)) {
      builder.setPassedTestCount(
          Long.parseLong(testInfo.properties().get(MoblyTestInfoMapHelper.MOBLY_TESTS_PASSED)));
    }
    if (testInfo.properties().has(MoblyTestInfoMapHelper.MOBLY_TESTS_FAILED_AND_ERROR)) {
      builder.setFailedTestCount(
          Long.parseLong(
              testInfo.properties().get(MoblyTestInfoMapHelper.MOBLY_TESTS_FAILED_AND_ERROR)));
    }
    if (testInfo.timing().getStartTime() != null) {
      builder.setStartTime(TimeUtils.toProtoTimestamp(testInfo.timing().getStartTime()));
    }
    if (testInfo.timing().getEndTime() != null) {
      builder.setEndTime(TimeUtils.toProtoTimestamp(testInfo.timing().getEndTime()));
    }
    if (testInfo.timing().getCreateTime() != null) {
      builder.setCreateTime(TimeUtils.toProtoTimestamp(testInfo.timing().getCreateTime()));
    }
    if (testInfo.timing().getModifyTime() != null) {
      builder.setUpdateTime(TimeUtils.toProtoTimestamp(testInfo.timing().getModifyTime()));
    }
    return builder
        .setTotalTestCount(builder.getPassedTestCount() + builder.getFailedTestCount())
        .setState(convertStatusAndResultToCommandState(testInfo.status(), testInfo.result()))
        .build();
  }

  private void updateCommandDetail(JobInfo jobInfo) {
    synchronized (requestDetailLock) {
      if (!requestDetail.containsCommandDetails(
          newMultiCommandRequestHandler.getCommandIdOfJob(jobInfo))) {
        logger.atWarning().log(
            "Tradefed job %s not found in requestDetail", jobInfo.locator().getId());
        return;
      }
      // Add a command attempt
      CommandAttemptDetail commandAttemptDetail =
          generateCommandAttemptDetail(
              jobInfo, jobInfo.tests().getAll().values().iterator().next());
      requestDetail.addCommandAttemptDetails(commandAttemptDetail);
      requestDetail.setUpdateTime(TimeUtils.toProtoTimestamp(Instant.now()));
      updateSessionPluginOutput();
    }
  }

  private void updateSessionPluginOutput() {
    synchronized (requestDetailLock) {
      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
    }
  }

  private boolean hasSessionPassed(RequestDetail requestDetail) {
    return !requestDetail.getCommandDetailsMap().isEmpty()
        && requestDetail.getCommandDetailsMap().values().stream()
            .allMatch(commandDetail -> commandDetail.getState() == CommandState.COMPLETED);
  }

  private CommandState convertStatusAndResultToCommandState(Status status, Result result) {
    switch (status.get()) {
      case NEW:
        return CommandState.UNKNOWN_STATE;
      case ASSIGNED:
        return CommandState.QUEUED;
      case RUNNING:
        return CommandState.RUNNING;
      case DONE:
        if (result.get().equals(TestResult.PASS)) {
          return CommandState.COMPLETED;
        } else {
          return CommandState.ERROR;
        }
      case SUSPENDED:
        return CommandState.CANCELED;
    }
    return CommandState.UNKNOWN_STATE;
  }
}
