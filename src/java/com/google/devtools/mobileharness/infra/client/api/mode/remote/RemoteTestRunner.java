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

package com.google.devtools.mobileharness.infra.client.api.mode.remote;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.util.ErrorIdComparator;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.util.LabRpcProtoConverter;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ResourceFederation;
import com.google.devtools.mobileharness.infra.client.api.util.longevity.LongevityTestHelper;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.ContainerModePreference;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.SandboxModePreference;
import com.google.devtools.mobileharness.infra.container.proto.SandboxSettingProto.SandboxSetting;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.infra.controller.test.BaseTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.launcher.ThreadPoolTestRunnerLauncher;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest.ContainerSetting;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest.ResolveFileItem;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.TestRunnerTiming;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.ExecTestStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.PrepareTestStub;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.trace.proto.SpanProto.ParentSpan;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.sharedpool.SharedPoolJobUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.util.time.TimeoutUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto.VersionCheckRequest;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionRequest;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.shared.version.rpc.stub.VersionStub;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.client.api.util.stub.StubManager;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.ForwardTestMessageRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.SubTestGenDataResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.TestMessage;
import com.google.wireless.qa.mobileharness.shared.comm.message.CacheableTestMessageHandler;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** For executing a single test allocated by calling remote lab. */
public class RemoteTestRunner extends BaseTestRunner<RemoteTestRunner> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** File that starts with this tag won't be sent to lab server side. */
  private static final String TAG_CLIENT_FILE_PREFIX = "client_side:";

  private static final int USE_GET_TEST_ENGINE_STATUS_SHORT_INTERVAL_MAX_COUNT = 15;
  private static final Duration GET_TEST_ENGINE_STATUS_SHORT_INTERVAL = Duration.ofSeconds(2L);
  private static final Duration GET_TEST_ENGINE_STATUS_LONG_INTERVAL = Duration.ofSeconds(10L);

  /** Real-time rpc call interval. */
  private static final Duration REAL_TIME_RPC_CALL_INTERVAL = Duration.ofSeconds(1L);

  /** Numbers to use real-time rpc call interval in real-time tests. */
  private static final int NUM_USE_REAL_TIME_RPC_CALL_INTERVAL = 20;

  /** Time range limits for getting test status. */
  private static final Duration MIN_GET_TEST_STATUS_RPC_CALL_INTERVAL = Duration.ofMillis(200L);

  private static final Duration MAX_GET_TEST_STATUS_RPC_CALL_INTERVAL = Duration.ofSeconds(10L);
  private static final Duration MIN_CONSECUTIVE_GET_TEST_STATUS_ERROR_DURATION =
      Duration.ofSeconds(10L);
  private static final Duration MAX_CONSECUTIVE_GET_TEST_STATUS_ERROR_DURATION =
      Duration.ofMinutes(10L);

  /** See ContainerSetting.getSyncStartingTimeoutMs(). */
  private static final Duration LAB_SERVER_TEST_ENGINE_SYNC_STARTING_TIMEOUT = Duration.ZERO;

  private static final int KICK_OFF_TEST_MAX_TRY_COUNT = 15;
  private static final Duration KICK_OFF_TEST_RETRY_INTERVAL = Duration.ofSeconds(2L);

  private final Sleeper sleeper;
  private final Clock clock;
  private final ListeningExecutorService threadPool;
  private final CachedTestMessageForwarder testMessageForwarder;
  private final TestMessageManager testMessageManager;
  @Nullable private final String impersonationUser;
  private final LabServerLocator labServerLocator;
  private final ResourceFederation resourceFederation;
  private final List<String> downloadedGenDirs = new ArrayList<>();
  private final LocalFileUtil fileUtil;
  private final JobSpecHelper jobSpecHelper;
  private final StubManager stubManager;
  private final LongevityTestHelper longevityTestHelper = new LongevityTestHelper();

  private final FileResolver fileResolver;

  /** Whether the test is actually started in the remote lab. */
  private volatile boolean testKickedOff = false;

  /** Set in {@link #initialize} method. */
  private volatile Version labVersion;

  /**
   * Set in {@link #checkDevice} if it returns normally.
   *
   * <p>The task that waits until the test engine becomes ready.
   */
  @VisibleForTesting volatile ListenableFuture<TestEngineLocator> waitUntilTestEngineReadyTask;

  /** Set in {@link #runTest}. It is non-null when {@link #testKickedOff} is true. */
  @Nullable private volatile TestEngineLocator testEngineLocator;

  public RemoteTestRunner(
      DirectTestRunnerSetting setting,
      ListeningExecutorService threadPool,
      ResourceFederation resourceFederation,
      boolean supportImpersonation,
      FileResolver fileResolver)
      throws MobileHarnessException {
    this(
        new ThreadPoolTestRunnerLauncher<>(threadPool, setting.globalInternalBus().orElse(null)),
        setting,
        threadPool,
        Sleeper.defaultSleeper(),
        Clock.systemUTC(),
        new LocalFileUtil(),
        JobSpecHelper.getDefaultHelper(),
        StubManager.getInstance(),
        TestMessageManager.getInstance(),
        resourceFederation,
        fileResolver,
        supportImpersonation);
  }

  @VisibleForTesting
  RemoteTestRunner(
      TestRunnerLauncher<? super RemoteTestRunner> launcher,
      DirectTestRunnerSetting setting,
      ListeningExecutorService threadPool,
      Sleeper sleeper,
      Clock clock,
      LocalFileUtil fileUtil,
      JobSpecHelper jobSpecHelper,
      StubManager stubManager,
      TestMessageManager testMessageManager,
      ResourceFederation resourceFederation,
      FileResolver fileResolver,
      boolean supportImpersonation)
      throws MobileHarnessException {
    super(launcher, setting, threadPool);
    this.threadPool = threadPool;
    this.sleeper = sleeper;
    this.clock = clock;
    this.fileUtil = fileUtil;
    this.jobSpecHelper = jobSpecHelper;
    this.stubManager = stubManager;
    this.testMessageManager = testMessageManager;
    this.resourceFederation = resourceFederation;
    this.fileResolver = fileResolver;

    this.impersonationUser =
        supportImpersonation ? setting.testInfo().jobInfo().getImpersonatee() : null;
    this.testMessageForwarder = new CachedTestMessageForwarder(threadPool);
    this.labServerLocator =
        LabServerLocator.longRunningLabServer(getAllocation().getDevice().labLocator());

    // Subscribes messages for forwarding to the lab side.
    registerTestEventSubscriber(testMessageForwarder, DirectTestRunner.EventScope.TEST_MESSAGE);
  }

  @Override
  protected final RemoteTestRunner self() {
    return this;
  }

  @Override
  protected String getComponentName() {
    return "client";
  }

  @Override
  protected void initialize(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("========= Client: InitializeTest (%s) =========", testInfo.locator().getId());
    labVersion = getLabVersion();
  }

  @CanIgnoreReturnValue
  @Override
  protected List<DeviceFeature> checkDevice(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("========= Client: CheckDevice (%s) =========", testInfo.locator().getId());

    // Gets container/sandbox mode preferences.
    boolean defaultSandboxPreference = getDefaultSandboxPreference(testInfo);
    ContainerModePreference containerModePreference =
        getContainerModePreference(defaultSandboxPreference);
    SandboxModePreference sandboxModePreference =
        getSandboxModePreference(defaultSandboxPreference);

    if (SharedPoolJobUtil.isUsingSharedPool(testInfo.jobInfo())) {
      containerModePreference = ContainerModePreference.MANDATORY_NON_CONTAINER;
      sandboxModePreference = SandboxModePreference.MANDATORY_NON_SANDBOX;
    }

    logger.atInfo().log(
        "container_mode_preference=%s, sandbox_mode_preference=%s",
        containerModePreference, sandboxModePreference);

    // Sends the test metadata to lab server.
    CreateTestResponse createTestResponse =
        prepareTest(
            testInfo,
            allocation.getAllDeviceLocators(),
            containerModePreference,
            sandboxModePreference);

    // Validates container/sandbox modes.
    boolean isContainerMode = createTestResponse.getContainerInfo().getIsContainerMode();
    boolean isSandboxMode = createTestResponse.getContainerInfo().getIsSandboxMode();
    testInfo.properties().add(PropertyName.Test.CONTAINER_MODE, Boolean.toString(isContainerMode));
    testInfo.properties().add(PropertyName.Test.SANDBOX_MODE, Boolean.toString(isSandboxMode));
    logger.atInfo().log(
        "Is container mode: %s, is sandbox mode: %s", isContainerMode, isSandboxMode);
    if ((containerModePreference == ContainerModePreference.MANDATORY_CONTAINER && !isContainerMode)
        || (containerModePreference == ContainerModePreference.MANDATORY_NON_CONTAINER
            && isContainerMode)) {
      throw new MobileHarnessException(
          InfraErrorId.TE_DENIED_MANDATORY_CONTAINER_PREFERENCE,
          String.format(
              "Client container mode preference is [%s] but lab final container mode is [%s]",
              containerModePreference, isContainerMode ? "container" : "non-container"));
    }
    if ((sandboxModePreference == SandboxModePreference.MANDATORY_SANDBOX && !isSandboxMode)
        || (sandboxModePreference == SandboxModePreference.MANDATORY_NON_SANDBOX
            && isSandboxMode)) {
      throw new MobileHarnessException(
          InfraErrorId.TE_DENIED_MANDATORY_SANDBOX_PREFERENCE,
          String.format(
              "Client sandbox mode preference is [%s] but lab final sandbox mode is [%s]",
              sandboxModePreference, isSandboxMode ? "sandbox" : "non-sandbox"));
    }

    waitUntilTestEngineReadyTask =
        threadPool.submit(
            () ->
                waitUntilTestEngineReady(
                    createTestResponse.getGetTestEngineStatusResponse(), testInfo));

    return createTestResponse.getDeviceFeatureList();
  }

  @Override
  protected void preRunTest(
      boolean isTestSkipped,
      TestInfo testInfo,
      Allocation allocation,
      ImmutableList<LabQueryProto.DeviceInfo> newDeviceInfos,
      List<DeviceFeature> deviceFeatures)
      throws MobileHarnessException, InterruptedException {
    String testId = testInfo.locator().getId();
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("========= Client: PreRunTest (%s) =========", testId);

    if (isTestSkipped) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Skip initializing file transfer client and sending files "
                  + "because the test has been skipped.");
    } else {
      if (Flags.instance().enableClientFileTransfer.getNonNull()) {
        sendJobFiles(testInfo);
      }
    }
  }

  /** Should run the preRunTest if the test is NOT a resumed test. */
  @Override
  protected boolean shouldRunDoPreRunTest(TestInfo testInfo) {
    return !isResumedTest(testInfo);
  }

  @Override
  protected void runTest(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("========= Client: RunTest (%s) =========", testInfo.locator().getId());

    ImmutableList<DeviceLocator> deviceLocators = allocation.getAllDeviceLocators();
    if (isResumedTest(testInfo)) {
      testEngineLocator = LongevityTestHelper.resumeTestEngineLocator(testInfo).orElse(null);
      testInfo.log().atInfo().alsoTo(logger).log("Skip kickOffTest because it is a resumed job");
    } else {
      if (Flags.instance().enableClientFileTransfer.getNonNull()) {
        sendTestFiles(testInfo);
      }

      logger.atInfo().log("Waiting until test engine becomes ready...");
      try {
        testEngineLocator = waitUntilTestEngineReadyTask.get();
        if (testEngineLocator != null) {
          testInfo
              .properties()
              .add(
                  PropertyName.Test._TEST_ENGINE_LOCATOR,
                  requireNonNull(testEngineLocator).toString());
        }
      } catch (ExecutionException e) {
        throw new MobileHarnessException(
            InfraErrorId.CLIENT_REMOTE_MODE_TEST_ENGINE_NOT_READY, "Test engine is not ready", e);
      }
      logger.atInfo().log("Test engine is ready");

      kickOffTest(testInfo, deviceLocators);

      longevityTestHelper.persistentJobInfoIfNeeded(testInfo.jobInfo());
    }

    testKickedOff = true;
    // Forwards the cached messages only after the test has been kicked off successfully.
    // Forwards the messages asynchronously because waitTestResult() needs to start immediately.
    testMessageForwarder.asyncDisableAndHandleCache();
    waitTestResult(testInfo, deviceLocators);
  }

  @SuppressWarnings("Interruption")
  @Override
  protected PostTestDeviceOp postRunTest(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("========= Client: PostRunTest (%s) =========", testInfo.locator().getId());

    try {
      if (testKickedOff) {
        if (Flags.instance().enableClientFileTransfer.getNonNull()) {
          updateTestEngineFileTransferClient(testInfo);
        }
        getTestGenData(testInfo);
      }
      if (Flags.instance().enableClientFileTransfer.getNonNull()) {
        setFileTransferProperties(testInfo);
      }

      testMessageForwarder.close();

      if (waitUntilTestEngineReadyTask != null && !waitUntilTestEngineReadyTask.isDone()) {
        waitUntilTestEngineReadyTask.cancel(/* mayInterruptIfRunning= */ true);
      }

      Instant executeInstant = getTestRunnerExecuteInstant().orElseThrow(AssertionError::new);
      testInfo
          .properties()
          .add(
              PropertyName.Test.REMOTE_EXECUTION_TIME_MS,
              Long.toString(Duration.between(executeInstant, clock.instant()).toMillis()));
    } finally {
      // Notify lab server that client side postRunTest().Exceptions should be tolerated since the
      // test has been marked DONE already.
      PrepareTestServiceProto.CloseTestRequest req =
          PrepareTestServiceProto.CloseTestRequest.newBuilder()
              .setJobId(testInfo.jobInfo().locator().getId())
              .setTestId(testInfo.locator().getId())
              .build();
      PrepareTestStub prepareTestStub = getPrepareTestStub();
      try {
        longevityTestHelper.persistentJobInfoIfNeeded(testInfo.jobInfo());

        PrepareTestServiceProto.CloseTestResponse response =
            prepareTestStub.closeTest(req, impersonationUser);
        TestRunnerTiming testTiming = response.getTestTiming();
        if (testTiming.hasStartTimestamp() && testTiming.hasExecuteTimestamp()) {
          testInfo
              .properties()
              .add(
                  PropertyName.Test.REMOTE_START_DELAY_MS,
                  Long.toString(
                      Timestamps.toMillis(testTiming.getExecuteTimestamp())
                          - Timestamps.toMillis(testTiming.getStartTimestamp())));
        }
        if (testTiming.hasTestEngineSetupTime()) {
          testInfo
              .properties()
              .add(
                  PropertyName.Test.REMOTE_TEST_ENGINE_SETUP_TIME_MS,
                  Long.toString(Durations.toMillis(testTiming.getTestEngineSetupTime())));
        }
      } catch (RpcExceptionWithErrorId e) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to close test but ignore it since the test has been done");
      }
    }

    // Returns REBOOT to let DeviceAllocator always treat the device as dirty at client side.
    return PostTestDeviceOp.REBOOT;
  }

  private static boolean isResumedTest(TestInfo testInfo) {
    return testInfo
            .jobInfo()
            .properties()
            .getBoolean(PropertyName.Job._IS_RESUMED_JOB)
            .orElse(false)
        && testInfo.properties().has(PropertyName.Test._TEST_ENGINE_LOCATOR);
  }

  /** Gets the {@link PrepareTestStub} to talk to lab PrepareTestService. */
  private PrepareTestStub getPrepareTestStub() {
    return stubManager.getPrepareTestStub(labServerLocator, resourceFederation);
  }

  /** Gets the {@link VersionStub} to talk to lab VersionService. */
  private VersionStub getLabVersionStub() {
    return stubManager.getLabVersionStub(labServerLocator, resourceFederation);
  }

  /** Gets the {@link ExecTestStub} to talk to lab ExecTestService. */
  private ExecTestStub getTestEngineExecTestStub() {
    return stubManager.getTestEngineExecTestStub(
        labServerLocator, testEngineLocator, resourceFederation);
  }

  /**
   * Gets the default sandbox preference to help determine the container mode preference and sandbox
   * mode preference.
   */
  private boolean getDefaultSandboxPreference(TestInfo testInfo) {
    boolean defaultSandboxPreference = false;
    logger.atInfo().log("Default sandbox preference: %s", defaultSandboxPreference);
    return defaultSandboxPreference;
  }

  /** Gets the {@link ParentSpan} of the current instance, empty by default. */
  private ParentSpan getParentSpan() {
    return ParentSpan.getDefaultInstance();
  }

  /** Sends job level running files of the test to lab, does nothing by default. */
  private void sendJobFiles(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {}

  /** Sends test level running files to the lab, does nothing by default. */
  private void sendTestFiles(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {}

  /**
   * Updates the test engine file transfer client to download files from lab to client, does nothing
   * by default.
   */
  private void updateTestEngineFileTransferClient(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {}

  /** Sets file transfer related properties to the test, does nothing by default. */
  private void setFileTransferProperties(TestInfo testInfo) {}

  /**
   * Downloads all test generated files from lab side, does nothing if file transfer isn't
   * supported.
   */
  private void downloadTestGeneratedFiles(
      GetTestGenDataResponse resp, TestInfo testInfo, String subTestLogPostfix)
      throws InterruptedException {}

  /** Sends all run files of the test from client to lab. */
  private void sendJobFilesCore(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {}

  /** Downloads the whole gen file directory from lab server. */
  @VisibleForTesting
  void downloadGenDir(TestInfo testInfo, final String remoteGenFileDir)
      throws InterruptedException {}

  /**
   * Gets the lab version.
   *
   * @apiNote this method generates the first RPC between client and lab server
   * @return the lab version or 0.0.0 if the lab does not have VersionService (meaning an old lab)
   * @throws MobileHarnessException if fails to connect to the lab or the result is illegal
   */
  private Version getLabVersion() throws MobileHarnessException {
    logger.atInfo().log("Getting lab version of [%s]", labServerLocator);
    GetVersionRequest request = GetVersionRequest.getDefaultInstance();
    GetVersionResponse response;
    try {
      response = getLabVersionStub().getVersion(request, impersonationUser);
    } catch (RpcExceptionWithErrorId e) {
      if (e.getRpcCanonicalCode() == 12) {
        // If the lab does not have VersionService, treat it as a low-version lab server.
        logger.atInfo().log(
            "Lab [%s] does not have VersionService, treat it as version 0.0.0. Detail: %s",
            labServerLocator, e.getMessage());
        return new Version(0, 0, 0);
      } else {
        throw new MobileHarnessException(
            InfraErrorId.CLIENT_REMOTE_MODE_SEND_RPC_TO_LAB_SERVER_ERROR,
            String.format("Failed to send RPC to lab server [%s]", labServerLocator),
            e);
      }
    }
    logger.atInfo().log(
        "GetVersionResponse of [%s]: %s", labServerLocator, shortDebugString(response));
    Version version = new Version(response.getVersion());
    logger.atInfo().log("Lab version of [%s] is %s", labServerLocator, version);
    return version;
  }

  /** Sends all the test information except the file info to the lab. */
  private CreateTestResponse prepareTest(
      TestInfo testInfo,
      List<DeviceLocator> deviceLocators,
      ContainerModePreference containerModePreference,
      SandboxModePreference sandboxModePreference)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<String> hostNames =
        deviceLocators.stream()
            .map(deviceLocator -> deviceLocator.getLabLocator().getHostName())
            .distinct()
            .collect(toImmutableList());
    MobileHarnessExceptions.check(
        hostNames.size() == 1,
        InfraErrorId.TR_MULTIPLE_DEVICES_IN_DIFFERENT_LABS,
        () -> String.format("Devices are in different labs: %s", deviceLocators));

    JobInfo jobInfo = testInfo.jobInfo();
    Timeout timeout = getTestTimeout(jobInfo.setting().getNewTimeout());
    ImmutableList<ResolveFileItem> labResolveFiles = getLabResolveFiles(jobInfo);

    logger.atInfo().log("Prepare test %s with devices %s", testInfo.locator(), deviceLocators);
    CreateTestRequest createTestRequest =
        CreateTestRequest.newBuilder()
            .setVersionCheckRequest(
                VersionCheckRequest.newBuilder()
                    .setStubVersion(Version.CLIENT_VERSION.toString())
                    .setMinServiceVersion(Version.MIN_LAB_VERSION.toString()))
            .addAllDeviceId(
                deviceLocators.stream().map(DeviceLocator::getSerial).collect(toImmutableList()))
            .setJob(
                CreateTestRequest.Job.newBuilder()
                    .setJobId(jobInfo.locator().getId())
                    .setJobName(jobInfo.locator().getName())
                    .setJobCreateTimeMs(jobInfo.timing().getCreateTime().toEpochMilli())
                    .setJobStartTimeMs(jobInfo.timing().getStartTimeNonNull().toEpochMilli())
                    .setTimeout(
                        com.google.devtools.mobileharness.api.model.proto.Job.Timeout.newBuilder()
                            .setJobTimeoutMs(timeout.getJobTimeoutMs())
                            .setTestTimeoutMs(timeout.getTestTimeoutMs())
                            .setStartTimeoutMs(timeout.getStartTimeoutMs()))
                    .setJobFeature(jobInfo.toFeature())
                    .addAllLabResolveFile(labResolveFiles))
            .setTest(
                CreateTestRequest.Test.newBuilder()
                    .setTestId(testInfo.locator().getId())
                    .setTestName(testInfo.locator().getName())
                    .setTestCreateTimeMs(testInfo.timing().getCreateTime().toEpochMilli())
                    .setTestStartTimeMs(testInfo.timing().getStartTimeNonNull().toEpochMilli()))
            .setContainerSetting(
                ContainerSetting.newBuilder()
                    .setContainerModePreference(containerModePreference)
                    .setSandboxModePreference(sandboxModePreference)
                    .setNeedStartingLicense(false)
                    .setSyncStartingTimeoutMs(
                        LAB_SERVER_TEST_ENGINE_SYNC_STARTING_TIMEOUT.toMillis())
                    .setSandboxSetting(getSandboxSetting()))
            .setParentSpan(getParentSpan())
            .build();
    PrepareTestStub prepareTestStub = getPrepareTestStub();
    CreateTestResponse response;
    try {
      response = prepareTestStub.createTest(createTestRequest, impersonationUser);
    } catch (RpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.CLIENT_REMOTE_MODE_TEST_CREATE_ERROR,
          "Failed to create test in Lab Server",
          e);
    }

    return response;
  }

  private ImmutableList<ResolveFileItem> getLabResolveFiles(JobInfo jobInfo)
      throws MobileHarnessException, InterruptedException {
    return Streams.concat(
            jobInfo.files().getAll().entries().stream(),
            JobSpecHelper.getFiles(jobInfo.protoSpec().getProto()).entrySet().stream(),
            jobInfo.scopedSpecs().getFiles(jobSpecHelper).entrySet().stream())
        .filter(entry -> !resolveInClient(entry.getValue()))
        .map(entry -> createResolveFileItem(entry.getKey(), entry.getValue(), jobInfo))
        .collect(toImmutableList());
  }

  /** Returns whether the file should be resolved in the client. */
  private static boolean resolveInClient(String filePath) {
    if (Flags.instance().enableClientFileTransfer.getNonNull()) {
      return Stream.of(RemoteFileType.ATS_FILE_SERVER.prefix()).noneMatch(filePath::startsWith)
          || filePath.equals(SessionRequestHandlerUtil.PARAM_ANDROID_XTS_ZIP_FILE_PATH);
    } else {
      return false;
    }
  }

  private static ResolveFileItem createResolveFileItem(
      String tag, String filePath, JobInfo jobInfo) {
    ResolveFileItem.Builder builder = ResolveFileItem.newBuilder().setTag(tag).setFile(filePath);
    return builder.build();
  }

  private TestEngineLocator waitUntilTestEngineReady(
      GetTestEngineStatusResponse response, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    int count = 0;
    while (true) {
      count++;
      switch (response.getTestEngineStatus()) {
        case READY:
          logger.atInfo().log(
              "Test engine becomes ready, locator=[%s]",
              shortDebugString(response.getTestEngineLocator()));
          return response.getTestEngineLocator();
        case FAILED:
          logger.atInfo().log("Test engine failed to start");
          throw new MobileHarnessException(
              InfraErrorId.TE_TEST_ENGINE_FAILURE_WHEN_CLIENT_WAITING_TEST_ENGINE_READY,
              "Test engine failed to start",
              ErrorModelConverter.toDeserializedException(response.getExceptionDetail()));
        case CLOSED:
          logger.atInfo().log("Test engine closed");
          throw new MobileHarnessException(
              InfraErrorId.TE_TEST_ENGINE_CLOSED_WHEN_CLIENT_WAITING_TEST_ENGINE_READY,
              "Test engine closed",
              /* cause= */ response.hasExceptionDetail()
                  ? ErrorModelConverter.toDeserializedException(response.getExceptionDetail())
                  : null);
        default:
          logger.atInfo().log("Test engine status: [%s]", response.getTestEngineStatus());
      }

      sleeper.sleep(getGetTestEngineStatusInterval(count));
      logger.atInfo().log("Getting test engine status...");

      TestLocator testLocator = testInfo.locator();
      try {
        response =
            getPrepareTestStub()
                .getTestEngineStatus(
                    GetTestEngineStatusRequest.newBuilder()
                        .setTestId(testLocator.getId())
                        .setJobId(testLocator.getJobLocator().getId())
                        .build(),
                    impersonationUser);
      } catch (RpcExceptionWithErrorId e) {
        logger.atWarning().withCause(e).log("Failed to get test engine status");
      }
    }
  }

  private static Duration getGetTestEngineStatusInterval(int count) {
    return count <= USE_GET_TEST_ENGINE_STATUS_SHORT_INTERVAL_MAX_COUNT
        ? GET_TEST_ENGINE_STATUS_SHORT_INTERVAL
        : GET_TEST_ENGINE_STATUS_LONG_INTERVAL;
  }

  /** Kicks off the test in lab after all job/test files are ready in the lab. */
  private void kickOffTest(TestInfo testInfo, List<DeviceLocator> deviceLocators)
      throws MobileHarnessException, InterruptedException {
    String testId = testInfo.locator().getId();
    logger.atInfo().log("Kick off test %s on device(s) %s", testId, deviceLocators);
    KickOffTestRequest req =
        new LabRpcProtoConverter()
            .generateKickOffTestRequestFrom(testInfo, deviceLocators, getParentSpan());
    ExecTestStub execTestStub = getTestEngineExecTestStub();
    int kickOffTryCount = 0;
    while (true) {
      try {
        kickOffTryCount++;
        execTestStub.kickOffTest(req, impersonationUser);
        testInfo.log().atInfo().alsoTo(logger).log("Test kicked off at lab side");
        break;
      } catch (RpcExceptionWithErrorId e) {
        MobileHarnessException newException =
            new MobileHarnessException(
                InfraErrorId.TR_FAILED_TO_KICK_OFF_REMOTE_TEST,
                String.format("Failed to kick off test %s on device %s", testId, deviceLocators),
                e.getApplicationError().isPresent() ? e.getApplicationError().get() : e);
        newException.setStackTrace(new StackTraceElement[] {});
        throw newException;
      }
    }
  }

  /** Waits until the test is finished in lab server. And retrieves the test result. */
  private void waitTestResult(TestInfo testInfo, List<DeviceLocator> deviceLocators)
      throws InterruptedException, MobileHarnessException {
    // Client side may also log to TestInfo. So we can NOT use the client side test log length as
    // the offset for lab server side log. Each sub-test should have a log offset counter.
    Map<String, Integer> remoteLogOffset = new HashMap<>();
    GetTestStatusResponse resp;
    @Nullable Instant consecutiveNonFatalRpcErrorStartingTime = null;
    ExecTestStub execTestStub = getTestEngineExecTestStub();
    Duration rpcCallInterval =
        getGetTestStatusRpcCallInterval(
            testInfo, Flags.instance().getTestStatusRpcCallInterval.getNonNull());

    Duration maxConsecutiveErrorDuration =
        Flags.instance().maxConsecutiveGetTestStatusErrorDuration.getNonNull();
    if (maxConsecutiveErrorDuration.compareTo(MIN_CONSECUTIVE_GET_TEST_STATUS_ERROR_DURATION) < 0) {
      maxConsecutiveErrorDuration = MIN_CONSECUTIVE_GET_TEST_STATUS_ERROR_DURATION;
    } else if (maxConsecutiveErrorDuration.compareTo(MAX_CONSECUTIVE_GET_TEST_STATUS_ERROR_DURATION)
        > 0) {
      maxConsecutiveErrorDuration = MAX_CONSECUTIVE_GET_TEST_STATUS_ERROR_DURATION;
    }

    int count = 0;
    while (!Thread.currentThread().isInterrupted()) {
      if (Flags.instance().realTimeTest.getNonNull().equals(Boolean.TRUE)
          && count < NUM_USE_REAL_TIME_RPC_CALL_INTERVAL) {
        sleeper.sleep(REAL_TIME_RPC_CALL_INTERVAL);
      } else {
        sleeper.sleep(rpcCallInterval);
      }
      count++;

      // Request root testInfo.
      GetTestStatusRequest.Builder builder = createGetTestStatusRequest(testInfo, remoteLogOffset);

      try {
        resp = execTestStub.getTestStatus(builder.build(), impersonationUser);
        consecutiveNonFatalRpcErrorStartingTime = null; // Resets it after a successful call.
      } catch (RpcExceptionWithErrorId e) {
        // While test running, the job of this test may be expired due to some reasons, such as
        // b/14494422. In this case, throws Exception and cancels the job. Otherwise, just alerts
        // Exception message.
        if (e.getApplicationError().isPresent()
            && ErrorIdComparator.equal(
                e.getApplicationError().get().getErrorId(), InfraErrorId.TM_TEST_NOT_FOUND)) {
          MobileHarnessException newException =
              new MobileHarnessException(
                  InfraErrorId.CLIENT_REMOTE_MODE_TEST_NOT_FOUND,
                  "Test not found in Lab Server",
                  e.getApplicationError().get());
          newException.setStackTrace(new StackTraceElement[] {});
          throw newException;
        } else {
          // A non-fatal RPC error occurs.
          String errMsg = "Failed to get test status on device(s) " + deviceLocators;
          Instant currentTime = clock.instant();

          if (consecutiveNonFatalRpcErrorStartingTime == null) {
            consecutiveNonFatalRpcErrorStartingTime = currentTime;
            // Only adds the warning to the test info once for consecutive non-fatal errors.
            testInfo
                .warnings()
                .addAndLog(
                    new MobileHarnessException(
                        InfraErrorId.CLIENT_REMOTE_MODE_TEST_GET_STATUS_ERROR,
                        errMsg,
                        e.getApplicationError().isPresent() ? e.getApplicationError().get() : e),
                    logger);
          } else if (Duration.between(consecutiveNonFatalRpcErrorStartingTime, currentTime)
                  .compareTo(maxConsecutiveErrorDuration)
              > 0) {
            MobileHarnessException newException =
                new MobileHarnessException(
                    InfraErrorId.CLIENT_REMOTE_MODE_TEST_CONSECUTIVE_GET_STATUS_ERROR,
                    errMsg,
                    e.getApplicationError().isPresent() ? e.getApplicationError().get() : e);
            newException.setStackTrace(new StackTraceElement[] {});
            throw newException;
          } else {
            logger.atWarning().log("%s", errMsg);
          }
        }
        continue;
      }

      // Update root testInfo.
      updateTestStatus(resp, testInfo, remoteLogOffset);

      if (resp.getTestStatus().equals(TestStatus.DONE)) {
        logger.atInfo().log(
            "Finished on device(s) %s with result %s!", deviceLocators, testInfo.resultWithCause());
        return;
      }
    }
    logger.atWarning().log("Timeout on device(s) %s!", deviceLocators);
  }

  /** Gets the test generated properties and downloads the generated files from lab server. */
  private void getTestGenData(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    // Request root testInfo.
    GetTestGenDataRequest.Builder builder = createGetTestGenDataRequest(testInfo);

    GetTestGenDataResponse resp;
    ExecTestStub execTestStub = getTestEngineExecTestStub();
    try {
      resp = execTestStub.getTestGenData(builder.build(), impersonationUser);
    } catch (RpcExceptionWithErrorId e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_REMOTE_MODE_TEST_GET_GEN_DATA_ERROR,
                  "Failed to get test generated data from " + testEngineLocator,
                  e),
              logger);
      return;
    }

    // Update root testInfo.
    updateTestGenData(resp, testInfo);
  }

  @VisibleForTesting
  Duration getGetTestStatusRpcCallInterval(TestInfo testInfo, Duration defaultInterval) {
    Duration rpcCallInterval =
        testInfo
            .jobInfo()
            .params()
            .getOptional(JobInfo.PARAM_GET_TEST_STATUS_RPC_CALL_INTERVAL_MS)
            .map(Long::valueOf)
            .map(Duration::ofMillis)
            .orElse(defaultInterval);
    if (rpcCallInterval.compareTo(MIN_GET_TEST_STATUS_RPC_CALL_INTERVAL) < 0) {
      rpcCallInterval = MIN_GET_TEST_STATUS_RPC_CALL_INTERVAL;
    } else if (rpcCallInterval.compareTo(MAX_GET_TEST_STATUS_RPC_CALL_INTERVAL) > 0) {
      rpcCallInterval = MAX_GET_TEST_STATUS_RPC_CALL_INTERVAL;
    }
    return rpcCallInterval;
  }

  /**
   * Recursively create {@link GetTestStatusRequest.Builder} for root testInfo and all its sub-tests
   */
  @VisibleForTesting
  GetTestStatusRequest.Builder createGetTestStatusRequest(
      TestInfo testInfo, Map<String, Integer> remoteLogOffset) {
    String testId = testInfo.locator().getId();
    Integer offset = remoteLogOffset.get(testId);
    if (offset == null) {
      offset = 0;
      remoteLogOffset.put(testId, 0);
    }

    GetTestStatusRequest.Builder builder =
        GetTestStatusRequest.newBuilder()
            .setJobId(testInfo.jobInfo().locator().getId())
            .setTestId(testId)
            .setTestLogOffset(offset);
    // Request known sub-testInfo.
    for (TestInfo subTestInfo : testInfo.subTests().getAll().values()) {
      builder.addSubTest(createGetTestStatusRequest(subTestInfo, remoteLogOffset).build());
    }
    return builder;
  }

  /** Recursively update root testInfo and all its sub-tests */
  @VisibleForTesting
  void updateTestStatus(
      GetTestStatusResponse resp, TestInfo testInfo, Map<String, Integer> remoteLogOffset)
      throws MobileHarnessException {
    new LabRpcProtoConverter()
        .updateTestStatus(
            resp, testInfo, remoteLogOffset, testMessageManager, getTestMessagePoster());
    if (resp.getDeviceFeatureCount() > 0) {
      try {
        updateDeviceStatus(resp.getDeviceFeatureList());
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            InfraErrorId.CLIENT_REMOTE_MODE_UPDATE_DEVICE_FEATURE_ERROR,
            "Failed to update the device features",
            e);
      }
    }
  }

  /**
   * Recursively create {@link GetTestGenDataRequest.Builder} from root testInfo and all its
   * sub-tests
   */
  @VisibleForTesting
  GetTestGenDataRequest.Builder createGetTestGenDataRequest(TestInfo testInfo) {
    GetTestGenDataRequest.Builder builder =
        GetTestGenDataRequest.newBuilder()
            .setJobId(testInfo.jobInfo().locator().getId())
            .setTestId(testInfo.locator().getId());
    // Request known sub-testInfo.
    for (TestInfo subTestInfo : testInfo.subTests().getAll().values()) {
      builder.addSubTest(createGetTestGenDataRequest(subTestInfo).build());
    }
    return builder;
  }

  /** Update testInfo of the root and sub-tests with {@link GetTestGenDataResponse} */
  @VisibleForTesting
  void updateTestGenData(GetTestGenDataResponse resp, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    new LabRpcProtoConverter().updateTestInfoFromTestGenData(resp, testInfo);
    String testId = testInfo.locator().getId();
    String subTestLogPostfix =
        testInfo.isRootTest()
            ? ""
            : String.format(" for sub_test %s(%s)", testInfo.locator().getName(), testId);

    if (Flags.instance().enableClientFileTransfer.getNonNull()) {
      // Downloads test generated files.
      downloadTestGeneratedFiles(resp, testInfo, subTestLogPostfix);
    }

    // Update sub-testInfo received requested and returned from lab server.
    Set<String> leftOverSubTestIds =
        testInfo.subTests().getAll().values().stream()
            .map(t -> t.locator().getId())
            .collect(toCollection(LinkedHashSet::new));

    for (SubTestGenDataResponse subTestResp : resp.getSubTestList()) {
      // GetTestGenData request is always a follow-up of GetTestStatus request, it is safe to
      // assume there won't be new sub-tests created on the lab side after GetTestStatus return the
      // latest subTestInfo.
      String subTestId = subTestResp.getTestId();
      updateTestGenData(subTestResp.getGenData(), testInfo.subTests().getById(subTestId));
      leftOverSubTestIds.remove(subTestId);
    }

    for (String subTestId : leftOverSubTestIds) {
      testInfo.subTests().remove(subTestId);
      testInfo.log().atInfo().alsoTo(logger).log("The sub test %s has been removed.", subTestId);
    }
  }

  @VisibleForTesting
  ContainerModePreference getContainerModePreference(boolean defaultSandboxPreference) {
    Optional<ContainerModePreference> preferenceFromParam =
        getTestInfo()
            .jobInfo()
            .params()
            .toNewParams()
            .get(JobInfo.PARAM_CONTAINER_MODE_PREFERENCE)
            .filter(((Predicate<String>) String::isEmpty).negate())
            .map(String::toUpperCase)
            .flatMap(
                paramValue -> {
                  Optional<ContainerModePreference> parsedResult =
                      Enums.getIfPresent(ContainerModePreference.class, paramValue).toJavaUtil();
                  if (parsedResult.isEmpty()) {
                    logger.atWarning().log("Unrecognized ContainerModePreference [%s]", paramValue);
                  }
                  return parsedResult;
                });
    if (preferenceFromParam.isPresent() && isMandatory(preferenceFromParam.get())) {
      return preferenceFromParam.get();
    }

    if (getTestInfo()
        .properties()
        .getBoolean(PropertyName.Test.RETRY_AFTER_CONTAINER_FAILS)
        .orElse(false)) {
      getTestInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Use mandatory non container mode preference when retrying after sandbox test"
                  + " failed");
      return ContainerModePreference.MANDATORY_NON_CONTAINER;
    }
    if (!getTestInfo().properties().getBoolean(PropertyName.Test.CONTAINER_MODE).orElse(true)) {
      getTestInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Use mandatory non container mode preference when the foregoing test runs as"
                  + " non-container.");
      return ContainerModePreference.MANDATORY_NON_CONTAINER;
    }
    return preferenceFromParam.orElse(
        defaultSandboxPreference
            ? ContainerModePreference.CONTAINER
            : ContainerModePreference.NON_CONTAINER);
  }

  private SandboxSetting getSandboxSetting() {
    return SandboxSetting.newBuilder()
        .setSandboxMemoryMb(
            getTestInfo()
                .jobInfo()
                .params()
                .toNewParams()
                .get(JobInfo.PARAM_SANDBOX_MEMORY_MB)
                .map(Integer::parseInt)
                .orElse(0))
        .build();
  }

  /**
   * If this test is created because a foregoing sandbox mode test fails, we run it as non-sandbox
   * mode for higher test success rate.
   */
  @VisibleForTesting
  SandboxModePreference getSandboxModePreference(boolean defaultSandboxPreference) {
    Optional<SandboxModePreference> preferenceFromParam =
        getTestInfo()
            .jobInfo()
            .params()
            .toNewParams()
            .get(JobInfo.PARAM_SANDBOX_MODE_PREFERENCE)
            .filter(((Predicate<String>) String::isEmpty).negate())
            .map(String::toUpperCase)
            .flatMap(
                paramValue -> {
                  Optional<SandboxModePreference> parsedResult =
                      Enums.getIfPresent(SandboxModePreference.class, paramValue).toJavaUtil();
                  if (parsedResult.isEmpty()) {
                    logger.atWarning().log("Unrecognized SandboxModePreference [%s]", paramValue);
                  }
                  return parsedResult;
                });
    if (preferenceFromParam.isPresent() && isMandatory(preferenceFromParam.get())) {
      return preferenceFromParam.get();
    }

    if (getTestInfo()
        .properties()
        .getBoolean(PropertyName.Test.RETRY_AFTER_SANDBOX_FAILS)
        .orElse(false)) {
      getTestInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Use mandatory non sandbox mode preference when retrying after sandbox test"
                  + " failed");
      return SandboxModePreference.MANDATORY_NON_SANDBOX;
    }
    if (!getTestInfo().properties().getBoolean(PropertyName.Test.SANDBOX_MODE).orElse(true)) {
      getTestInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Use mandatory non sandbox mode preference when the foregoing test runs as"
                  + " non-sandbox.");
      return SandboxModePreference.MANDATORY_NON_SANDBOX;
    }
    return preferenceFromParam.orElse(
        defaultSandboxPreference
            ? SandboxModePreference.SANDBOX
            : SandboxModePreference.NON_SANDBOX);
  }

  private static Timeout getTestTimeout(
      com.google.devtools.mobileharness.api.model.job.in.Timeout jobTimeout) {
    com.google.devtools.mobileharness.api.model.job.in.Timeout labServerTestTimeout =
        TimeoutUtil.finalizeLabServerTestTimeout(jobTimeout);
    return Timeout.newBuilder()
        .setJobTimeoutMs(labServerTestTimeout.jobTimeout().toMillis())
        .setTestTimeoutMs(labServerTestTimeout.testTimeout().toMillis())
        .setStartTimeoutMs(labServerTestTimeout.startTimeout().toMillis())
        .build();
  }

  private static boolean isMandatory(SandboxModePreference preference) {
    return preference.equals(SandboxModePreference.MANDATORY_NON_SANDBOX)
        || preference.equals(SandboxModePreference.MANDATORY_SANDBOX);
  }

  private static boolean isMandatory(ContainerModePreference preference) {
    return preference.equals(ContainerModePreference.MANDATORY_NON_CONTAINER)
        || preference.equals(ContainerModePreference.MANDATORY_CONTAINER);
  }

  private class CachedTestMessageForwarder extends CacheableTestMessageHandler {

    private CachedTestMessageForwarder(ListeningExecutorService threadPool) {
      super(threadPool, "cached-test-message-forwarder-" + getTestInfo().locator().getId());
    }

    @Subscribe
    private void receiveTestMessage(TestMessageEvent testMessageEvent) {
      submitTestMessage(testMessageEvent.getTestMessageInfo());
    }

    @Override
    public void handleTestMessage(TestMessageInfo testMessageInfo) {
      if (!testMessageInfo.isRemote()) {
        logger.atFine().log("Forward test message %s", testMessageInfo);
        ForwardTestMessageRequest request =
            ForwardTestMessageRequest.newBuilder()
                .setTestId(getTestInfo().locator().getId())
                .setTestMessage(
                    TestMessage.newBuilder()
                        .putAllMessageContent(testMessageInfo.message())
                        .addAllSubTestIdChain(testMessageInfo.subTestIdChain()))
                .build();
        try {
          ExecTestStub execTestStub = getTestEngineExecTestStub();
          execTestStub.forwardTestMessage(request, impersonationUser);
        } catch (RpcExceptionWithErrorId e) {
          logTestEventError(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_REMOTE_MODE_TEST_MESSAGE_FORWARD_ERROR,
                  String.format("Failed to forward message %s", testMessageInfo),
                  e),
              "test message event");
        }
      }
    }
  }
}
