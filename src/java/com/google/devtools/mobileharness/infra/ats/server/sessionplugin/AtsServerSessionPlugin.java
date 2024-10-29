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
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.AtsServerSessionNotification;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.AtsServerSessionNotification.NotificationCase;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CancelReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandAttemptDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.ErrorReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest.RequestCase;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.CreateJobsResult;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.HandleResultProcessingResult;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionNotificationEvent;
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
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
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

  private static final XtsTradefedRunCancellation CANCELLATION_MESSAGE =
      XtsTradefedRunCancellation.newBuilder()
          .setKillTradefedSignal(2)
          .setCancelReason("User cancelled the test request")
          .build();

  /** Set in {@link #onSessionStarting}. */
  private volatile SessionRequest request;

  private final Object sessionLock = new Object();

  // The source of truth for this session's request states.
  @GuardedBy("sessionLock")
  private final RequestDetail.Builder requestDetail = RequestDetail.newBuilder();

  // All non-tradefed jobs which will be initiated when the session starts. They will be added to
  // the session when all tradefed jobs have ended.
  @GuardedBy("sessionLock")
  private ImmutableList<JobInfo> nonTradefedJobs = null;

  private final SessionInfo sessionInfo;
  private final NewMultiCommandRequestHandler newMultiCommandRequestHandler;
  private final LocalSessionStub localSessionStub;
  private final Clock clock;
  private final TestMessageUtil testMessageUtil;

  @Inject
  AtsServerSessionPlugin(
      SessionInfo sessionInfo,
      NewMultiCommandRequestHandler newMultiCommandRequestHandler,
      LocalSessionStub localSessionStub,
      Clock clock,
      TestMessageUtil testMessageUtil) {
    this.sessionInfo = sessionInfo;
    this.newMultiCommandRequestHandler = newMultiCommandRequestHandler;
    this.localSessionStub = localSessionStub;
    this.clock = clock;
    this.testMessageUtil = testMessageUtil;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws InvalidProtocolBufferException, InterruptedException {
    request =
        sessionInfo.getSessionPluginExecutionConfig().getConfig().unpack(SessionRequest.class);
    if (request.getRequestCase().equals(RequestCase.NEW_MULTI_COMMAND_REQUEST)) {
      synchronized (sessionLock) {
        NewMultiCommandRequest newMultiCommandRequest = request.getNewMultiCommandRequest();
        requestDetail
            .setCreateTime(Timestamps.fromMillis(clock.millis()))
            .setStartTime(Timestamps.fromMillis(clock.millis()))
            .setId(sessionInfo.getSessionId())
            .setOriginalRequest(newMultiCommandRequest)
            .setMaxRetryOnTestFailures(newMultiCommandRequest.getMaxRetryOnTestFailures())
            .addAllCommandInfos(newMultiCommandRequest.getCommandsList());
        try {
          // Check if user initiated cancellation.
          if (requestDetail.getState().equals(RequestState.CANCELED)) {
            return;
          } else {
            requestDetail.setState(RequestState.RUNNING);
          }

          CreateJobsResult createTradefedJobsResult =
              newMultiCommandRequestHandler.createTradefedJobs(newMultiCommandRequest, sessionInfo);
          ImmutableList<JobInfo> tradefedJobs = createTradefedJobsResult.jobInfos();
          requestDetail.setState(createTradefedJobsResult.state());
          createTradefedJobsResult.errorReason().ifPresent(requestDetail::setErrorReason);
          createTradefedJobsResult
              .errorMessage()
              .ifPresent(errorMessage -> appendErrorMessage(requestDetail, errorMessage));
          createTradefedJobsResult.commandDetails().forEach(requestDetail::putCommandDetails);
          if (!createTradefedJobsResult.state().equals(RequestState.RUNNING)) {
            return;
          }

          // Create non-tradefed jobs.
          CreateJobsResult createNonTradefedJobsResult =
              newMultiCommandRequestHandler.createNonTradefedJobs(
                  newMultiCommandRequest, sessionInfo);
          requestDetail.setState(createNonTradefedJobsResult.state());
          nonTradefedJobs = createNonTradefedJobsResult.jobInfos();
          createNonTradefedJobsResult.errorReason().ifPresent(requestDetail::setErrorReason);
          createNonTradefedJobsResult
              .errorMessage()
              .ifPresent(errorMessage -> appendErrorMessage(requestDetail, errorMessage));
          createNonTradefedJobsResult.commandDetails().forEach(requestDetail::putCommandDetails);
          if (!createNonTradefedJobsResult.state().equals(RequestState.RUNNING)) {
            return;
          }

          if (tradefedJobs.isEmpty() && nonTradefedJobs.isEmpty()) {
            requestDetail
                .setState(RequestState.ERROR)
                .setErrorReason(ErrorReason.INVALID_REQUEST)
                .setErrorMessage(
                    String.format(
                        "No jobs were created for session： %s ", sessionInfo.getSessionId()));
            logger.atWarning().log(
                "Session [%s] interrupted: No tradefed or non-tradefed jobs were created.",
                sessionInfo.getSessionId());
            return;
          }

          requestDetail.setUpdateTime(Timestamps.fromMillis(clock.millis()));

          // Ensure non-tradefed jobs are added only if no tradefed jobs exist or all tradefed jobs
          // have ended.
          if (!tradefedJobs.isEmpty()) {
            // Add tradefed jobs to session.
            addJobsToSession(tradefedJobs);
          } else {
            // If no tradefed job was added, add non tradefed jobs directly.
            addJobsToSession(nonTradefedJobs);
          }
        } finally {
          updateSessionPluginOutput();
        }
      }
    }
  }

  @Subscribe
  public void onTestStarting(TestStartingEvent event) {
    resumeRequestDetailFromSessionPluginOutput();

    TestInfo testInfo = event.getTest();
    boolean shouldSendCancellationMessage = false;
    // Sends cancellation test message if necessary.
    synchronized (sessionLock) {
      if (requestDetail.getState().equals(RequestState.CANCELED)) {
        shouldSendCancellationMessage = true;
      }
    }
    if (shouldSendCancellationMessage) {
      sendCancellationMessageToStartedTest(testInfo);
    }
  }

  @Subscribe
  public void onJobEnded(JobEndEvent jobEndEvent) throws InterruptedException {
    resumeRequestDetailFromSessionPluginOutput();

    JobInfo jobInfo = jobEndEvent.getJob();

    // Generate a new commandAttempt for the finished job, and update the command status.
    updateCommandDetail(jobInfo);

    // If all tradefed jobs have ended, create non tradefed jobs.
    synchronized (sessionLock) {
      try {
        if (requestDetail.getState() == RequestState.CANCELED) {
          return;
        }
        // If a non-tradefed tests ended, that means all non-tradefed tests had already been created
        // and no need to create more.
        if (!jobInfo.type().getDriver().equals(TRADEFED_DRIVER_NAME)) {
          return;
        }

        boolean sessionHasUnfinishedTradefedJob =
            sessionInfo.getAllJobs().stream()
                .anyMatch(
                    job ->
                        job.type().getDriver().equals(TRADEFED_DRIVER_NAME)
                            && !job.status().get().equals(TestStatus.DONE));
        if (sessionHasUnfinishedTradefedJob) {
          // If there are still running tradefed jobs, wait and not add non-tradefed jobs.
          return;
        }

        boolean sessionHasNonTradefedJobs =
            sessionInfo.getAllJobs().stream()
                .anyMatch(job -> !job.type().getDriver().equals(TRADEFED_DRIVER_NAME));
        if (sessionHasNonTradefedJobs) {
          // If there are non-tradefed jobs, that means all non-tradefed tests had already been
          // added. So no need to add again. This can happen when two jobs end at the same time.
          return;
        }

        // Non-tradefed jobs might be lost in the resumed sessions. Re-initialize them to ensure
        // they are executed.
        if (nonTradefedJobs == null) {
          CreateJobsResult createNonTradefedJobsResult =
              newMultiCommandRequestHandler.createNonTradefedJobs(
                  requestDetail.getOriginalRequest(), sessionInfo);
          nonTradefedJobs = createNonTradefedJobsResult.jobInfos();
        }
        if (!nonTradefedJobs.isEmpty()) {
          addJobsToSession(nonTradefedJobs);
        }
      } finally {
        updateSessionPluginOutput();
      }
    }
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event) throws InterruptedException {
    resumeRequestDetailFromSessionPluginOutput();

    synchronized (sessionLock) {
      try {
        HandleResultProcessingResult handleResultProcessingResult =
            newMultiCommandRequestHandler.handleResultProcessing(
                sessionInfo,
                requestDetail.getOriginalRequest(),
                requestDetail.getCommandDetailsMap().values());
        // Set final state if not in terminal state.
        if (handleResultProcessingResult.state().equals(RequestState.RUNNING)
            || handleResultProcessingResult.state().equals(RequestState.UNKNOWN)) {
          requestDetail.setState(
              hasSessionPassed(handleResultProcessingResult.commandDetails())
                  ? RequestState.COMPLETED
                  : RequestState.ERROR);
        } else {
          requestDetail.setState(handleResultProcessingResult.state());
        }
        handleResultProcessingResult.errorReason().ifPresent(requestDetail::setErrorReason);
        handleResultProcessingResult
            .errorMessage()
            .ifPresent(errorMessage -> appendErrorMessage(requestDetail, errorMessage));
        requestDetail
            .putAllCommandDetails(handleResultProcessingResult.commandDetails())
            .putAllTestContext(handleResultProcessingResult.testContexts());
      } catch (Throwable e) {
        requestDetail
            .setState(RequestState.ERROR)
            .setErrorReason(ErrorReason.RESULT_PROCESSING_ERROR)
            .setErrorMessage(e.getMessage());
        throw e;
      } finally {
        updateSessionPluginOutput();
      }

      if (canRetrySession(requestDetail.build())) {
        try {
          retrySession();
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log("Failed to trigger retry session.");
        }
      } else if (requestDetail.getState().equals(RequestState.ERROR)
          && requestDetail.getErrorReason().equals(ErrorReason.UNKNOWN_REASON)) {
        requestDetail.setErrorReason(ErrorReason.RESULT_PROCESSING_ERROR);
        requestDetail.setErrorMessage("Failed to process test results.");
        updateSessionPluginOutput();
      }
    }
  }

  @Subscribe
  public void onSessionNotification(SessionNotificationEvent event)
      throws InvalidProtocolBufferException {
    AtsServerSessionNotification notification =
        event.sessionNotification().getNotification().unpack(AtsServerSessionNotification.class);
    logger.atInfo().log("Received notification: %s", shortDebugString(notification));

    // TODO: Support killing jobs here (for non-TF jobs or jobs during allocation).
    if (notification.getNotificationCase() == NotificationCase.CANCEL_SESSION) {
      // send end signal to all running tests.
      ImmutableList<TestInfo> startedTestsBeforeCancellation;
      synchronized (sessionLock) {
        requestDetail.setState(RequestState.CANCELED);
        requestDetail.setCancelReason(CancelReason.REQUEST_API);
        requestDetail.setErrorMessage("Received cancel session notification");
        updateSessionPluginOutput();
        startedTestsBeforeCancellation =
            event.sessionInfo().getAllJobs().stream()
                .map(jobInfo -> jobInfo.tests().getAll().values())
                .flatMap(Collection::stream)
                .filter(testInfo -> testInfo.status().get().equals(TestStatus.RUNNING))
                .collect(toImmutableList());
      }
      for (TestInfo testInfo : startedTestsBeforeCancellation) {
        sendCancellationMessageToStartedTest(testInfo);
      }
    }
  }

  /** Add jobs to the session. */
  @GuardedBy("sessionLock")
  private void addJobsToSession(ImmutableList<JobInfo> jobInfoList) {
    jobInfoList.forEach(sessionInfo::addJob);
  }

  private void sendCancellationMessageToStartedTest(TestInfo testInfo) {
    try {
      testMessageUtil.sendProtoMessageToTest(testInfo, CANCELLATION_MESSAGE);
      logger.atInfo().log("Sent cancel test message to test [%s]", testInfo.locator().getId());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to send cancel test message to test [%s]", testInfo.locator().getId());
    }
  }

  @GuardedBy("sessionLock")
  private void retrySession() throws MobileHarnessException {
    NewMultiCommandRequest.Builder retryRequestBuilder =
        requestDetail.getOriginalRequest().toBuilder();
    if (requestDetail.getTestContextMap().isEmpty()) {
      // No test context, retry like a new request.
      retryRequestBuilder.clearPrevTestContext();
      // Use original command line if exists, in case current request is a retry. Otherwise reuse
      // current request's command line.
      if (requestDetail.getOriginalRequest().hasPrevTestContext()
          && !requestDetail.getOriginalRequest().getPrevTestContext().getCommandLine().isEmpty()) {
        String retryCommandLine =
            requestDetail.getOriginalRequest().getPrevTestContext().getCommandLine();
        retryRequestBuilder
            .clearCommands()
            .addCommands(
                requestDetail.getOriginalRequest().getCommandsList().get(0).toBuilder()
                    .setCommandLine(retryCommandLine)
                    .build());
      }
    } else {
      // Has test context, retry with previous test result as context.
      retryRequestBuilder
          .setPrevTestContext(requestDetail.getTestContextMap().values().iterator().next())
          .clearCommands();
      String retryCommandLine =
          requestDetail.getOriginalRequest().getTestEnvironment().getRetryCommandLine();
      retryRequestBuilder.addCommands(
          requestDetail.getOriginalRequest().getCommandsList().get(0).toBuilder()
              .setCommandLine(retryCommandLine)
              .build());
    }
    retryRequestBuilder.setRetryPreviousSessionId(sessionInfo.getSessionId());
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

  // TODO: create more concrete retry strategy.
  private boolean canRetrySession(RequestDetail requestDetail) {
    return requestDetail.getState().equals(RequestState.ERROR)
        && requestDetail.getMaxRetryOnTestFailures() > 0
        && !requestDetail.getCommandDetailsMap().isEmpty()
        && requestDetail.getCommandDetailsMap().values().iterator().next().getTotalModuleCount()
            != 0;
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
    Instant testStartTime = testInfo.timing().getStartTime();
    Instant testEndTime = testInfo.timing().getEndTime();
    Instant testCreateTime = testInfo.timing().getCreateTime();
    Instant testModifyTime = testInfo.timing().getModifyTime();
    if (testStartTime != null) {
      builder.setStartTime(TimeUtils.toProtoTimestamp(testStartTime));
    }
    if (testEndTime != null) {
      builder.setEndTime(TimeUtils.toProtoTimestamp(testEndTime));
    }
    if (testCreateTime != null) {
      builder.setCreateTime(TimeUtils.toProtoTimestamp(testCreateTime));
    }
    if (testModifyTime != null) {
      builder.setUpdateTime(TimeUtils.toProtoTimestamp(testModifyTime));
    }
    return builder
        .setTotalTestCount(builder.getPassedTestCount() + builder.getFailedTestCount())
        .setState(convertStatusAndResultToCommandState(testInfo.status(), testInfo.result()))
        .build();
  }

  private void updateCommandDetail(JobInfo jobInfo) {
    synchronized (sessionLock) {
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
    synchronized (sessionLock) {
      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
    }
  }

  private void resumeRequestDetailFromSessionPluginOutput() {
    synchronized (sessionLock) {
      sessionInfo
          .getSessionPluginOutput(RequestDetail.class)
          .ifPresent(detail -> requestDetail.clear().mergeFrom(detail));
    }
  }

  private boolean hasSessionPassed(ImmutableMap<String, CommandDetail> commandDetailsMap) {
    return !commandDetailsMap.isEmpty()
        && commandDetailsMap.values().stream()
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

  private static void appendErrorMessage(RequestDetail.Builder requestDetail, String newMessage) {
    requestDetail.setErrorMessage(appendErrorMessage(requestDetail.getErrorMessage(), newMessage));
  }

  private static String appendErrorMessage(String existingMessage, String newMessage) {
    if (existingMessage.isBlank()) {
      return newMessage;
    }
    if (newMessage.isBlank()) {
      return existingMessage;
    }
    return existingMessage + " //--// " + newMessage;
  }
}
