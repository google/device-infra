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

import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.HOURS;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.sessionorchestrator.AtsSessionOrchestrator;
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
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestModuleResult;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.CreateJobsResult;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.HandleResultProcessingResult;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.PrepareSessionRequestResult;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
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
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache.CacheRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache.InvalidateCacheRequest;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedTestModuleResultsMessage;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedTestModuleResults;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.Any;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat.ParseException;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Session Plugin to serve test requests coming from ATS server. */
@WithProto({SessionRequest.class, RequestDetail.class})
final class AtsServerSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String SESSION_PLUGIN_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.ats.server.sessionplugin.AtsServerSessionPlugin";
  private static final String SESSION_MODULE_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.ats.server.sessionplugin.AtsServerSessionPluginModule";
  private static final String SESSION_PLUGIN_LABEL = "AtsServerSessionPlugin";

  private static final String CANCELLATION_REASON = "User cancelled the test request";
  private static final int KILL_TRADEFED_SIGNAL = 2;
  private static final String DEFAULT_RETRY_COMMAND_LINE = "retry --retry 0";

  private final Object sessionLock = new Object();

  private final Supplier<RequestDetail.Builder> requestDetailSupplier =
      Suppliers.memoize(this::resumeRequestDetailFromSessionPluginOutput);

  private final SessionInfo sessionInfo;

  private final NewMultiCommandRequestHandler newMultiCommandRequestHandler;
  private final LocalSessionStub localSessionStub;
  private final Clock clock;
  private final SessionDeviceCache sessionDeviceCache;
  private final ListeningScheduledExecutorService scheduledThreadPool;
  private final AtsSessionOrchestrator orchestrator;

  // Cached after the first prepareSessionRequestInfo() call so subsequent events can reuse it
  // without repeatedly rebuilding CommandDetail, querying host IP, and updating requestDetail,
  // while still supporting lazy initialization if the session was resumed.
  @GuardedBy("sessionLock")
  @Nullable
  private SessionRequestInfo sessionRequestInfo;

  @GuardedBy("itself")
  private final Map<LabLocator, Set<String>> cachedDevices = new HashMap<>();

  @Inject
  AtsServerSessionPlugin(
      SessionInfo sessionInfo,
      NewMultiCommandRequestHandler newMultiCommandRequestHandler,
      LocalSessionStub localSessionStub,
      Clock clock,
      SessionDeviceCache sessionDeviceCache,
      AtsSessionOrchestrator orchestrator) {
    this.sessionInfo = sessionInfo;
    this.newMultiCommandRequestHandler = newMultiCommandRequestHandler;
    this.localSessionStub = localSessionStub;
    this.clock = clock;
    this.sessionDeviceCache = sessionDeviceCache;
    this.orchestrator = orchestrator;
    this.scheduledThreadPool =
        ThreadPools.createStandardScheduledThreadPool(
            "ats-server-session-plugin-device-cache-refresher-" + sessionInfo.getSessionId(), 1);
    logFailure(
        this.scheduledThreadPool.scheduleWithFixedDelay(
            this::refreshCache, /* initialDelay= */ 1, /* delay= */ 1, HOURS),
        Level.WARNING,
        "Failed to refresh device cache");
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
            .setCreateTime(Timestamps.fromMillis(clock.instant().toEpochMilli()))
            .setStartTime(Timestamps.fromMillis(clock.instant().toEpochMilli()))
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
            // Add a dummy command detail that will be updated later. Need to flush the command
            // detail info to UI before creating jobs because job creation can be time consuming and
            // without command detail the UI will show no command is running before the first update
            // from OLCS, which is a issue for retry that has previous attempt result that can
            // temporarily disappear.
            String commandId =
                newMultiCommandRequestHandler.getCommandId(
                    newMultiCommandRequest.getCommands(0), newMultiCommandRequest);
            requestDetail.putCommandDetails(
                commandId,
                CommandDetail.newBuilder()
                    .setId(commandId)
                    .setRequestId(sessionInfo.getSessionId())
                    .setState(CommandState.RUNNING)
                    .setStartTime(Timestamps.fromMillis(clock.instant().toEpochMilli()))
                    .setCreateTime(Timestamps.fromMillis(clock.instant().toEpochMilli()))
                    .setUpdateTime(Timestamps.fromMillis(clock.instant().toEpochMilli()))
                    .setCommandLine(newMultiCommandRequest.getCommands(0).getCommandLine())
                    .build());
          }
          updateSessionPluginOutput(requestDetail);

          // Slate requests execute a standalone job directly without android-xts.zip or
          // AtsSessionOrchestrator.
          if (isSlateRequest(newMultiCommandRequest)) {
            createSlateJobs(requestDetail, newMultiCommandRequest);
          } else {
            createXtsJobs(requestDetail, newMultiCommandRequest);
          }
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log(
              "Failed to start session [%s]", sessionInfo.getSessionId());
          handleSessionError(requestDetail, e);
        } finally {
          requestDetail.setUpdateTime(Timestamps.fromMillis(clock.instant().toEpochMilli()));
          updateSessionPluginOutput(requestDetail);
        }
      }
    }
  }

  @Subscribe
  public void onTestStarting(TestStartingEvent event) {
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetail = requestDetailSupplier.get();
      try {
        TestInfo testInfo = event.getTest();
        ImmutableList<DeviceLocator> deviceLocators =
            event.getAllocation().getAllDeviceLocators().stream()
                .map(
                    com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator
                        ::toNewDeviceLocator)
                .collect(toImmutableList());
        if (!deviceLocators.isEmpty()) {
          cacheDevices(deviceLocators);
        }
        requestDetail
            .setWorkingJobId(testInfo.jobInfo().locator().getId())
            .setWorkingTestId(testInfo.locator().getId());
        // Sends cancellation test message if necessary.
        orchestrator.onTestStarting(testInfo);
      } finally {
        updateSessionPluginOutput(requestDetail);
      }
    }
  }

  @Subscribe
  public void onJobEnded(JobEndEvent jobEndEvent) throws InterruptedException {
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetail = requestDetailSupplier.get();

      try {
        if (requestDetail.getState().equals(RequestState.CANCELED)
            || requestDetail.getState().equals(RequestState.ERROR)
            || isAborted()) {
          return;
        }
        newMultiCommandRequestHandler.handleNonTradefedJobEnd(jobEndEvent.getJob(), requestDetail);
        // Slate requests run a standalone job directly without AtsSessionOrchestrator.
        if (!isSlateRequest(requestDetail.getOriginalRequest())) {
          // Ensure sessionRequestInfo is populated in case the session was resumed after an OLC
          // server restart and onSessionStarting() did not run in this instance.
          if (!ensureSessionRequestInfoPrepared(
              requestDetail, requestDetail.getOriginalRequest())) {
            return;
          }
          orchestrator.onJobEnd(jobEndEvent, this.sessionRequestInfo);
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to handle job ended event for session [%s]", sessionInfo.getSessionId());
        handleSessionError(requestDetail, e);
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
        orchestrator.onSessionEnded();
        HandleResultProcessingResult handleResultProcessingResult =
            newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
        if (isAborted()) {
          requestDetail.setState(RequestState.CANCELED).setCancelReason(CancelReason.REQUEST_API);
        } else if (handleResultProcessingResult.state().equals(RequestState.RUNNING)
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
        invalidateDevicesCache();
        updateSessionPluginOutput(requestDetail);
        newMultiCommandRequestHandler.cleanup(sessionInfo);
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
          requestDetail
              .setState(RequestState.CANCELED)
              .setCancelReason(CancelReason.REQUEST_API)
              .setErrorMessage("Received cancel session notification");
          // Sends end signal to all running tests.
          orchestrator.onSessionCancellation(CANCELLATION_REASON, KILL_TRADEFED_SIGNAL);
        }
      } finally {
        updateSessionPluginOutput(requestDetail);
      }
    }
  }

  @Subscribe
  public void onTestMessage(TestMessageEvent event) {
    XtsTradefedTestModuleResultsMessage.Builder resultsMessage =
        XtsTradefedTestModuleResultsMessage.newBuilder();
    try {
      if (!event.decodeProtoTestMessage(resultsMessage, ExtensionRegistry.getEmptyRegistry())) {
        return;
      }
    } catch (ParseException e) {
      logger.atWarning().withCause(e).log("Failed to decode test message");
      return;
    }

    synchronized (sessionLock) {
      RequestDetail.Builder requestDetail = requestDetailSupplier.get();
      try {
        String testModuleResultsString = resultsMessage.getTestModuleResultsString();
        XtsTradefedTestModuleResults testModuleResults;
        try {
          testModuleResults =
              XtsTradefedTestModuleResults.decodeFromString(testModuleResultsString);
        } catch (RuntimeException e) {
          logger.atWarning().withCause(e).log("Failed to decode Tradefed test module results");
          return;
        }
        Map<String, TestModuleResult> currentResults = new LinkedHashMap<>();
        requestDetail.getTestModuleResultsList().forEach(r -> currentResults.put(r.getName(), r));

        testModuleResults.runningModules().values().stream()
            .map(
                module ->
                    TestModuleResult.newBuilder()
                        .setName(module.id())
                        .setComplete(!module.isRunning())
                        .setDurationMs(module.duration().toMillis())
                        .setPassedTests(module.testsPassed())
                        .setFailedTests(module.testsFailed())
                        .setTotalTests(module.testsExpected())
                        .build())
            .forEach(r -> currentResults.put(r.getName(), r));

        requestDetail.clearTestModuleResults().addAllTestModuleResults(currentResults.values());
      } finally {
        updateSessionPluginOutput(requestDetail);
      }
    }
  }

  /**
   * Prepares the {@link SessionRequestInfo} for the request and delegates xTS job creation and
   * initial scheduling to {@link AtsSessionOrchestrator#startSession}.
   */
  @GuardedBy("sessionLock")
  void createXtsJobs(
      RequestDetail.Builder requestDetail, NewMultiCommandRequest newMultiCommandRequest)
      throws MobileHarnessException, InterruptedException {
    if (!ensureSessionRequestInfoPrepared(requestDetail, newMultiCommandRequest)) {
      return;
    }
    orchestrator.startSession(this.sessionRequestInfo);
  }

  /**
   * Ensures {@link #sessionRequestInfo} is prepared for the session, populating it via {@link
   * NewMultiCommandRequestHandler#prepareSessionRequestInfo} if not already cached.
   *
   * @return true if {@link #sessionRequestInfo} is ready and the request is {@link
   *     RequestState#RUNNING}, false otherwise
   */
  @GuardedBy("sessionLock")
  private boolean ensureSessionRequestInfoPrepared(
      RequestDetail.Builder requestDetail, NewMultiCommandRequest newMultiCommandRequest)
      throws InterruptedException {
    if (this.sessionRequestInfo != null) {
      return true;
    }
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(
            newMultiCommandRequest, sessionInfo);
    updateRequestDetailWithPrepareResult(requestDetail, prepareResult);
    if (!prepareResult.state().equals(RequestState.RUNNING)
        || prepareResult.sessionRequestInfo().isEmpty()) {
      return false;
    }
    this.sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    return true;
  }

  /** Creates and adds Slate jobs to the session. */
  @GuardedBy("sessionLock")
  void createSlateJobs(
      RequestDetail.Builder requestDetail, NewMultiCommandRequest newMultiCommandRequest)
      throws InterruptedException {
    CreateJobsResult createSlateJobsResult =
        newMultiCommandRequestHandler.createSlateJobs(newMultiCommandRequest, sessionInfo);
    updateRequestDetailWithCreateJobsResult(requestDetail, createSlateJobsResult);
    if (!createSlateJobsResult.state().equals(RequestState.RUNNING)) {
      return;
    }

    // One slate job per session.
    createSlateJobsResult.jobInfos().forEach(sessionInfo::addJob);
  }

  private void updateRequestDetailWithCreateJobsResult(
      RequestDetail.Builder requestDetail, CreateJobsResult createJobsResult) {
    requestDetail.setState(createJobsResult.state());
    createJobsResult.errorReason().ifPresent(requestDetail::setErrorReason);
    createJobsResult
        .errorMessage()
        .ifPresent(errorMessage -> appendErrorMessage(requestDetail, errorMessage));
    createJobsResult.commandDetails().forEach(requestDetail::putCommandDetails);
  }

  /**
   * Updates {@code requestDetail} with state, errors, and command details from {@code
   * prepareResult}.
   */
  private void updateRequestDetailWithPrepareResult(
      RequestDetail.Builder requestDetail, PrepareSessionRequestResult prepareResult) {
    requestDetail.setState(prepareResult.state());
    prepareResult.errorReason().ifPresent(requestDetail::setErrorReason);
    prepareResult
        .errorMessage()
        .ifPresent(errorMessage -> appendErrorMessage(requestDetail, errorMessage));
    prepareResult
        .commandDetail()
        .ifPresent(
            commandDetail -> {
              String commandId = commandDetail.getId();
              // During onSessionStarting(), requestDetail only holds a placeholder CommandDetail
              // (without originalCommandInfo), which should be replaced with the full CommandDetail
              // from prepareResult. Conversely, when prepareSessionRequestInfo() is called lazily
              // during onJobEnded() on a resumed session, requestDetail already has the persisted
              // CommandDetail (with originalCommandInfo), so skip overwriting it to preserve its
              // original start and create timestamps.
              if (prepareResult.state().equals(RequestState.RUNNING)
                  && requestDetail.containsCommandDetails(commandId)
                  && requestDetail.getCommandDetailsMap().get(commandId).hasOriginalCommandInfo()) {
                return;
              }
              requestDetail.putCommandDetails(commandId, commandDetail);
            });
  }

  @GuardedBy("sessionLock")
  private boolean isAborted() {
    return sessionInfo
        .getSessionProperty(SessionProperties.PROPERTY_KEY_SESSION_ABORTED_WHEN_RUNNING)
        .map(Boolean::parseBoolean)
        .orElse(false);
  }

  /**
   * Records session failure state and error details in {@code requestDetail} when orchestration or
   * job creation throws a {@link MobileHarnessException}.
   */
  @GuardedBy("sessionLock")
  private void handleSessionError(RequestDetail.Builder requestDetail, MobileHarnessException e) {
    if (requestDetail.getState().equals(RequestState.ERROR)) {
      return;
    }
    // When neither Tradefed nor non-Tradefed jobs match the command, record the request-level error
    // message without marking the CommandDetail as ERROR.
    if (e.getErrorId() == InfraErrorId.XTS_NO_JOB_CREATED_FOR_SESSION) {
      requestDetail
          .setState(RequestState.ERROR)
          .setErrorReason(ErrorReason.INVALID_REQUEST)
          .setErrorMessage(
              String.format("No jobs were created for session: %s ", sessionInfo.getSessionId()));
      return;
    }
    // For all other orchestration or job creation errors (e.g., invalid test resources or failures
    // during deferred main job creation after SETUP), mark both the request and command as ERROR.
    ErrorReason errorReason = newMultiCommandRequestHandler.getErrorReason(e);
    String commandLine = requestDetail.getOriginalRequest().getCommands(0).getCommandLine();
    requestDetail.setState(RequestState.ERROR).setErrorReason(errorReason);
    appendErrorMessage(
        requestDetail,
        String.format(
            "INVALID_COMMAND_%s with error: %s", commandLine, MoreThrowables.shortDebugString(e)));
    for (Map.Entry<String, CommandDetail> entry :
        ImmutableMap.copyOf(requestDetail.getCommandDetailsMap()).entrySet()) {
      requestDetail.putCommandDetails(
          entry.getKey(),
          entry.getValue().toBuilder()
              .setState(CommandState.ERROR)
              .setErrorReason(errorReason)
              .setErrorMessage(getStackTraceAsString(e))
              .build());
    }
  }

  private static boolean isSlateRequest(NewMultiCommandRequest request) {
    return request.getCommandsCount() > 0
        && request.getCommands(0).getCommandLine().startsWith("slate");
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
      if (retryCommandLine.isEmpty()) {
        retryCommandLine = DEFAULT_RETRY_COMMAND_LINE;
      }
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
    return requestDetail.getState() == RequestState.COMPLETED
        && requestDetail.getMaxRetryOnTestFailures() > 0
        && hasSessionCompletedWithFailure(requestDetail);
  }

  @GuardedBy("sessionLock")
  private void updateSessionPluginOutput(RequestDetail.Builder requestDetail) {
    RequestDetail latestRequestDetail = requestDetail.build();
    sessionInfo.setSessionPluginOutput(unused -> latestRequestDetail, RequestDetail.class);
  }

  /**
   * Resumes or initializes the {@link RequestDetail.Builder} from session plugin output.
   *
   * <p>Falls back to unpacking the original {@link NewMultiCommandRequest} from {@link
   * SessionInfo#getSessionPluginExecutionConfig()} if missing from output (e.g. server restart
   * before {@link #onSessionStarting} flushes output, or in tests), ensuring subsequent job
   * re-initialization has access to the original request parameters.
   */
  private RequestDetail.Builder resumeRequestDetailFromSessionPluginOutput() {
    // No need to use sessionLock here because the caller already holds the lock.
    // It's added only to satisfy ErrorProne GuardedBy analysis.
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetailBuilder = RequestDetail.newBuilder();
      sessionInfo
          .getSessionPluginOutput(RequestDetail.class)
          .ifPresent(requestDetailBuilder::mergeFrom);
      if (requestDetailBuilder
          .getOriginalRequest()
          .equals(NewMultiCommandRequest.getDefaultInstance())) {
        try {
          if (sessionInfo.getSessionPluginExecutionConfig() != null
              && sessionInfo.getSessionPluginExecutionConfig().hasConfig()
              && sessionInfo
                  .getSessionPluginExecutionConfig()
                  .getConfig()
                  .is(SessionRequest.class)) {
            SessionRequest sessionRequest =
                sessionInfo
                    .getSessionPluginExecutionConfig()
                    .getConfig()
                    .unpack(SessionRequest.class);
            requestDetailBuilder.setOriginalRequest(sessionRequest.getNewMultiCommandRequest());
          }
        } catch (InvalidProtocolBufferException e) {
          logger.atWarning().withCause(e).log(
              "Failed to unpack SessionRequest from session plugin execution config");
        }
      }
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
    if (newMessage.equals(existingMessage)) {
      return existingMessage;
    }
    return existingMessage + " //--// " + newMessage;
  }

  private enum CacheAction {
    CACHE,
    REFRESH,
    INVALIDATE
  }

  /**
   * Caches the allocated devices for the session.
   *
   * <p>This caches devices to solve the problem where a session requires a set of devices and
   * generates requirements for multiple jobs at the beginning. When one job finishes and the next
   * job is submitted to {@link SessionInfo}, some devices might temporarily or permanently become
   * undetected by the infrastructure (e.g., due to USB reset issues), causing the subsequent job
   * allocation to fail.
   *
   * <p>With the cache, {@code SessionDeviceCache} accesses the lab-side {@code XtsDeviceCache} via
   * RPC in order to keep the local device runner alive for the cached devices. Throughout the
   * cached period, the devices transition normally between {@code IDLE} and {@code BUSY} states in
   * the infrastructure. This delegates the handling of whether a device is truly offline to the
   * specific driver and plugin, instead of having the infrastructure release them prematurely.
   *
   * <p>The cache is managed as follows:
   *
   * <ul>
   *   <li>Initially registered during {@code onTestStarting} when the devices are allocated.
   *   <li>Periodically refreshed with a fixed delay of 1 hour (using a 3-hour lease duration) to
   *       prevent permanent lockups in case of OLC server crashes.
   *   <li>Fully invalidated and released when the session finishes or gets aborted (via {@code
   *       onSessionEnded}).
   * </ul>
   */
  private void cacheDevices(ImmutableList<DeviceLocator> deviceLocators) {
    Map<LabLocator, List<String>> newDevicesToCache = new HashMap<>();
    synchronized (cachedDevices) {
      for (DeviceLocator deviceLocator : deviceLocators) {
        LabLocator lab = deviceLocator.labLocator();
        String id = deviceLocator.id();
        Set<String> ids = cachedDevices.computeIfAbsent(lab, k -> new HashSet<>());
        if (ids.add(id)) {
          newDevicesToCache.computeIfAbsent(lab, k -> new ArrayList<>()).add(id);
        }
      }
    }
    doCacheDevices(newDevicesToCache, CacheAction.CACHE);
  }

  private void refreshCache() {
    ImmutableMap<LabLocator, ImmutableList<String>> devicesToRefresh;
    synchronized (cachedDevices) {
      devicesToRefresh =
          cachedDevices.entrySet().stream()
              .filter(entry -> !entry.getValue().isEmpty())
              .collect(
                  toImmutableMap(
                      Map.Entry::getKey, entry -> ImmutableList.copyOf(entry.getValue())));
    }
    doCacheDevices(devicesToRefresh, CacheAction.REFRESH);
  }

  private void invalidateDevicesCache() {
    scheduledThreadPool.shutdown();
    ImmutableMap<LabLocator, ImmutableList<String>> devicesToInvalidate;
    synchronized (cachedDevices) {
      devicesToInvalidate =
          cachedDevices.entrySet().stream()
              .filter(entry -> !entry.getValue().isEmpty())
              .collect(
                  toImmutableMap(
                      Map.Entry::getKey, entry -> ImmutableList.copyOf(entry.getValue())));
      cachedDevices.clear();
    }
    doCacheDevices(devicesToInvalidate, CacheAction.INVALIDATE);
  }

  private void doCacheDevices(
      Map<LabLocator, ? extends Collection<String>> devicesToCache, CacheAction action) {
    for (Map.Entry<LabLocator, ? extends Collection<String>> entry : devicesToCache.entrySet()) {
      LabLocator lab = entry.getKey();
      ImmutableList<String> ids = ImmutableList.copyOf(entry.getValue());
      try {
        switch (action) {
          case CACHE -> {
            logger.atInfo().log(
                "Caching newly allocated devices %s on lab %s for session %s",
                ids, lab, sessionInfo.getSessionId());
            sessionDeviceCache.cache(
                new CacheRequest(
                    lab,
                    ids,
                    /* timeout= */ Duration.ofHours(3),
                    "xts",
                    sessionInfo.getSessionId()));
          }
          case REFRESH -> {
            logger.atInfo().log(
                "Refreshing device caches: %s on lab %s for session %s",
                ids, lab, sessionInfo.getSessionId());
            sessionDeviceCache.cache(
                new CacheRequest(
                    lab,
                    ids,
                    /* timeout= */ Duration.ofHours(3),
                    "xts",
                    sessionInfo.getSessionId()));
          }
          case INVALIDATE -> {
            logger.atInfo().log(
                "Invalidating device caches: %s on lab %s for session %s",
                ids, lab, sessionInfo.getSessionId());
            sessionDeviceCache.invalidateCache(
                new InvalidateCacheRequest(lab, ids, "xts", sessionInfo.getSessionId()));
          }
        }
      } catch (MobileHarnessException | InterruptedException e) {
        if (MoreThrowables.isInterruption(e)) {
          Thread.currentThread().interrupt();
        }
        switch (action) {
          case CACHE ->
              logger.atWarning().withCause(e).log(
                  "Failed to cache devices %s on lab %s for session %s",
                  ids, lab, sessionInfo.getSessionId());
          case REFRESH ->
              logger.atWarning().withCause(e).log(
                  "Failed to refresh cache for devices %s on lab %s for session %s",
                  ids, lab, sessionInfo.getSessionId());
          case INVALIDATE ->
              logger.atWarning().withCause(e).log(
                  "Failed to invalidate cache for devices %s on lab %s for session %s",
                  ids, lab, sessionInfo.getSessionId());
        }
      }
    }
  }
}
