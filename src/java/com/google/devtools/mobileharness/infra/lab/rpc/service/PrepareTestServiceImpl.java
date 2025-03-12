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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoDuration;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoTimestamp;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
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
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator.GrpcLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator.StubbyLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineStatus;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.controller.test.manager.TestStartedException;
import com.google.devtools.mobileharness.infra.controller.test.model.JobExecutionUnit;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcDnsAddress;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcShardName;
import com.google.devtools.mobileharness.infra.lab.Annotations.GlobalEventBus;
import com.google.devtools.mobileharness.infra.lab.Annotations.LabGrpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.LabRpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaCloudRpc;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaStubby;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.infra.lab.controller.JobManager;
import com.google.devtools.mobileharness.infra.lab.controller.util.LabFileNotifier;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest.ContainerSetting;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest.ResolveFileItem;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestResponse.ContainerInfo;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.TestRunnerTiming;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.checker.ServiceSideVersionChecker;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Implementation of {@code PrepareTestService}. */
@Singleton
public class PrepareTestServiceImpl {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SATELLITE_LAB_TEST_ENGINE_RESOURCE_PATH =
      "/com/google/devtools/mobileharness/infra/container/testengine/SatelliteLabTestEngine_deploy.jar";

  private static final Duration WAIT_DEVICE_READY_TIMEOUT = Duration.ofSeconds(20L);

  private final LocalDeviceManager localDeviceManager;
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
  private final int labGrpcPort;
  private final boolean servViaCloudRpc;
  private final String cloudRpcDnsName;
  private final String cloudRpcShardName;
  private final EventBus globalInternalEventBus;
  private final FileResolver fileResolver;

  @Inject
  public PrepareTestServiceImpl(
      LocalDeviceManager localDeviceManager,
      JobManager jobManager,
      ProxyTestManager testManager,
      LocalFileUtil localFileUtil,
      NetUtil netUtil,
      SystemUtil systemUtil,
      FileResolver fileResolver,
      @ServViaStubby boolean servViaStubby,
      @LabRpcPort int labRpcPort,
      @LabGrpcPort int labGrpcPort,
      @ServViaCloudRpc boolean servViaCloudRpc,
      @CloudRpcDnsAddress String cloudRpcDnsName,
      @CloudRpcShardName String cloudRpcShardName,
      @GlobalEventBus EventBus globalInternalEventBus) {
    this.localDeviceManager = localDeviceManager;
    this.jobManager = jobManager;
    this.testManager = testManager;
    this.localFileUtil = localFileUtil;
    this.netUtil = netUtil;
    this.systemUtil = systemUtil;
    this.servViaStubby = servViaStubby;
    this.labRpcPort = labRpcPort;
    this.labGrpcPort = labGrpcPort;
    this.servViaCloudRpc = servViaCloudRpc;
    this.cloudRpcDnsName = cloudRpcDnsName;
    this.cloudRpcShardName = cloudRpcShardName;
    this.globalInternalEventBus = globalInternalEventBus;
    this.fileResolver = fileResolver;
  }

  @CanIgnoreReturnValue
  public CreateTestResponse createTest(CreateTestRequest req)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("CreateTestRequest [%s]", req);

    // Checks the client version.
    VersionCheckResponse versionCheckResponse =
        versionChecker.checkStub(req.getVersionCheckRequest());

    // Gets LocalDeviceRunner.
    List<LocalDeviceRunner> deviceRunners = getDeviceRunnersUntilReady(req.getDeviceIdList());

    // Checks the job feature.
    checkJobFeature(req.getJob().getJobFeature(), deviceRunners);

    // Creates JobExecutionUnit if absent.
    JobExecutionUnit jobExecutionUnit = createAndAddJobIfAbsent(req.getJob());

    // Creates TestExecutionUnit.
    TestExecutionUnit testExecutionUnit = createTestExecutionUnit(req.getTest(), jobExecutionUnit);

    ImmutableList<Device> devices =
        deviceRunners.stream().map(LocalDeviceRunner::getDevice).collect(toImmutableList());

    // Creates TestRunnerLauncher.
    TestRunnerLauncher<? super ProxyTestRunner> launcher =
        new LocalDeviceTestRunnerLauncher(
            deviceRunners.get(0), deviceRunners.stream().skip(1L).collect(toImmutableList()));

    // Creates Allocation.
    Allocation allocation =
        new Allocation(
            testExecutionUnit.locator(),
            req.getDeviceIdList().stream()
                .map(deviceId -> DeviceLocator.of(deviceId, LabLocator.LOCALHOST))
                .collect(toImmutableList()));

    List<String> decorators =
        req.getJob().getJobFeature().getDeviceRequirements().getDeviceRequirementList().stream()
            .flatMap(deviceRequirementList -> deviceRequirementList.getDecoratorList().stream())
            .collect(toImmutableList());

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
    proxyTestRunner = jobManager.addTestIfAbsent(proxyTestRunner);

    TestLocator testLocator =
        TestLocator.of(
            req.getTest().getTestId(),
            req.getTest().getTestName(),
            JobLocator.of(req.getJob().getJobId(), req.getJob().getJobName()));
    startResolveJobFiles(testLocator, jobExecutionUnit, req.getJob().getLabResolveFileList());

    // Starts TestRunner.
    try {
      testManager.startTest(proxyTestRunner);
    } catch (TestStartedException e) {
      logger.atSevere().withCause(e).log(
          "Skip duplicated CreateTest request for the allocation %s. "
              + "See b/38099373 for more detail.",
          allocation);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_PREPARE_TEST_TEST_RUNNER_START_ERROR,
          String.format("Failed to start test %s", testExecutionUnit.locator().id()),
          e);
    }

    // Waits until the test engine is ready if it does not need starting license.
    waitUntilTestEngineAndResolveJobFilesReady(
        testLocator, proxyTestRunner, req.getContainerSetting());

    // Creates the response.
    CreateTestResponse response =
        CreateTestResponse.newBuilder()
            .setVersionCheckResponse(versionCheckResponse)
            .addAllDeviceFeature(
                deviceRunners.stream()
                    .map(LocalDeviceRunner::getDevice)
                    .map(Device::toFeature)
                    .collect(toImmutableList()))
            .setContainerInfo(
                ContainerInfo.newBuilder()
                    .setIsContainerMode(proxyTestRunner.isContainerMode())
                    .setIsSandboxMode(proxyTestRunner.isSandboxMode())
                    .build())
            .setGetTestEngineStatusResponse(
                getGetTestEngineStatusResponse(
                    req.getJob().getJobId(), req.getTest().getTestId(), proxyTestRunner))
            .build();

    logger.atInfo().log("CreateTestResponse [%s]", shortDebugString(response));

    return response;
  }

  private void startResolveJobFiles(
      TestLocator testLocator, JobExecutionUnit jobExecutionUnit, List<ResolveFileItem> jobFiles)
      throws MobileHarnessException {
    String runFileDir = jobExecutionUnit.dirs().runFileDir();
    String tmpFileDir = jobExecutionUnit.dirs().tmpFileDir();

    ImmutableList<ResolveSource> resolveSources =
        jobFiles.stream()
            .map(
                jobFile ->
                    ResolveSource.create(
                        jobFile.getFile(),
                        jobFile.getTag(),
                        ImmutableMap.copyOf(jobFile.getResolvingParameterMap()),
                        runFileDir,
                        tmpFileDir))
            .collect(toImmutableList());
    jobManager.startResolveJobFiles(
        testLocator,
        resolveSources,
        source -> {
          ListenableFuture<Optional<ResolveResult>> resolveFileFuture =
              fileResolver.resolveAsync(source);
          return Futures.transformAsync(
              resolveFileFuture,
              resolveResult -> {
                if (resolveResult != null && resolveResult.isPresent()) {
                  return immediateFuture(resolveResult.get());
                } else {
                  throw new MobileHarnessException(
                      BasicErrorId.RESOLVE_FILE_INVALID_FILE_ERROR,
                      String.format(
                          "The file %s is not supported to resolve in lab.", source.path()));
                }
              },
              directExecutor());
        });
  }

  public GetTestEngineStatusResponse getTestEngineStatus(GetTestEngineStatusRequest req)
      throws MobileHarnessException {
    return getGetTestEngineStatusResponse(
        req.getJobId(), req.getTestId(), testManager.getProxyTestRunner(req.getTestId()));
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

  private void checkJobFeature(JobFeature jobFeature, List<LocalDeviceRunner> deviceRunners)
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

    for (LocalDeviceRunner deviceRunner : deviceRunners) {
      if (isJobSupported(deviceRunner.getDevice(), primaryDeviceJobType)) {
        return;
      }
    }

    throw new MobileHarnessException(
        InfraErrorId.LAB_RPC_PREPARE_TEST_JOB_TYPE_NOT_SUPPORTED,
        String.format("Job type [%s] is not supported by MH lab", primaryDeviceJobType));
  }

  /** Checks whether the job type is supported by this device. */
  private static boolean isJobSupported(Device device, JobType jobType) {
    if (!device.getDeviceTypes().contains(jobType.getDevice())) {
      logger.atWarning().log(
          "The device type [%s] is not supported by the device with ID %s",
          Sets.difference(ImmutableSet.of(jobType.getDevice()), device.getDeviceTypes()),
          device.getDeviceControlId());
      return false;
    }

    if (!device.getDriverTypes().contains(jobType.getDriver())) {
      logger.atWarning().log(
          "The driver [%s] is not supported by the device with ID %s",
          Sets.difference(ImmutableSet.of(jobType.getDriver()), device.getDriverTypes()),
          device.getDeviceControlId());
      return false;
    }

    if (!device.getDecoratorTypes().containsAll(jobType.getDecoratorList())) {
      logger.atWarning().log(
          "The decorators [%s] are not supported by the device with ID %s",
          Sets.difference(
              ImmutableSet.copyOf(jobType.getDecoratorList()), device.getDecoratorTypes()),
          device.getDeviceControlId());
      return false;
    }
    return true;
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
            /* genFileDir= */ PathUtil.join(DirUtil.getPublicGenDir(), jobId),
            /* tmpFileDir= */ PathUtil.join(DirUtil.getPrivateGenDir(), jobId),
            /* runFileDir= */ PathUtil.join(DirUtil.getRunDir(), jobId),
            /* remoteFileDir= */ null,
            /* hasTestSubdirs= */ true,
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

  private List<LocalDeviceRunner> getDeviceRunnersUntilReady(List<String> deviceIds)
      throws MobileHarnessException, InterruptedException {
    // Waits for the device to be ready, since the device maybe INIT in LabServer.
    Clock clock = Clock.systemUTC();
    Instant expireTime = clock.instant().plus(WAIT_DEVICE_READY_TIMEOUT);
    Sleeper sleeper = Sleeper.defaultSleeper();
    int attempts = 0;
    while (true) {
      try {
        return getDeviceRunners(deviceIds);
      } catch (MobileHarnessException e) {
        if (clock.instant().isAfter(expireTime)
            || (e.getErrorId() != InfraErrorId.LAB_RPC_PREPARE_TEST_DEVICE_NOT_ALIVE
                && e.getErrorId() != InfraErrorId.LAB_RPC_PREPARE_TEST_DEVICE_NOT_FOUND)) {
          throw e;
        }
        logger.atWarning().log(
            "%d failed attempts to get device runners of %s, try again later",
            ++attempts, deviceIds);
        sleeper.sleep(Duration.ofSeconds(1));
      }
    }
  }

  private List<LocalDeviceRunner> getDeviceRunners(List<String> deviceIds)
      throws MobileHarnessException {
    List<LocalDeviceRunner> deviceRunners = new ArrayList<>();
    for (String deviceId : deviceIds) {
      LocalDeviceRunner deviceRunner = localDeviceManager.getLocalDeviceRunner(deviceId);
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
    Optional<String> labHostIp = netUtil.getUniqueHostIpOrEmpty();
    if (servViaStubby) {
      StubbyLocator.Builder stubbyLocator =
          StubbyLocator.newBuilder().setHostName(labHostName).setPort(labRpcPort);
      labHostIp.ifPresent(stubbyLocator::setIp);
      builder.setEnableStubby(true).setStubbyLocator(stubbyLocator);
    }
    GrpcLocator.Builder grpcLocator =
        GrpcLocator.newBuilder()
            .setGrpcTarget(String.format("dns:///%s:%s", labHostName, labGrpcPort))
            .setHostName(labHostName)
            .setGrpcPort(labGrpcPort);
    labHostIp.ifPresent(grpcLocator::setHostIp);
    builder.setEnableGrpc(true).setGrpcLocator(grpcLocator);

    return builder.build();
  }

  private void waitUntilTestEngineAndResolveJobFilesReady(
      TestLocator testLocator, ProxyTestRunner proxyTestRunner, ContainerSetting containerSetting)
      throws MobileHarnessException {
    try {
      Clock clock = Clock.systemUTC();
      Duration testEngineSyncStartingTimeout =
          containerSetting.getNeedStartingLicense()
              ? Duration.ZERO
              : Duration.ofMillis(containerSetting.getSyncStartingTimeoutMs());
      logger.atInfo().log(
          "Wait [%s] until test engine  and resolving job files is ready",
          testEngineSyncStartingTimeout);

      Instant expireTime = clock.instant().plus(testEngineSyncStartingTimeout);
      boolean ready = proxyTestRunner.waitUntilTestEngineReady(testEngineSyncStartingTimeout);
      Instant now = clock.instant();
      if (ready && expireTime.isAfter(now)) {
        Optional<ListenableFuture<List<ResolveResult>>> resolveJobFilesFuture =
            jobManager.getResolveJobFilesFuture(testLocator.jobLocator().id(), testLocator.id());
        if (resolveJobFilesFuture.isPresent()) {
          try {
            resolveJobFilesFuture
                .get()
                .get(Duration.between(now, expireTime).toMillis(), MILLISECONDS);
          } catch (ExecutionException | TimeoutException e) {
            // Ignore the exception here. GetTestEngineStatus will process the exception.
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_PREPARE_TEST_INTERRUPTED,
          "Interrupted when waiting until test engine is ready",
          e);
    }
  }

  private GetTestEngineStatusResponse getGetTestEngineStatusResponse(
      String jobId, String testId, ProxyTestRunner proxyTestRunner) throws MobileHarnessException {
    TestEngineStatus testEngineStatus = proxyTestRunner.getTestEngineStatus();
    Optional<TestEngineLocator> testEngineLocator = proxyTestRunner.getTestEngineLocator();
    Optional<Throwable> testEngineErrorInResponse =
        proxyTestRunner.getTestEngineError().map(e -> e);

    Optional<ListenableFuture<List<ResolveResult>>> resolveJobFilesFuture =
        jobManager.getResolveJobFilesFuture(jobId, testId);
    TestEngineStatus resolveJobFilesStatus;
    if (resolveJobFilesFuture.isPresent()) {
      try {
        resolveJobFilesFuture.get().get(0, SECONDS);
        resolveJobFilesStatus = TestEngineStatus.READY;
      } catch (ExecutionException e) {
        resolveJobFilesStatus = TestEngineStatus.FAILED;
        if (testEngineErrorInResponse.isPresent()) {
          testEngineErrorInResponse.get().addSuppressed(e.getCause());
        } else {
          testEngineErrorInResponse = Optional.ofNullable(e.getCause());
        }
      } catch (TimeoutException e) {
        resolveJobFilesStatus = TestEngineStatus.STARTED;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new MobileHarnessException(
            InfraErrorId.LAB_RPC_PREPARE_TEST_INTERRUPTED,
            "Interrupted when waiting until test engine is ready",
            e);
      }
    } else {
      resolveJobFilesStatus = TestEngineStatus.NOT_STARTED;
    }

    GetTestEngineStatusResponse.Builder response =
        GetTestEngineStatusResponse.newBuilder()
            .setHasTestEngineLocator(testEngineLocator.isPresent())
            .setTestEngineStatus(
                testEngineStatus.equals(TestEngineStatus.READY)
                    ? resolveJobFilesStatus
                    : testEngineStatus);
    testEngineLocator.ifPresent(response::setTestEngineLocator);
    testEngineErrorInResponse
        .map(ErrorModelConverter::toExceptionDetail)
        .ifPresent(response::setError);
    return response.build();
  }
}
