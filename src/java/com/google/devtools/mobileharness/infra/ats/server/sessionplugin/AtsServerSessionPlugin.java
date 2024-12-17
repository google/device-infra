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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.AtsServerSessionNotification;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.AtsServerSessionNotification.NotificationCase;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CancelReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.ErrorReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetailOrBuilder;
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
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

  private final Object sessionLock = new Object();

  private final Supplier<RequestDetail.Builder> requestDetailSupplier =
      Suppliers.memoize(this::resumeRequestDetailFromSessionPluginOutput);

  // All non-tradefed jobs which will be initiated when the session starts. They will be added to
  // the session when all tradefed jobs have ended.
  @GuardedBy("sessionLock")
  private ImmutableList<JobInfo> nonTradefedJobs = null;

  @SuppressWarnings("PreferredInterfaceType")
  @GuardedBy("sessionLock")
  private List<JobInfo> tradefedJobs = null;

  @GuardedBy("sessionLock")
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
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetail = requestDetailSupplier.get();
      SessionRequest request =
          sessionInfo.getSessionPluginExecutionConfig().getConfig().unpack(SessionRequest.class);
      if (request.getRequestCase().equals(RequestCase.NEW_MULTI_COMMAND_REQUEST)) {
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
          tradefedJobs = new ArrayList<>(createTradefedJobsResult.jobInfos());
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
            // Add one tradefed job to session, if we have multiple TF jobs execute serially. The
            // following jobs will be added when the previous job hits onJobEnded.
            sessionInfo.addJob(tradefedJobs.remove(0));
          } else {
            // If no tradefed job was added, add non tradefed jobs directly.
            nonTradefedJobs.forEach(sessionInfo::addJob);
          }
        } finally {
          updateSessionPluginOutput(requestDetail);
        }
      }
    }
  }

  @Subscribe
  public void onTestStarting(TestStartingEvent event) {
    // Sends cancellation test message if necessary.
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetail = requestDetailSupplier.get();
      try {

        TestInfo testInfo = event.getTest();
        boolean shouldSendCancellationMessage = false;
        if (requestDetail.getState().equals(RequestState.CANCELED)) {
          shouldSendCancellationMessage = true;
        }

        if (shouldSendCancellationMessage) {
          sendCancellationMessageToStartedTest(testInfo);
        }
      } finally {
        updateSessionPluginOutput(requestDetail);
      }
    }
  }

  @Subscribe
  public void onJobEnded(JobEndEvent jobEndEvent) throws InterruptedException {
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetail = requestDetailSupplier.get();

      JobInfo jobInfo = jobEndEvent.getJob();
      // If all tradefed jobs have ended, create non tradefed jobs.
      try {
        // Tradefed jobs might be lost in the resumed sessions. Re-initialize them to ensure
        // they are executed and also remove the jobs that are already added to the session.
        if (tradefedJobs == null) {
          CreateJobsResult createTradefedJobsResult =
              newMultiCommandRequestHandler.createTradefedJobs(
                  requestDetail.getOriginalRequest(), sessionInfo);
          tradefedJobs = createTradefedJobsResult.jobInfos();
          List<JobInfo> triggeredTradefedJobs = sessionInfo.getAllJobs();
          tradefedJobs.removeIf(
              job ->
                  triggeredTradefedJobs.stream()
                      .anyMatch(
                          triggeredJob ->
                              job.locator().getName().equals(triggeredJob.locator().getName())));
        }
        // if we have multiple TF jobs execute serially, we need to only add the job one by one
        // after the previous job is ended.
        if (!tradefedJobs.isEmpty()) {
          sessionInfo.addJob(tradefedJobs.remove(0));
        }
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
          nonTradefedJobs.forEach(sessionInfo::addJob);
        }
      } finally {
        updateSessionPluginOutput(requestDetail);
      }
    }
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event) throws InterruptedException {
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetail = requestDetailSupplier.get();

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
              hasSessionCompleted(handleResultProcessingResult.commandDetails())
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

        if (canRetrySession(requestDetail)) {
          try {
            String nextAttemptSessionId = retrySession(requestDetail);
            requestDetail.setNextAttemptSessionId(nextAttemptSessionId);
          } catch (MobileHarnessException e) {
            logger.atWarning().withCause(e).log("Failed to trigger retry session.");
          }
        } else if (requestDetail.getState().equals(RequestState.ERROR)
            && requestDetail.getErrorReason().equals(ErrorReason.UNKNOWN_REASON)
            && !hasSessionCompletedWithFailure(requestDetail)) {
          requestDetail.setErrorReason(ErrorReason.RESULT_PROCESSING_ERROR);
          requestDetail.setErrorMessage("Failed to process test results.");
        }
      } catch (Throwable e) {
        requestDetail
            .setState(RequestState.ERROR)
            .setErrorReason(ErrorReason.RESULT_PROCESSING_ERROR)
            .setErrorMessage(e.getMessage() == null ? "Empty error message" : e.getMessage());
        throw e;
      } finally {
        updateSessionPluginOutput(requestDetail);
      }
    }
  }

  @Subscribe
  public void onSessionNotification(SessionNotificationEvent event)
      throws InvalidProtocolBufferException {
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetail = requestDetailSupplier.get();

      try {
        AtsServerSessionNotification notification =
            event
                .sessionNotification()
                .getNotification()
                .unpack(AtsServerSessionNotification.class);
        logger.atInfo().log("Received notification: %s", shortDebugString(notification));

        // TODO: Support killing jobs here (for non-TF jobs or jobs during allocation).
        if (notification.getNotificationCase() == NotificationCase.CANCEL_SESSION) {
          // send end signal to all running tests.
          requestDetail.setState(RequestState.CANCELED);
          requestDetail.setCancelReason(CancelReason.REQUEST_API);
          requestDetail.setErrorMessage("Received cancel session notification");
          ImmutableList<TestInfo> startedTestsBeforeCancellation =
              event.sessionInfo().getAllJobs().stream()
                  .map(jobInfo -> jobInfo.tests().getAll().values())
                  .flatMap(Collection::stream)
                  .filter(testInfo -> testInfo.status().get().equals(TestStatus.RUNNING))
                  .collect(toImmutableList());
          for (TestInfo testInfo : startedTestsBeforeCancellation) {
            sendCancellationMessageToStartedTest(testInfo);
          }
        }
      } finally {
        updateSessionPluginOutput(requestDetail);
      }
    }
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
  private String retrySession(RequestDetailOrBuilder requestDetail) throws MobileHarnessException {
    NewMultiCommandRequest originalRequest = requestDetail.getOriginalRequest();
    NewMultiCommandRequest.Builder retryRequestBuilder = originalRequest.toBuilder();
    if (requestDetail.getTestContextMap().isEmpty()) {
      // No test context, retry like a new request.
      retryRequestBuilder.clearPrevTestContext();
      // Use original command line if exists, in case current request is a retry. Otherwise reuse
      // current request's command line.
      if (originalRequest.hasPrevTestContext()
          && !originalRequest.getPrevTestContext().getCommandLine().isEmpty()) {
        String retryCommandLine = originalRequest.getPrevTestContext().getCommandLine();
        retryRequestBuilder
            .clearCommands()
            .addCommands(
                originalRequest.getCommandsList().get(0).toBuilder()
                    .setCommandLine(retryCommandLine)
                    .build());
      }
    } else {
      // Has test context, retry with previous test result as context.
      retryRequestBuilder
          .setPrevTestContext(requestDetail.getTestContextMap().values().iterator().next())
          .clearCommands();
      String retryCommandLine = originalRequest.getTestEnvironment().getRetryCommandLine();
      retryRequestBuilder.addCommands(
          originalRequest.getCommandsList().get(0).toBuilder()
              .setCommandLine(retryCommandLine)
              .build());
    }
    retryRequestBuilder.setRetryPreviousSessionId(sessionInfo.getSessionId());
    retryRequestBuilder.addAllPreviousSessionIds(sessionInfo.getSessionId());
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
    return nextAttemptSessionId;
  }

  // TODO: create more concrete retry strategy.
  private static boolean canRetrySession(RequestDetailOrBuilder requestDetail) {
    return requestDetail.getMaxRetryOnTestFailures() > 0
        && hasSessionCompletedWithFailure(requestDetail);
  }

  @GuardedBy("sessionLock")
  private void updateSessionPluginOutput(RequestDetail.Builder requestDetail) {
    RequestDetail latestRequestDetail = requestDetail.build();
    sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
  }

  private RequestDetail.Builder resumeRequestDetailFromSessionPluginOutput() {
    // No need to use sessionLock here because the caller already holds the lock.
    // It's added only to work around
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetailBuilder = RequestDetail.newBuilder();
      sessionInfo
          .getSessionPluginOutput(RequestDetail.class)
          .ifPresent(requestDetailBuilder::mergeFrom);
      return requestDetailBuilder;
    }
  }

  private static boolean hasSessionCompleted(
      ImmutableMap<String, CommandDetail> commandDetailsMap) {
    return !commandDetailsMap.isEmpty()
        && commandDetailsMap.values().stream()
            .allMatch(commandDetail -> commandDetail.getState() == CommandState.COMPLETED);
  }

  private static boolean hasSessionCompletedWithFailure(RequestDetailOrBuilder requestDetail) {
    ImmutableMap<String, CommandDetail> commandDetailsMap =
        ImmutableMap.copyOf(requestDetail.getCommandDetailsMap());
    return !commandDetailsMap.isEmpty()
        && hasSessionCompleted(commandDetailsMap)
        && commandDetailsMap.values().stream()
            .anyMatch(commandDetail -> commandDetail.getFailedTestCount() > 0);
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
