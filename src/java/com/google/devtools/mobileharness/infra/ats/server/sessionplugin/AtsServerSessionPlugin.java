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

import static com.google.protobuf.TextFormat.shortDebugString;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.DEVICE_ID_LIST;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
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
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfigs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.LocalSessionStub;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.api.driver.XtsTradefedTest;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/** Session Plugin to serve test requests coming from ATS server. */
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
  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final NewMultiCommandRequestHandler newMultiCommandRequestHandler;
  private final LocalSessionStub localSessionStub;

  @Inject
  AtsServerSessionPlugin(
      SessionInfo sessionInfo,
      NewMultiCommandRequestHandler newMultiCommandRequestHandler,
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      LocalSessionStub localSessionStub) {
    this.sessionInfo = sessionInfo;
    this.newMultiCommandRequestHandler = newMultiCommandRequestHandler;
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
    this.localSessionStub = localSessionStub;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws InvalidProtocolBufferException, InterruptedException, MobileHarnessException {
    request =
        sessionInfo.getSessionPluginExecutionConfig().getConfig().unpack(SessionRequest.class);
    if (request.getRequestCase().equals(RequestCase.NEW_MULTI_COMMAND_REQUEST)) {
      synchronized (requestDetailLock) {
        requestDetail.mergeFrom(
            newMultiCommandRequestHandler.addTradefedJobs(
                request.getNewMultiCommandRequest(), sessionInfo));
        runningTradefedJobCount = sessionInfo.getAllJobs().size();
        // If no tradefed job was created and the request does not contain any error that cause it
        // to cancel, create non tradefed jobs directly.
        if (requestDetail.getCommandDetailsCount() == 0
            && requestDetail.getState() == RequestState.RUNNING) {
          createNonTradefedJobs();
        }
        RequestDetail latestRequestDetail = requestDetail.build();
        sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
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
  public void onSessionEnded(SessionEndedEvent event)
      throws InterruptedException, MobileHarnessException {
    synchronized (requestDetailLock) {
      newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail.build());
      Map<String, CommandDetail> newCommandDetails = new HashMap<>();
      for (CommandDetail commandDetail : requestDetail.getCommandDetailsMap().values()) {
        CommandDetail.Builder updatedCommandDetail =
            commandDetail.toBuilder()
                .setState(
                    hasCommandPassed(commandDetail.getId(), requestDetail.build())
                        ? CommandState.COMPLETED
                        : CommandState.ERROR);
        updatedCommandDetail.setEndTime(updatedCommandDetail.getUpdateTime());
        newCommandDetails.put(commandDetail.getId(), updatedCommandDetail.build());
      }
      requestDetail.putAllCommandDetails(newCommandDetails);
      requestDetail.setState(
          sessionRequestHandlerUtil.isSessionPassed(sessionInfo.getAllJobs())
              ? RequestState.COMPLETED
              : RequestState.ERROR);
      logger.atInfo().log("RequestDetail: %s", shortDebugString(requestDetail.build()));

      if (requestDetail.getState().equals(RequestState.ERROR)
          && requestDetail.getMaxRetryOnTestFailures() > 0) {
        retrySession();
      }

      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
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
  }

  private void createNonTradefedJobs() throws MobileHarnessException, InterruptedException {
    synchronized (requestDetailLock) {
      for (CommandInfo commandInfo : requestDetail.getOriginalRequest().getCommandsList()) {
        Optional<CommandDetail> commandDetail =
            newMultiCommandRequestHandler.addNonTradefedJobs(
                requestDetail.getOriginalRequest(), commandInfo, sessionInfo);
        if (commandDetail.isPresent()) {
          requestDetail.putCommandDetails(commandDetail.get().getId(), commandDetail.get());
        }
      }
      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
    }
  }

  private CommandAttemptDetail generateCommandAttemptDetail(JobInfo jobInfo, TestInfo testInfo) {
    CommandAttemptDetail.Builder builder = CommandAttemptDetail.newBuilder();
    builder.setId(testInfo.locator().getId());
    builder.setRequestId(sessionInfo.getSessionId());
    builder.setCommandId(newMultiCommandRequestHandler.getCommandIdOfJob(jobInfo));
    if (testInfo.properties().has(DEVICE_ID_LIST)) {
      ImmutableList<String> deviceSerials =
          ImmutableList.copyOf(testInfo.properties().get(DEVICE_ID_LIST).split(","));
      builder.addAllDeviceSerials(deviceSerials);
    }

    // Tradefed test result.
    if (testInfo.properties().has(XtsTradefedTest.TRADEFED_TESTS_PASSED)) {
      builder.setPassedTestCount(
          Long.parseLong(testInfo.properties().get(XtsTradefedTest.TRADEFED_TESTS_PASSED)));
    }
    if (testInfo.properties().has(XtsTradefedTest.TRADEFED_TESTS_FAILED)) {
      builder.setFailedTestCount(
          Long.parseLong(testInfo.properties().get(XtsTradefedTest.TRADEFED_TESTS_FAILED)));
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
    return builder
        .setTotalTestCount(builder.getPassedTestCount() + builder.getFailedTestCount())
        .setStartTime(TimeUtils.toProtoTimestamp(testInfo.timing().getStartTime()))
        .setEndTime(TimeUtils.toProtoTimestamp(testInfo.timing().getEndTime()))
        .setCreateTime(TimeUtils.toProtoTimestamp(testInfo.timing().getCreateTime()))
        .setUpdateTime(TimeUtils.toProtoTimestamp(testInfo.timing().getModifyTime()))
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
      String commandId = newMultiCommandRequestHandler.getCommandIdOfJob(jobInfo);
      // Add a command attempt
      CommandAttemptDetail commandAttemptDetail =
          generateCommandAttemptDetail(
              jobInfo, jobInfo.tests().getAll().values().iterator().next());
      requestDetail.addCommandAttemptDetails(commandAttemptDetail);
      CommandDetail oldCommandDetail = requestDetail.getCommandDetailsOrThrow(commandId);
      CommandDetail newCommandDetail =
          oldCommandDetail.toBuilder()
              .setPassedTestCount(
                  oldCommandDetail.getPassedTestCount() + commandAttemptDetail.getPassedTestCount())
              .setFailedTestCount(
                  oldCommandDetail.getFailedTestCount() + commandAttemptDetail.getFailedTestCount())
              .setTotalTestCount(
                  oldCommandDetail.getTotalTestCount() + commandAttemptDetail.getTotalTestCount())
              .setUpdateTime(TimeUtils.toProtoTimestamp(Instant.now()))
              .build();

      requestDetail.putCommandDetails(commandId, newCommandDetail);
      requestDetail.setUpdateTime(TimeUtils.toProtoTimestamp(Instant.now()));
      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
    }
  }

  private boolean hasCommandPassed(String commandId, RequestDetail requestDetail) {
    return requestDetail.getCommandAttemptDetailsList().stream()
        .filter(commandAttemptDetail -> commandAttemptDetail.getCommandId().equals(commandId))
        .allMatch(
            commandAttemptDetail -> commandAttemptDetail.getState() == CommandState.COMPLETED);
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
