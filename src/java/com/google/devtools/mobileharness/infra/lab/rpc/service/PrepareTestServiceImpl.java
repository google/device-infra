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
import static com.google.devtools.deviceaction.common.utils.TimeUtils.toProtoDuration;
import static com.google.devtools.deviceaction.common.utils.TimeUtils.toProtoTimestamp;
import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.shared.util.path.PathUtil;
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
import com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner;
import com.google.devtools.mobileharness.infra.container.controller.ProxyToDirectTestRunner;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator.StubbyLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineStatus;
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
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.checker.ServiceSideVersionChecker;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Implementation of {@code PrepareTestService}. */
@Singleton
public class PrepareTestServiceImpl {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SATELLITE_LAB_TEST_ENGINE_RESOURCE_PATH =
      "/com/google/devtools/mobileharness/infra/container/testengine/SatelliteLabTestEngine_deploy.jar";

  private final LocalDeviceRunnerProvider deviceRunnerProvider;
  private final JobManager jobManager;
  private final ProxyTestManager testManager;
  private final ServiceSideVersionChecker versionChecker =
      new ServiceSideVersionChecker(Version.LAB_VERSION, Version.MIN_CLIENT_VERSION);
  private final LocalFileUtil localFileUtil;
  private final NetUtil netUtil;
  private final ResUtil resUtil = new ResUtil();
  private final SystemUtil systemUtil;
  private final boolean servViaStubby;
  private final int labRpcPort;
  private final boolean servViaCloudRpc;
  private final String cloudRpcDnsName;
  private final String cloudRpcShardName;
  private final EventBus globalInternalEventBus;

  @Inject
  public PrepareTestServiceImpl(
      LocalDeviceRunnerProvider deviceRunnerProvider,
      JobManager jobManager,
      ProxyTestManager testManager,
      LocalFileUtil localFileUtil,
      NetUtil netUtil,
      SystemUtil systemUtil,
      boolean servViaStubby,
      int labRpcPort,
      boolean servViaCloudRpc,
      String cloudRpcDnsName,
      String cloudRpcShardName,
      EventBus globalInternalEventBus) {
    this.deviceRunnerProvider = deviceRunnerProvider;
    this.jobManager = jobManager;
    this.testManager = testManager;
    this.localFileUtil = localFileUtil;
    this.netUtil = netUtil;
    this.systemUtil = systemUtil;
    this.servViaStubby = servViaStubby;
    this.labRpcPort = labRpcPort;
    this.servViaCloudRpc = servViaCloudRpc;
    this.cloudRpcDnsName = cloudRpcDnsName;
    this.cloudRpcShardName = cloudRpcShardName;
    this.globalInternalEventBus = globalInternalEventBus;
  }

  @CanIgnoreReturnValue
  public CreateTestResponse createTest(CreateTestRequest req)
      throws MobileHarnessException, InterruptedException {
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
    TestExecutionUnit testExecutionUnit = createTestExecutionUnit(req.getTest(), jobExecutionUnit);

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

  public GetTestEngineStatusResponse getTestEngineStatus(GetTestEngineStatusRequest req)
      throws MobileHarnessException {
    return getGetTestEngineStatusResponse(testManager.getProxyTestRunner(req.getTestId()));
  }

  @CanIgnoreReturnValue
  public StartTestEngineResponse startTestEngine(StartTestEngineRequest req)
      throws MobileHarnessException {
    testManager.getProxyTestRunner(req.getTestId()).asyncStartTestEngine();
    return StartTestEngineResponse.getDefaultInstance();
  }

  @CanIgnoreReturnValue
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

  private ProxyTestRunner createTestRunner(
      ContainerSetting containerSetting,
      TestRunnerLauncher<? super ProxyTestRunner> launcher,
      TestExecutionUnit testExecutionUnit,
      Allocation allocation,
      List<Device> devices,
      List<String> decorators)
      throws MobileHarnessException, InterruptedException {
    return new ProxyToDirectTestRunner(
        launcher,
        testExecutionUnit,
        allocation,
        new LabFileNotifier(),
        getProxiedTestEngineLocator());
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
