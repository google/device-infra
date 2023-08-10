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

package com.google.devtools.mobileharness.infra.lab.rpc.service;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.protobuf.TextFormat.shortDebugString;
import static com.google.protobuf.util.JavaTimeConversions.toProtoDuration;
import static com.google.protobuf.util.JavaTimeConversions.toProtoTimestamp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.time.Sleeper;
import com.google.common.util.PathUtil;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.api.model.job.in.Timeout;
import com.google.devtools.mobileharness.api.model.job.out.Timing;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Job.JobFeature;
import com.google.devtools.mobileharness.infra.bridge.proto.ProxyMetadata;
import com.google.devtools.mobileharness.infra.container.controller.ContainerFileNotificationCache;
import com.google.devtools.mobileharness.infra.container.controller.ContainerTestRunner;
import com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner;
import com.google.devtools.mobileharness.infra.container.controller.ProxyToDirectTestRunner;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.ContainerModePreference;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.SandboxModePreference;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator.CloudRpcLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator.StubbyLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineStatus;
import com.google.devtools.mobileharness.infra.container.sandbox.SandboxFlag;
import com.google.devtools.mobileharness.infra.container.sandbox.SandboxRunner;
import com.google.devtools.mobileharness.infra.container.util.FileUnitConverter;
import com.google.devtools.mobileharness.infra.container.util.SandboxUtil;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunnerProvider;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.controller.test.manager.TestStartedException;
import com.google.devtools.mobileharness.infra.controller.test.model.JobExecutionUnit;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.infra.lab.controller.JobManager;
import com.google.devtools.mobileharness.infra.lab.controller.util.LabFileNotifier;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestService;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest.ContainerSetting;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestResponse.ContainerInfo;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.TestRunnerTiming;
import com.google.devtools.mobileharness.shared.trace.MobileHarnessLocalTraceSpanBuilder;
import com.google.devtools.mobileharness.shared.trace.MobileHarnessWithLocalTraceSpan;
import com.google.devtools.mobileharness.shared.trace.TraceClient;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil.GcsParams;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil.GcsParams.Scope;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.checker.ServiceSideVersionChecker;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckResponse;
import com.google.errorprone.annotations.ResultIgnorabilityUnspecified;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.comm.cloudrpc.CloudRpcServerType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import com.google.wireless.qa.mobileharness.shared.util.PortManager;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Implementation of {@link PrepareTestService}. */
@Singleton
public class PrepareTestServiceImpl {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SATELLITE_LAB_TEST_ENGINE_RESOURCE_PATH =
      "/com/google/devtools/mobileharness/infra/container/testengine/SatelliteLabTestEngine_deploy.jar";

  private final LocalDeviceRunnerProvider deviceRunnerProvider;
  private final JobManager jobManager;
  private final ProxyTestManager testManager;
  private final ContainerFileNotificationCache containerFileNotificationCache;
  private final ServiceSideVersionChecker versionChecker =
      new ServiceSideVersionChecker(Version.LAB_VERSION, Version.MIN_CLIENT_VERSION);
  private final LocalFileUtil localFileUtil;
  private final NetUtil netUtil;
  private final ResUtil resUtil = new ResUtil();
  private final SandboxUtil sandboxUtil;
  private final SystemUtil systemUtil;
  private final boolean servViaStubby;
  private final int labRpcPort;
  private final boolean servViaCloudRpc;
  private final String cloudRpcDnsName;
  private final String cloudRpcShardName;
  private final TraceClient traceClient;
  private final EventBus globalInternalEventBus;

  @Inject
  public PrepareTestServiceImpl(
      LocalDeviceRunnerProvider deviceRunnerProvider,
      JobManager jobManager,
      ProxyTestManager testManager,
      ContainerFileNotificationCache containerFileNotificationCache,
      LocalFileUtil localFileUtil,
      NetUtil netUtil,
      SandboxUtil sandboxUtil,
      SystemUtil systemUtil,
      boolean servViaStubby,
      int labRpcPort,
      boolean servViaCloudRpc,
      String cloudRpcDnsName,
      String cloudRpcShardName,
      EventBus globalInternalEventBus,
      TraceClient traceClient) {
    this.deviceRunnerProvider = deviceRunnerProvider;
    this.jobManager = jobManager;
    this.testManager = testManager;
    this.containerFileNotificationCache = containerFileNotificationCache;
    this.localFileUtil = localFileUtil;
    this.netUtil = netUtil;
    this.sandboxUtil = sandboxUtil;
    this.systemUtil = systemUtil;
    this.servViaStubby = servViaStubby;
    this.labRpcPort = labRpcPort;
    this.servViaCloudRpc = servViaCloudRpc;
    this.cloudRpcDnsName = cloudRpcDnsName;
    this.cloudRpcShardName = cloudRpcShardName;
    this.globalInternalEventBus = globalInternalEventBus;
    this.traceClient = traceClient;
  }

  @ResultIgnorabilityUnspecified
  public CreateTestResponse createTest(CreateTestRequest req)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessLocalTraceSpanBuilder traceSpanBuilder =
        new MobileHarnessLocalTraceSpanBuilder(
                getClass().getSimpleName(), "createTest", traceClient)
            .setParent(req.getParentSpan().getId());
    req.getParentSpan().getTags().getTagList().forEach(traceSpanBuilder::addTag);
    try (MobileHarnessWithLocalTraceSpan ignored = traceSpanBuilder.build()) {
      logger.atInfo().log("CreateTestRequest [%s]", req);

      // Checks the client version.
      VersionCheckResponse versionCheckResponse =
          versionChecker.checkStub(req.getVersionCheckRequest());

      // Gets LocalDeviceRunner.
      List<LocalDeviceTestRunner> deviceRunners = getDeviceRunners(req.getDeviceIdList());

      // Checks the job feature.
      checkJobFeature(req.getJob().getJobFeature(), deviceRunners);

      // Creates JobExecutionUnit if absent.
      JobExecutionUnit jobExecutionUnit = createAndAddJobIfAbsent(req.getJob());

      // Creates TestExecutionUnit.
      TestExecutionUnit testExecutionUnit =
          createTestExecutionUnit(req.getTest(), jobExecutionUnit);

      List<Device> devices =
          deviceRunners.stream().map(LocalDeviceTestRunner::getDevice).collect(toImmutableList());

      // Creates TestRunnerLauncher.
      TestRunnerLauncher<? super ProxyTestRunner> launcher =
          new LocalDeviceTestRunnerLauncher(
              deviceRunners.get(0), deviceRunners.stream().skip(1L).collect(Collectors.toList()));

      // Creates Allocation.
      Allocation allocation =
          new Allocation(
              testExecutionUnit.locator(),
              req.getDeviceIdList().stream()
                  .map(deviceId -> DeviceLocator.of(deviceId, LabLocator.LOCALHOST))
                  .collect(Collectors.toList()));

      List<String> decorators =
          req.getJob().getJobFeature().getDeviceRequirements().getDeviceRequirementList().stream()
              .flatMap(deviceRequirementList -> deviceRequirementList.getDecoratorList().stream())
              .collect(Collectors.toList());

      // Creates TestRunner.
      ProxyTestRunner proxyTestRunner =
          createTestRunner(
              req.getContainerSetting(),
              launcher,
              testExecutionUnit,
              allocation,
              devices,
              decorators);

      // Adds TestRunner to JobManager.
      jobManager.addTestIfAbsent(proxyTestRunner);

      // Starts TestRunner.
      try {
        testManager.startTest(proxyTestRunner);
      } catch (TestStartedException e) {
        logger.atSevere().withCause(e).log(
            "Skip duplicated CreateTest request for the allocation %s. "
                + "See b/38099373 for more detail.",
            allocation);
      } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
        throw new MobileHarnessException(
            InfraErrorId.LAB_RPC_PREPARE_TEST_TEST_RUNNER_START_ERROR,
            String.format("Failed to start test %s", testExecutionUnit.locator().id()),
            e);
      }

      // Waits until the test engine is ready if it does not need starting license.
      waitUntilTestEngineReady(proxyTestRunner, req.getContainerSetting());

      // Creates the response.
      CreateTestResponse response =
          CreateTestResponse.newBuilder()
              .setVersionCheckResponse(versionCheckResponse)
              .addAllDeviceFeature(
                  deviceRunners.stream()
                      .map(LocalDeviceTestRunner::getDevice)
                      .map(Device::toFeature)
                      .collect(Collectors.toList()))
              .setContainerInfo(
                  ContainerInfo.newBuilder()
                      .setIsContainerMode(proxyTestRunner.isContainerMode())
                      .setIsSandboxMode(proxyTestRunner.isSandboxMode())
                      .build())
              .setGetTestEngineStatusResponse(getGetTestEngineStatusResponse(proxyTestRunner))
              .build();

      logger.atInfo().log("CreateTestResponse [%s]", shortDebugString(response));

      return response;
    }
  }

  public GetTestEngineStatusResponse getTestEngineStatus(GetTestEngineStatusRequest req)
      throws MobileHarnessException {
    return getGetTestEngineStatusResponse(testManager.getProxyTestRunner(req.getTestId()));
  }

  @ResultIgnorabilityUnspecified
  public StartTestEngineResponse startTestEngine(StartTestEngineRequest req)
      throws MobileHarnessException {
    testManager.getProxyTestRunner(req.getTestId()).asyncStartTestEngine();
    return StartTestEngineResponse.getDefaultInstance();
  }

  @ResultIgnorabilityUnspecified
  public CloseTestResponse closeTest(CloseTestRequest req) {
    logger.atInfo().log("CloseTestRequest [%s]", req);

    CloseTestResponse.Builder response = CloseTestResponse.newBuilder();

    try {
      jobManager.markTestClientPostRunDone(req.getJobId(), req.getTestId());
      response.setTestTiming(getTestTiming(req.getTestId()));
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to close test %s", req.getTestId());
    } finally {
      testManager.closeTest(req.getTestId());
    }

    return response.build();
  }

  private TestRunnerTiming getTestTiming(String testId) throws MobileHarnessException {
    ProxyTestRunner proxyTestRunner = testManager.getProxyTestRunner(testId);
    Optional<Instant> testStartInstant = proxyTestRunner.getTestRunnerStartInstant();
    Optional<Instant> testExecuteInstant = proxyTestRunner.getTestRunnerExecuteInstant();
    Optional<Duration> testEngineSetupTime = proxyTestRunner.getTestEngineSetupTime();

    TestRunnerTiming.Builder testRunnerTiming = TestRunnerTiming.newBuilder();
    testStartInstant.ifPresent(
        instant -> testRunnerTiming.setStartTimestamp(toProtoTimestamp(instant)));
    testExecuteInstant.ifPresent(
        instant -> testRunnerTiming.setExecuteTimestamp(toProtoTimestamp(instant)));
    testEngineSetupTime.ifPresent(
        duration -> testRunnerTiming.setTestEngineSetupTime(toProtoDuration(duration)));

    return testRunnerTiming.build();
  }

  private void checkJobFeature(JobFeature jobFeature, List<LocalDeviceTestRunner> deviceRunners)
      throws MobileHarnessException {
    // TODO: Checks device requirement directly rather than job type.
    JobType primaryDeviceJobType =
        JobType.newBuilder()
            .setDriver(jobFeature.getDriver())
            .setDevice(jobFeature.getDeviceRequirements().getDeviceRequirement(0).getDeviceType())
            .addAllDecorator(
                Lists.reverse(
                    jobFeature.getDeviceRequirements().getDeviceRequirement(0).getDecoratorList()))
            .build();

    for (LocalDeviceTestRunner deviceRunner : deviceRunners) {
      if (deviceRunner.isJobSupported(primaryDeviceJobType)) {
        return;
      }
    }

    throw new MobileHarnessException(
        InfraErrorId.LAB_RPC_PREPARE_TEST_JOB_TYPE_NOT_SUPPORTED,
        String.format("Job type [%s] is not supported by MH lab", primaryDeviceJobType));
  }

  private JobExecutionUnit createAndAddJobIfAbsent(CreateTestRequest.Job job) {
    String jobId = job.getJobId();
    JobLocator jobLocator = JobLocator.of(jobId, job.getJobName());
    String driver = job.getJobFeature().getDriver();
    Timeout jobTimeout = Timeout.fromProto(job.getTimeout());
    Timing jobTiming = new Timing(Instant.ofEpochMilli(job.getJobCreateTimeMs()));
    jobTiming.start(Instant.ofEpochMilli(job.getJobStartTimeMs()));
    Dirs jobDirs =
        new Dirs(
            PathUtil.join(DirUtil.getPublicGenDir(), jobId) /* genFileDir */,
            PathUtil.join(DirUtil.getPrivateGenDir(), jobId) /* tmpFileDir */,
            PathUtil.join(DirUtil.getRunDir(), jobId) /* runFileDir */,
            null /* remoteFileDir */,
            true /* hasTestSubdirs */,
            localFileUtil);

    JobExecutionUnit jobExecutionUnit =
        JobExecutionUnit.create(jobLocator, driver, jobTimeout, jobTiming, jobDirs);
    return jobManager.addJobIfAbsent(jobExecutionUnit, job.getDisableMasterSyncing());
  }

  private TestExecutionUnit createTestExecutionUnit(
      CreateTestRequest.Test test, JobExecutionUnit job) {
    String testId = test.getTestId();
    TestLocator testLocator = TestLocator.of(testId, test.getTestName(), job.locator());
    Timing testTiming = new Timing(Instant.ofEpochMilli(test.getTestCreateTimeMs()));
    testTiming.start(Instant.ofEpochMilli(test.getTestStartTimeMs()));
    return new TestExecutionUnit(testLocator, testTiming, job);
  }

  private List<LocalDeviceTestRunner> getDeviceRunners(List<String> deviceIds)
      throws MobileHarnessException {
    List<LocalDeviceTestRunner> deviceRunners = new ArrayList<>();
    for (String deviceId : deviceIds) {
      LocalDeviceTestRunner deviceRunner = deviceRunnerProvider.getLocalDeviceRunner(deviceId);
      MobileHarnessExceptions.check(
          deviceRunner != null,
          InfraErrorId.LAB_RPC_PREPARE_TEST_DEVICE_NOT_FOUND,
          () -> String.format("Device [%s] does not exist", deviceId));
      MobileHarnessExceptions.check(
          deviceRunner.isAlive(),
          InfraErrorId.LAB_RPC_PREPARE_TEST_DEVICE_NOT_ALIVE,
          () ->
              String.format(
                  "Device [%s] is not alive with status [%s] when preparing test",
                  deviceId, deviceRunner.getDeviceStatus()));
      deviceRunners.add(deviceRunner);
    }
    return deviceRunners;
  }

  @VisibleForTesting
  ContainerInfo calculateContainerMode(
      ContainerSetting containerSetting,
      List<Device> devices,
      List<String> decorators,
      SandboxUtil sandboxUtil)
      throws MobileHarnessException {
    // Handles unspecified preferences.
    ContainerModePreference containerModePreference = containerSetting.getContainerModePreference();
    if (containerModePreference == ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED) {
      containerModePreference = ContainerModePreference.MANDATORY_NON_CONTAINER;
    }
    SandboxModePreference sandboxModePreference = containerSetting.getSandboxModePreference();
    if (sandboxModePreference == SandboxModePreference.SANDBOX_MODE_PREFERENCE_UNSPECIFIED) {
      sandboxModePreference = SandboxModePreference.MANDATORY_NON_SANDBOX;
    }

    // Handles illegal preference combination.
    if (containerModePreference == ContainerModePreference.MANDATORY_NON_CONTAINER
        && sandboxModePreference == SandboxModePreference.MANDATORY_SANDBOX) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_PREPARE_TEST_ILLEGAL_CONTAINER_MODE_PREFERENCE,
          "Mandatory sandbox mode with mandatory non-container mode is illegal");
    }

    ContainerInfo.Builder info = ContainerInfo.newBuilder();

    // Determines final container mode.
    // Use container mode when 1 of the 2 requirements below is met when container mode is supported
    // by allocated devices:
    // 1. Container mode preference is MANDATORY_CONTAINER or CONTAINER.
    // 2. Sandbox mode preference is MANDATORY_SANDBOX.
    Optional<MobileHarnessException> containerSupportException =
        sandboxUtil.checkContainerMode(devices, decorators);
    if ((containerModePreference == ContainerModePreference.MANDATORY_CONTAINER
            || containerModePreference == ContainerModePreference.CONTAINER)
        || (sandboxModePreference == SandboxModePreference.MANDATORY_SANDBOX)) {
      if (containerSupportException.isPresent()) {
        info.setDetail(
            ErrorModelConverter.toExceptionDetail(
                containerSupportException.get(), /* addStackTrace= */ false));
      } else {
        info.setIsContainerMode(true);
      }
    }

    if (!info.getIsContainerMode()
        && (containerModePreference == ContainerModePreference.MANDATORY_CONTAINER
            || sandboxModePreference == SandboxModePreference.MANDATORY_SANDBOX)) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_PREPARE_TEST_ILLEGAL_CONTAINER_MODE_PREFERENCE,
          "Requesting mandatory container/mandatory sandbox mode on devices/decorators without"
              + " container mode support.",
          containerSupportException.get());
    }

    // Determines final sandbox mode.
    // Use sandbox mode when the 3 requirements below are met:
    // 1. Use container mode.
    // 2. Sandbox mode is supported by all allocated devices.
    // 3. Sandbox preference is MANDATORY_SANDBOX or SANDBOX.
    Optional<MobileHarnessException> sandboxSupportException =
        sandboxUtil.checkSandboxMode(devices, decorators);
    if (sandboxModePreference == SandboxModePreference.SANDBOX
        || sandboxModePreference == SandboxModePreference.MANDATORY_SANDBOX) {
      if (info.getIsContainerMode()) {
        if (sandboxSupportException.isPresent()) {
          info.setDetail(
              ErrorModelConverter.toExceptionDetail(
                  sandboxSupportException.get(), /* addStackTrace= */ false));
        } else {
          info.setIsSandboxMode(true);
        }
      } else if (!info.hasDetail()) {
        info.setDetail(
            ErrorModelConverter.toExceptionDetail(
                new MobileHarnessException(
                    InfraErrorId.LAB_RPC_PREPARE_TEST_ILLEGAL_SANDBOX_MODE_PREFERENCE,
                    "Requesting sandbox mode while requesting non-container mode or mandatory"
                        + " non-container mode.")));
      }
    }

    if (!info.getIsSandboxMode()
        && sandboxModePreference == SandboxModePreference.MANDATORY_SANDBOX) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_PREPARE_TEST_ILLEGAL_SANDBOX_MODE_PREFERENCE,
          "Requesting mandatory sandbox mode on devices/decorators without sandbox mode support.",
          sandboxSupportException.get());
    }

    return info.build();
  }

  private ProxyTestRunner createTestRunner(
      ContainerSetting containerSetting,
      TestRunnerLauncher<? super ProxyTestRunner> launcher,
      TestExecutionUnit testExecutionUnit,
      Allocation allocation,
      List<Device> devices,
      List<String> decorators)
      throws MobileHarnessException, InterruptedException {
    String testEngineBinaryPath = null;
    try {
      testEngineBinaryPath = getTestEngineBinaryPath(testExecutionUnit);
      logger.atInfo().log("Test engine binary path: %s", testEngineBinaryPath);
    } catch (MobileHarnessException | RuntimeException e) {
      logger.atWarning().withCause(e).log("Failed to get test engine binary");
    }

    // Calculates the container mode.
    ContainerInfo containerInfo =
        testEngineBinaryPath == null
            ? ContainerInfo.getDefaultInstance()
            : calculateContainerMode(containerSetting, devices, decorators, sandboxUtil);
    logger.atInfo().log(
        "Container info of test %s: %s", testExecutionUnit.locator().id(), containerInfo);

    if (containerInfo.getIsContainerMode()) {
      CommandExecutor commandExecutor =
          CommandExecutor.newBuilder()
              .useDefaultThreadPool(false /* propagateTraceContext */)
              .build();
      return new ContainerTestRunner(
          launcher,
          testExecutionUnit,
          allocation,
          containerInfo.getIsSandboxMode(),
          globalInternalEventBus,
          PathUtil.dirname(testEngineBinaryPath),
          PathUtil.basename(testEngineBinaryPath),
          labRpcPort,
          servViaCloudRpc,
          cloudRpcDnsName,
          devices,
          containerSetting.getNeedStartingLicense(),
          containerSetting.getSandboxSetting(),
          new SandboxRunner(commandExecutor),
          PortManager.getInstance(),
          netUtil,
          sandboxUtil,
          systemUtil,
          new FileUnitConverter(),
          commandExecutor,
          resUtil,
          localFileUtil,
          containerFileNotificationCache,
          Sleeper.defaultSleeper());
    } else {
      return new ProxyToDirectTestRunner(
          launcher,
          testExecutionUnit,
          allocation,
          new LabFileNotifier(),
          traceClient,
          getProxiedTestEngineLocator());
    }
  }

  private String getTestEngineBinaryPath(
      @SuppressWarnings("unused") TestExecutionUnit testExecutionUnit)
      throws MobileHarnessException, InterruptedException {
    // TODO: Get binary from client / based on test type.
    // In shared lab we download the test engine binary from GCS. Here we read the
    // path from a flag which passed in at lab server start up.
    if (DeviceUtil.inSharedLab()) {
      logger.atInfo().log("Using test engine binary from DeviceMaster");
      if (!localFileUtil.isFileExist(SandboxFlag.getTestEnginePath())) {
        downloadTestEngineBinary();
      }
      return SandboxFlag.getTestEnginePath();
    }
    logger.atInfo().log("Using test engine binary from lab server");
    return resUtil.getResourceFile(getClass(), SATELLITE_LAB_TEST_ENGINE_RESOURCE_PATH);
  }

  // Download the TestEngine if it's not already downloaded. This is needed in case the test run
  // before TestEngine binary is downloaded.
  private void downloadTestEngineBinary() throws MobileHarnessException, InterruptedException {
    GcsUtil gcsUtil =
        new GcsUtil(
            new GcsParams(
                GcsUtil.DEVICE_MASTER_APP_NAME,
                GcsUtil.SHARED_LAB_GCS_BUCKET_NAME,
                Scope.READ_ONLY,
                /* useAppDefault= */ true));
    gcsUtil.copyFileToLocalIfNotExist(
        SandboxFlag.getTestEngineGcsPath(), Path.of(SandboxFlag.getTestEnginePath()));
  }

  private TestEngineLocator getProxiedTestEngineLocator() throws MobileHarnessException {
    TestEngineLocator.Builder builder = TestEngineLocator.newBuilder();
    String labHostName = netUtil.getLocalHostName();

    if (servViaStubby) {
      StubbyLocator.Builder stubbyLocator =
          StubbyLocator.newBuilder().setHostName(labHostName).setPort(labRpcPort);
      netUtil.getUniqueHostIpOrEmpty().ifPresent(stubbyLocator::setIp);
      builder.setEnableStubby(true).setStubbyLocator(stubbyLocator);
    }
    if (servViaCloudRpc) {
      builder
          .setEnableCloudRpc(true)
          .setCloudRpcLocator(
              CloudRpcLocator.newBuilder()
                  .setProxyMetadata(
                      ProxyMetadata.newBuilder()
                          .setEndpoint(CloudRpcServerType.LAB_SERVER.getEndpoint())
                          .setShard(cloudRpcShardName))
                  .setFileTransferProxyMetadata(
                      ProxyMetadata.newBuilder()
                          .setEndpoint(CloudRpcServerType.LAB_CLOUD_FILE_TRANSFER.getEndpoint())
                          .setShard(cloudRpcShardName)));
    }

    return builder.build();
  }

  private void waitUntilTestEngineReady(
      ProxyTestRunner proxyTestRunner, ContainerSetting containerSetting)
      throws MobileHarnessException {
    try {
      Duration testEngineSyncStartingTimeout =
          containerSetting.getNeedStartingLicense()
              ? Duration.ZERO
              : Duration.ofMillis(containerSetting.getSyncStartingTimeoutMs());
      logger.atInfo().log("Wait [%s] until test engine is ready", testEngineSyncStartingTimeout);
      proxyTestRunner.waitUntilTestEngineReady(testEngineSyncStartingTimeout);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_PREPARE_TEST_INTERRUPTED,
          "Interrupted when waiting until test engine is ready",
          e);
    }
  }

  private GetTestEngineStatusResponse getGetTestEngineStatusResponse(
      ProxyTestRunner proxyTestRunner) {
    TestEngineStatus testEngineStatus = proxyTestRunner.getTestEngineStatus();
    Optional<TestEngineLocator> testEngineLocator = proxyTestRunner.getTestEngineLocator();
    Optional<MobileHarnessException> testEngineError = proxyTestRunner.getTestEngineError();
    GetTestEngineStatusResponse.Builder response =
        GetTestEngineStatusResponse.newBuilder()
            .setHasTestEngineLocator(testEngineLocator.isPresent())
            .setTestEngineStatus(testEngineStatus);
    testEngineLocator.ifPresent(response::setTestEngineLocator);
    testEngineError.map(ErrorModelConverter::toExceptionDetail).ifPresent(response::setError);
    return response.build();
  }
}
