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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.HOURS;

import com.google.common.base.Ascii;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ModuleRunResult;
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
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedTestModuleResultsMessage;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedTestModuleResults;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.Any;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.PhaseSkippableDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.nio.file.Path;
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
  private static final String TRADEFED_DRIVER_NAME = "TradefedTest";
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
  private static final String DEFAULT_RETRY_COMMAND_LINE = "retry --retry 0";

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
  @Nullable
  private JobInfo setupJob = null;

  @GuardedBy("sessionLock")
  private boolean isTeardownJobInitialized = false;

  @GuardedBy("sessionLock")
  @Nullable
  private JobInfo teardownJob = null;

  @GuardedBy("sessionLock")
  @Nullable
  private String runningSetupJobId = null;

  @GuardedBy("sessionLock")
  @Nullable
  private String runningTeardownJobId = null;

  private final SessionInfo sessionInfo;

  private final NewMultiCommandRequestHandler newMultiCommandRequestHandler;
  private final LocalSessionStub localSessionStub;
  private final Clock clock;
  private final TestMessageUtil testMessageUtil;
  private final LocalFileUtil localFileUtil;
  private final SessionDeviceCache sessionDeviceCache;
  private final ListeningScheduledExecutorService scheduledThreadPool;

  @GuardedBy("itself")
  private final Map<LabLocator, Set<String>> cachedDevices = new HashMap<>();

  @Inject
  AtsServerSessionPlugin(
      SessionInfo sessionInfo,
      NewMultiCommandRequestHandler newMultiCommandRequestHandler,
      LocalSessionStub localSessionStub,
      Clock clock,
      TestMessageUtil testMessageUtil,
      LocalFileUtil localFileUtil,
      SessionDeviceCache sessionDeviceCache) {
    this.sessionInfo = sessionInfo;
    this.newMultiCommandRequestHandler = newMultiCommandRequestHandler;
    this.localSessionStub = localSessionStub;
    this.clock = clock;
    this.testMessageUtil = testMessageUtil;
    this.localFileUtil = localFileUtil;
    this.sessionDeviceCache = sessionDeviceCache;
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

          if (newMultiCommandRequest.getCommands(0).getCommandLine().startsWith("slate")) {
            createSlateJobs(requestDetail, newMultiCommandRequest);
          } else {
            createXtsJobs(requestDetail, newMultiCommandRequest);
          }
        } finally {
          requestDetail.setUpdateTime(Timestamps.fromMillis(clock.instant().toEpochMilli()));
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
        ImmutableList<DeviceLocator> deviceLocators =
            event.getAllocation().getAllDeviceLocators().stream()
                .map(
                    com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator
                        ::toNewDeviceLocator)
                .collect(toImmutableList());
        if (!deviceLocators.isEmpty()) {
          cacheDevices(deviceLocators);
        }
        requestDetail.setWorkingJobId(testInfo.jobInfo().locator().getId());
        requestDetail.setWorkingTestId(testInfo.locator().getId());
      } finally {
        updateSessionPluginOutput(requestDetail);
      }
    }
  }

  @Subscribe
  public void onJobEnded(JobEndEvent jobEndEvent)
      throws InterruptedException, MobileHarnessException {
    synchronized (sessionLock) {
      RequestDetail.Builder requestDetail = requestDetailSupplier.get();

      JobInfo jobInfo = jobEndEvent.getJob();

      try {
        handleXtsJobEnd(jobInfo, requestDetail);
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

  @GuardedBy("sessionLock")
  void createXtsJobs(
      RequestDetail.Builder requestDetail, NewMultiCommandRequest newMultiCommandRequest)
      throws InterruptedException {
    if (!createTradefedJobs(requestDetail, newMultiCommandRequest)
        || !createNonTradefedJobs(requestDetail, newMultiCommandRequest)
        || hasNoJobsCreated(requestDetail)
        || !createSetupJobs(requestDetail, newMultiCommandRequest)
        || !createTeardownJobs(requestDetail, newMultiCommandRequest)) {
      return;
    }

    if (setupJob != null) {
      // If a setup job was created, schedule it first while deferring main jobs, and the teardown
      // job.
      addSetupJob(setupJob);
    } else {
      addMainJobs(requestDetail);
    }
  }

  /**
   * Creates Tradefed jobs from the request.
   *
   * <p>This method performs the following steps:
   *
   * <ul>
   *   <li>Generates Tradefed jobs via {@link NewMultiCommandRequestHandler#createTradefedJobs}.
   *   <li>Updates {@link RequestDetail} with the resulting creation state and error messages.
   *   <li>Saves the generated jobs in {@link #tradefedJobs}.
   * </ul>
   *
   * @return true if the creation state is {@link RequestState#RUNNING}, false otherwise
   */
  @GuardedBy("sessionLock")
  private boolean createTradefedJobs(
      RequestDetail.Builder requestDetail, NewMultiCommandRequest newMultiCommandRequest)
      throws InterruptedException {
    CreateJobsResult createTradefedJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(newMultiCommandRequest, sessionInfo);
    tradefedJobs = new ArrayList<>(createTradefedJobsResult.jobInfos());
    updateRequestDetailWithCreateJobsResult(requestDetail, createTradefedJobsResult);
    return createTradefedJobsResult.state().equals(RequestState.RUNNING);
  }

  /**
   * Creates non-Tradefed jobs from the request.
   *
   * <p>This method performs the following steps:
   *
   * <ul>
   *   <li>Generates non-Tradefed jobs via {@link
   *       NewMultiCommandRequestHandler#createNonTradefedJobs}.
   *   <li>Updates {@link RequestDetail} with the resulting creation state and error messages.
   *   <li>Saves the generated jobs in {@link #nonTradefedJobs}.
   * </ul>
   *
   * @return true if the creation state is {@link RequestState#RUNNING}, false otherwise
   */
  @GuardedBy("sessionLock")
  private boolean createNonTradefedJobs(
      RequestDetail.Builder requestDetail, NewMultiCommandRequest newMultiCommandRequest)
      throws InterruptedException {
    CreateJobsResult createNonTradefedJobsResult =
        newMultiCommandRequestHandler.createNonTradefedJobs(newMultiCommandRequest, sessionInfo);
    nonTradefedJobs = createNonTradefedJobsResult.jobInfos();
    updateRequestDetailWithCreateJobsResult(requestDetail, createNonTradefedJobsResult);
    return createNonTradefedJobsResult.state().equals(RequestState.RUNNING);
  }

  /**
   * Creates setup jobs from the request.
   *
   * <p>This method performs the following steps:
   *
   * <ul>
   *   <li>Generates setup jobs via {@link NewMultiCommandRequestHandler#createSetupJobs}.
   *   <li>Updates {@link RequestDetail} with the resulting creation state and error messages.
   *   <li>Saves the generated job in {@link #setupJob}.
   * </ul>
   *
   * @return true if the creation state is {@link RequestState#RUNNING}, false otherwise
   */
  @GuardedBy("sessionLock")
  private boolean createSetupJobs(
      RequestDetail.Builder requestDetail, NewMultiCommandRequest newMultiCommandRequest)
      throws InterruptedException {
    CreateJobsResult createSetupJobsResult =
        newMultiCommandRequestHandler.createSetupJobs(newMultiCommandRequest, sessionInfo);
    updateRequestDetailWithCreateJobsResult(requestDetail, createSetupJobsResult);
    setupJob = createSetupJobsResult.jobInfos().stream().findFirst().orElse(null);
    return createSetupJobsResult.state().equals(RequestState.RUNNING);
  }

  /**
   * Creates teardown jobs from the request.
   *
   * <p>This method performs the following steps:
   *
   * <ul>
   *   <li>Generates teardown jobs via {@link NewMultiCommandRequestHandler#createTeardownJobs}.
   *   <li>Updates {@link RequestDetail} with the resulting creation state and error messages.
   *   <li>Saves the generated job in {@link #teardownJob}.
   * </ul>
   *
   * @return true if the creation state is {@link RequestState#RUNNING}, false otherwise
   */
  @GuardedBy("sessionLock")
  private boolean createTeardownJobs(
      RequestDetail.Builder requestDetail, NewMultiCommandRequest newMultiCommandRequest)
      throws InterruptedException {
    CreateJobsResult createTeardownJobsResult =
        newMultiCommandRequestHandler.createTeardownJobs(newMultiCommandRequest, sessionInfo);
    updateRequestDetailWithCreateJobsResult(requestDetail, createTeardownJobsResult);
    isTeardownJobInitialized = true;
    teardownJob = createTeardownJobsResult.jobInfos().stream().findFirst().orElse(null);
    return createTeardownJobsResult.state().equals(RequestState.RUNNING);
  }

  /**
   * Verifies that at least one Tradefed or non-Tradefed job was created for the session, updating
   * the request state and logging an error if none were created.
   *
   * @return true if no jobs were created, false otherwise
   */
  @GuardedBy("sessionLock")
  private boolean hasNoJobsCreated(RequestDetail.Builder requestDetail) {
    if (tradefedJobs.isEmpty() && nonTradefedJobs.isEmpty()) {
      requestDetail
          .setState(RequestState.ERROR)
          .setErrorReason(ErrorReason.INVALID_REQUEST)
          .setErrorMessage(
              String.format("No jobs were created for session: %s ", sessionInfo.getSessionId()));
      logger.atWarning().log(
          "Session [%s] interrupted: No tradefed or non-tradefed jobs were created.",
          sessionInfo.getSessionId());
      return true;
    }
    return false;
  }

  /**
   * Adds the setup job to the session and records its execution ID in {@link #runningSetupJobId}.
   */
  @GuardedBy("sessionLock")
  private void addSetupJob(JobInfo setupJobInfo) {
    logger.atInfo().log("Adding setup job [%s].", setupJobInfo.locator().getId());
    runningSetupJobId = setupJobInfo.locator().getId();
    sessionInfo.addJob(setupJobInfo);
  }

  /**
   * Schedules main Tradefed and non-Tradefed jobs into the session.
   *
   * <p>The scheduling behavior follows these rules:
   *
   * <ul>
   *   <li><b>Tradefed jobs present:</b> Schedules the first Tradefed job. Subsequent Tradefed jobs
   *       execute sequentially when the previous one ends in {@link #handleXtsJobEnd}, after which
   *       any non-Tradefed jobs will be scheduled.
   *   <li><b>Only non-Tradefed jobs present:</b> Adds all non-Tradefed jobs directly.
   *   <li><b>No main jobs present:</b> Falls back to adding the teardown job if present.
   * </ul>
   */
  @GuardedBy("sessionLock")
  private void addMainJobs(RequestDetail.Builder requestDetail) throws InterruptedException {
    ensureTradefedJobsInitialized(requestDetail);
    if (!tradefedJobs.isEmpty()) {
      // Add one tradefed job to session, if we have multiple TF jobs execute serially. The
      // following jobs will be added when the previous job hits onJobEnded.
      sessionInfo.addJob(tradefedJobs.remove(0));
    } else {
      ensureNonTradefedJobsInitialized(requestDetail);
      if (!hasAnyNonTradefedJobs() && !nonTradefedJobs.isEmpty()) {
        // If no tradefed job was added, add non tradefed jobs directly.
        nonTradefedJobs.forEach(sessionInfo::addJob);
      } else {
        addTeardownJobIfAny(requestDetail);
      }
    }
  }

  /**
   * Adds the teardown job to the session if one has not already been added.
   *
   * <p>Before adding the teardown job, this method:
   *
   * <ul>
   *   <li>Checks whether a teardown job is already present in the session to prevent duplicates.
   *   <li>Ensures the teardown job is initialized (re-creating it from the original request if
   *       resumed).
   *   <li>Relays phase-skippable lifecycle decorator states from the setup job to the teardown job.
   * </ul>
   */
  @GuardedBy("sessionLock")
  private void addTeardownJobIfAny(RequestDetail.Builder requestDetail) {
    // Early return if a teardown job has already been added to the session. This prevents duplicate
    // teardown jobs when multiple main jobs complete around the same time or when
    // addTeardownJobIfAny is called from both addMainJobs and handleXtsJobEnd.
    boolean sessionHasTeardownJob = sessionInfo.getAllJobs().stream().anyMatch(this::isTeardownJob);
    if (sessionHasTeardownJob) {
      return;
    }
    ensureTeardownJobInitialized(requestDetail);
    if (teardownJob != null) {
      relayPhaseSkippableDecoratorStates(teardownJob);
      logger.atInfo().log("Adding teardown job [%s].", teardownJob.locator().getId());
      runningTeardownJobId = teardownJob.locator().getId();
      sessionInfo.addJob(teardownJob);
      teardownJob = null;
    }
  }

  /**
   * Re-initializes the teardown job if it is uninitialized (e.g., when resuming a session).
   *
   * <p>Teardown jobs might be lost in resumed sessions. This method re-creates the teardown job
   * from the original request if it was not already created.
   */
  @GuardedBy("sessionLock")
  private void ensureTeardownJobInitialized(RequestDetail.Builder requestDetail) {
    if (!isTeardownJobInitialized) {
      isTeardownJobInitialized = true;
      try {
        CreateJobsResult createTeardownJobsResult =
            newMultiCommandRequestHandler.createTeardownJobs(
                requestDetail.getOriginalRequest(), sessionInfo);
        teardownJob = createTeardownJobsResult.jobInfos().stream().findFirst().orElse(null);
      } catch (InterruptedException e) {
        logger.atWarning().withCause(e).log(
            "Interrupted when creating teardown job for session [%s]", sessionInfo.getSessionId());
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Relays phase-skippable lifecycle decorator properties from the setup job to the teardown job.
   *
   * <p>Finds the setup job from memory or from session jobs, and relays properties between their
   * root tests using {@link PhaseSkippableDecoratorUtil#relayStates}.
   */
  @GuardedBy("sessionLock")
  private void relayPhaseSkippableDecoratorStates(JobInfo teardownJobInfo) {
    JobInfo setupJobToRelay = setupJob;
    if (setupJobToRelay == null) {
      setupJobToRelay =
          sessionInfo.getAllJobs().stream().filter(this::isSetupJob).findFirst().orElse(null);
    }
    if (setupJobToRelay != null
        && setupJobToRelay.tests() != null
        && teardownJobInfo.tests() != null) {
      setupJobToRelay.tests().getAll().values().stream()
          .findFirst()
          .ifPresent(
              setupTest ->
                  teardownJobInfo
                      .tests()
                      .getAll()
                      .values()
                      .forEach(
                          teardownTest ->
                              PhaseSkippableDecoratorUtil.relayStates(setupTest, teardownTest)));
    }
  }

  /** Checks whether the given job is an ATS setup job based on its xTS job name property. */
  private boolean isSetupJob(JobInfo jobInfo) {
    return jobInfo
        .properties()
        .getOptional(XtsConstants.XTS_JOB_NAME)
        .map(name -> Ascii.equalsIgnoreCase(name, XtsConstants.SETUP_JOB_NAME))
        .orElse(false);
  }

  /** Checks whether the given job is an ATS teardown job based on its xTS job name property. */
  private boolean isTeardownJob(JobInfo jobInfo) {
    return jobInfo
        .properties()
        .getOptional(XtsConstants.XTS_JOB_NAME)
        .map(name -> Ascii.equalsIgnoreCase(name, XtsConstants.TEARDOWN_JOB_NAME))
        .orElse(false);
  }

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
   * Handles job completion in the session, orchestrating sequential scheduling of Tradefed,
   * non-Tradefed, and teardown jobs.
   *
   * <p>This method performs the following workflow:
   *
   * <ul>
   *   <li><b>Setup job end:</b> Starts main jobs via {@link #addMainJobs}.
   *   <li><b>Teardown job end:</b> Returns early as the session lifecycle is complete.
   *   <li><b>Main job end:</b> Handles post-processing for non-Tradefed jobs, schedules the next
   *       Tradefed job (if any), and schedules non-Tradefed jobs once all Tradefed jobs finish.
   *   <li><b>All main jobs finished:</b> Triggers {@link #addTeardownJobIfAny} once no unfinished
   *       Tradefed or non-Tradefed main jobs remain.
   * </ul>
   */
  @GuardedBy("sessionLock")
  void handleXtsJobEnd(JobInfo jobInfo, RequestDetail.Builder requestDetail)
      throws MobileHarnessException, InterruptedException {
    if (requestDetail.getState().equals(RequestState.CANCELED) || isAborted()) {
      return;
    }
    String jobId = jobInfo.locator().getId();
    if ((runningSetupJobId != null && runningSetupJobId.equals(jobId)) || isSetupJob(jobInfo)) {
      runningSetupJobId = null;
      logger.atInfo().log("Setup job [%s] ended, starting main jobs.", jobId);
      addMainJobs(requestDetail);
      return;
    }

    if ((runningTeardownJobId != null && runningTeardownJobId.equals(jobId))
        || isTeardownJob(jobInfo)) {
      runningTeardownJobId = null;
      logger.atInfo().log("Teardown job [%s] ended.", jobId);
      return;
    }

    handleNonTradefedJobEnd(jobInfo, requestDetail);
    ensureTradefedJobsInitialized(requestDetail);
    scheduleNextTradefedJob(jobInfo);
    scheduleNonTradefedJobsIfNeeded(jobInfo, requestDetail);
  }

  /**
   * Handles post-processing when a non-Tradefed xTS job completes, such as preparing Mobly log
   * directory names and writing module run result files.
   */
  @GuardedBy("sessionLock")
  private void handleNonTradefedJobEnd(JobInfo jobInfo, RequestDetail.Builder requestDetail)
      throws MobileHarnessException {
    if (!jobInfo.properties().getBoolean(Job.IS_XTS_NON_TF_JOB).orElse(false)) {
      return;
    }
    newMultiCommandRequestHandler.prepareMoblyJobLogDirName(jobInfo, requestDetail);
    for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
      ResultTypeWithCause resultWithCause = testInfo.resultWithCause().get();
      ModuleRunResult.Builder resultBuilder =
          ModuleRunResult.newBuilder().setResult(resultWithCause.type());
      if (resultWithCause.causeProto().isPresent()) {
        resultBuilder.setCause(resultWithCause.toStringWithDetail());
      }
      localFileUtil.writeToFile(
          Path.of(testInfo.getGenFileDir())
              .resolve("ats_module_run_result.textproto")
              .toAbsolutePath()
              .toString(),
          TextFormat.printer().printToString(resultBuilder.build()));
    }
  }

  /**
   * Re-initializes Tradefed jobs if they are uninitialized (e.g., when resuming a session).
   *
   * <p>Tradefed jobs might be lost in resumed sessions. This method re-creates them to ensure they
   * are executed, while removing jobs that have already been triggered in the session.
   */
  @GuardedBy("sessionLock")
  private void ensureTradefedJobsInitialized(RequestDetail.Builder requestDetail)
      throws InterruptedException {
    if (tradefedJobs == null) {
      CreateJobsResult createTradefedJobsResult =
          newMultiCommandRequestHandler.createTradefedJobs(
              requestDetail.getOriginalRequest(), sessionInfo);
      tradefedJobs = new ArrayList<>(createTradefedJobsResult.jobInfos());
      ImmutableSet<String> triggeredJobNames =
          sessionInfo.getAllJobs().stream()
              .map(job -> job.locator().getName())
              .collect(toImmutableSet());
      tradefedJobs.removeIf(job -> triggeredJobNames.contains(job.locator().getName()));
    }
  }

  /** Re-initializes non-Tradefed jobs if they are uninitialized (e.g., when resuming a session). */
  @GuardedBy("sessionLock")
  private void ensureNonTradefedJobsInitialized(RequestDetail.Builder requestDetail)
      throws InterruptedException {
    if (nonTradefedJobs == null) {
      CreateJobsResult createNonTradefedJobsResult =
          newMultiCommandRequestHandler.createNonTradefedJobs(
              requestDetail.getOriginalRequest(), sessionInfo);
      nonTradefedJobs = createNonTradefedJobsResult.jobInfos();
    }
  }

  /**
   * Schedules the next Tradefed job when executing multiple Tradefed jobs serially.
   *
   * <p>Each subsequent Tradefed job is added to the session only after the previous job has
   * finished.
   */
  @GuardedBy("sessionLock")
  private void scheduleNextTradefedJob(JobInfo completedJob) {
    if (tradefedJobs.isEmpty()) {
      return;
    }
    // The static xts job is the first job in the list, if the test result is not complete, we
    // don't execute any MCTS jobs.
    if (isStaticXtsJobAndFailed(completedJob)) {
      logger.atInfo().log(
          "Session [%s]: Static XTS job [%s] ended but result is not complete, clearing"
              + " remaining tradefed jobs.",
          sessionInfo.getSessionId(), completedJob.locator().getId());
      tradefedJobs.clear();
      return;
    }
    JobInfo nextJob = tradefedJobs.remove(0);
    logger.atInfo().log(
        "Session [%s]: Adding next tradefed job [%s] to session.",
        sessionInfo.getSessionId(), nextJob.locator().getId());
    sessionInfo.addJob(nextJob);
  }

  /**
   * Checks whether the given job is a static xTS dynamic-download job that ended without complete
   * passing results.
   */
  private static boolean isStaticXtsJobAndFailed(JobInfo jobInfo) {
    return jobInfo
            .properties()
            .getBoolean(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED)
            .orElse(false)
        && jobInfo
            .properties()
            .getOptional(XtsConstants.XTS_JOB_NAME)
            .orElse("")
            .contains(XtsConstants.STATIC_XTS_JOB_NAME)
        && jobInfo.tests().getAll().values().stream()
            .noneMatch(
                testInfo ->
                    testInfo.resultWithCause().get().type() == TestResult.PASS
                        && testInfo
                            .properties()
                            .getBoolean(XtsConstants.TRADEFED_JOBS_HAS_RESULT_FILE)
                            .orElse(false));
  }

  /**
   * Schedules non-Tradefed jobs or triggers the teardown job as main jobs complete.
   *
   * <p>This method handles job transitions after any Tradefed or non-Tradefed main job ends:
   *
   * <ul>
   *   <li><b>Canceled session:</b> No action is taken if the request state is {@link
   *       RequestState#CANCELED}.
   *   <li><b>Non-Tradefed job completed:</b> If all non-Tradefed main jobs have finished, triggers
   *       {@link #addTeardownJobIfAny}.
   *   <li><b>Tradefed job completed:</b> Once all Tradefed main jobs finish, checks {@link
   *       #hasAnyNonTradefedJobs} to avoid duplicate scheduling on concurrent completions. If no
   *       non-TF jobs were added yet, schedules them (re-initializing if needed) or triggers {@link
   *       #addTeardownJobIfAny} if none exist.
   * </ul>
   */
  @GuardedBy("sessionLock")
  private void scheduleNonTradefedJobsIfNeeded(
      JobInfo completedJob, RequestDetail.Builder requestDetail) throws InterruptedException {
    if (requestDetail.getState() == RequestState.CANCELED) {
      return;
    }
    if (!completedJob.type().getDriver().equals(TRADEFED_DRIVER_NAME)) {
      if (!hasUnfinishedNonTradefedJobs()) {
        logger.atInfo().log("All non-tradefed main jobs have completed.");
        addTeardownJobIfAny(requestDetail);
      }
      return;
    }
    // Skip if TF jobs are still running or non-TF jobs were already added (avoids duplicate
    // scheduling when TF jobs finish concurrently).
    if (hasUnfinishedTradefedJobs() || hasAnyNonTradefedJobs()) {
      return;
    }

    ensureNonTradefedJobsInitialized(requestDetail);
    if (!nonTradefedJobs.isEmpty()) {
      nonTradefedJobs.forEach(sessionInfo::addJob);
    } else {
      addTeardownJobIfAny(requestDetail);
    }
  }

  /**
   * Checks whether the session has any unfinished Tradefed jobs.
   *
   * <p>If there are still running tradefed jobs, wait and not add non-tradefed jobs.
   */
  private boolean hasUnfinishedTradefedJobs() {
    return sessionInfo.getAllJobs().stream()
        .anyMatch(
            job ->
                job.type().getDriver().equals(TRADEFED_DRIVER_NAME)
                    && !job.status().get().equals(TestStatus.DONE));
  }

  /**
   * Checks whether the session already has non-Tradefed jobs added.
   *
   * <p>If there are non-tradefed jobs, that means all non-tradefed tests had already been added. So
   * no need to add again. This can happen when two jobs end at the same time.
   */
  private boolean hasAnyNonTradefedJobs() {
    return sessionInfo.getAllJobs().stream().anyMatch(this::isMainNonTradefedJob);
  }

  private boolean hasUnfinishedNonTradefedJobs() {
    return sessionInfo.getAllJobs().stream()
        .anyMatch(job -> isMainNonTradefedJob(job) && !job.status().get().equals(TestStatus.DONE));
  }

  private boolean isMainNonTradefedJob(JobInfo job) {
    return !job.type().getDriver().equals(TRADEFED_DRIVER_NAME)
        && !isSetupJob(job)
        && !isTeardownJob(job);
  }

  @GuardedBy("sessionLock")
  private boolean isAborted() {
    return sessionInfo
        .getSessionProperty(SessionProperties.PROPERTY_KEY_SESSION_ABORTED_WHEN_RUNNING)
        .map(Boolean::parseBoolean)
        .orElse(false);
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
