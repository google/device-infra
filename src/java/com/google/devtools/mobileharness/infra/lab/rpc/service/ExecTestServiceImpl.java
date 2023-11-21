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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.infra.controller.device.DeviceHelperFactory;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner.EventScope;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.exception.TestRunnerLauncherConnectedException;
import com.google.devtools.mobileharness.infra.lab.common.env.UtrsEnvironments;
import com.google.devtools.mobileharness.infra.lab.controller.FilePublisher;
import com.google.devtools.mobileharness.infra.lab.controller.ForwardingTestMessageBuffer;
import com.google.devtools.mobileharness.infra.lab.controller.LabDirectTestRunnerHolder;
import com.google.devtools.mobileharness.infra.lab.controller.LabLocalDirectTestRunner;
import com.google.devtools.mobileharness.infra.lab.controller.util.LabFileNotifier;
import com.google.devtools.mobileharness.infra.lab.rpc.service.util.LabResponseProtoGenerator;
import com.google.devtools.mobileharness.infra.lab.rpc.service.util.TestInfoCreator;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.assistedinject.Assisted;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.ForwardTestMessageRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.ForwardTestMessageResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestDetailRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestDetailResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestResponse;
import com.google.wireless.qa.mobileharness.lab.proto.Stat;
import com.google.wireless.qa.mobileharness.lab.proto.Stat.Test;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.job.JobTypeUtil;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** The service logic class for Mobile Harness Client to run tests on Lab Server. */
public class ExecTestServiceImpl {

  /** Factory for creating ExecTestServiceImpl in UTRS. */
  public interface ExecTestServiceImplFactory {
    ExecTestServiceImpl create(ListeningExecutorService threadPool);
  }

  /** Logger for this service. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabDirectTestRunnerHolder testRunnerHolder;

  private final DeviceHelperFactory deviceHelperFactory;

  private final LabResponseProtoGenerator labResponseProtoGenerator;

  /** The test message manager. */
  private final TestMessageManager testMessageManager;

  /** The forwarding test message buffer. */
  private final ForwardingTestMessageBuffer forwardingTestMessageBuffer;

  private final ListeningExecutorService threadPool;

  private final TestInfoCreator testInfoCreator;

  @Nullable private final EventBus globalInternalEventBus;

  private volatile boolean forceCleanUpForDrainTimeout = false;

  /* Constructor for UTRS injection */
  @Inject
  ExecTestServiceImpl(
      LabDirectTestRunnerHolder testRunnerHolder,
      DeviceHelperFactory deviceHelperFactory,
      @Assisted ListeningExecutorService threadPool) {
    this(
        testRunnerHolder,
        deviceHelperFactory,
        new LabResponseProtoGenerator(new FilePublisher(), new LocalFileUtil()),
        threadPool,
        null,
        TestMessageManager.getInstance(),
        new ForwardingTestMessageBuffer(testRunnerHolder),
        new TestInfoCreator(testRunnerHolder, new LocalFileUtil()));
  }

  public ExecTestServiceImpl(
      LabDirectTestRunnerHolder testRunnerHolder,
      DeviceHelperFactory deviceHelperFactory,
      ListeningExecutorService threadPool,
      @Nullable EventBus globalInternalEventBus) {
    this(
        testRunnerHolder,
        deviceHelperFactory,
        new LabResponseProtoGenerator(new FilePublisher(), new LocalFileUtil()),
        threadPool,
        globalInternalEventBus,
        TestMessageManager.getInstance(),
        new ForwardingTestMessageBuffer(testRunnerHolder),
        new TestInfoCreator(testRunnerHolder, new LocalFileUtil()));
  }

  @VisibleForTesting
  ExecTestServiceImpl(
      LabDirectTestRunnerHolder testRunnerHolder,
      DeviceHelperFactory deviceHelperFactory,
      LabResponseProtoGenerator labResponseProtoGenerator,
      ListeningExecutorService threadPool,
      @Nullable EventBus globalInternalEventBus,
      TestMessageManager testMessageManager,
      ForwardingTestMessageBuffer forwardingTestMessageBuffer,
      TestInfoCreator testInfoCreator) {
    this.testRunnerHolder = testRunnerHolder;
    this.deviceHelperFactory = deviceHelperFactory;
    this.labResponseProtoGenerator = labResponseProtoGenerator;
    this.threadPool = threadPool;
    this.globalInternalEventBus = globalInternalEventBus;
    this.testMessageManager = testMessageManager;
    this.forwardingTestMessageBuffer = forwardingTestMessageBuffer;
    this.testInfoCreator = testInfoCreator;
  }

  @CanIgnoreReturnValue
  public KickOffTestResponse kickOffTest(KickOffTestRequest req) throws MobileHarnessException {
    logger.atInfo().log("KickOffTestRequest: %s", req);

    // Creates TestInfo.
    TestInfo testInfo;
    try {
      testInfo = testInfoCreator.create(req);
      testInfo
          .properties()
          .add(PropertyName.Test._IS_RUN_IN_DM, String.valueOf(UtrsEnvironments.isRunInDM()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_EXEC_TEST_KICK_OFF_TEST_INTERRUPTED,
          "Interrupted when creating test info",
          e);
    }

    // Gets DeviceHelpers.
    List<Device> devices = new ArrayList<>();
    for (String deviceId : req.getDeviceIdList()) {
      devices.add(deviceHelperFactory.getDeviceHelper(deviceId));
    }
    List<String> deviceIds = devices.stream().map(Device::getDeviceId).collect(Collectors.toList());

    // Creates Allocation.
    Allocation allocation =
        new Allocation(
            testInfo.locator(),
            // TODO: Use the correct way to create DeviceLocator after b/37969936
            // fixed.
            deviceIds.stream().map(DeviceLocator::new).collect(Collectors.toList()),
            devices.stream()
                .map(Device::getDimensions)
                .map(StrPairUtil::convertCollectionToMultimap)
                .collect(Collectors.toList()));

    // Creates DirectTestRunner.
    TestRunnerLauncher<? super DirectTestRunner> connectorTestRunnerLauncher =
        testRunnerHolder.createTestRunnerLauncher(testInfo.locator().getId());
    LabFileNotifier labFileNotifier =
        testRunnerHolder.createLabFileNotifier(testInfo.locator().getId());
    DirectTestRunnerSetting setting =
        DirectTestRunnerSetting.create(
            testInfo,
            allocation,
            globalInternalEventBus,
            /* internalPluginSubscribers= */ null,
            /* apiPluginSubscribers= */ null,
            /* jarPluginSubscribers= */ null);
    DirectTestRunner testRunner;
    try {
      testRunner =
          new LabLocalDirectTestRunner(
              connectorTestRunnerLauncher, setting, devices, threadPool, labFileNotifier);
    } catch (TestRunnerLauncherConnectedException e) {
      logger.atSevere().log(
          "Skipped the duplicated kickOffTest request for the running allocation %s. "
              + "See b/38099373 for more detail.",
          allocation);
      return KickOffTestResponse.getDefaultInstance();
    }
    testRunner.registerTestEventSubscriber(forwardingTestMessageBuffer, EventScope.TEST_MESSAGE);

    // Starts DirectTestRunner.
    testRunner.start();
    logger.atInfo().log("Start test %s with device %s", testInfo.locator().getId(), deviceIds);
    return KickOffTestResponse.getDefaultInstance();
  }

  public GetTestStatusResponse getTestStatus(GetTestStatusRequest req)
      throws MobileHarnessException {
    // Get root testInfo.
    TestInfo testInfo = testRunnerHolder.getTestInfo(req.getTestId());
    // Get device features.
    Optional<List<DeviceFeature>> deviceFeatures =
        testRunnerHolder.getDeviceFeatures(req.getTestId());
    // Create {@link GetTestStatusResponse} with root testInfo.
    GetTestStatusResponse.Builder builder =
        createGetTestStatusResponse(testInfo, req, deviceFeatures);
    return builder.build();
  }

  /**
   * Gets test detail.
   *
   * @since MH lab server 4.43
   */
  public GetTestDetailResponse getTestDetail(GetTestDetailRequest req)
      throws MobileHarnessException {
    TestInfo testInfo = testRunnerHolder.getTestInfo(req.getTestId());
    return GetTestDetailResponse.newBuilder()
        .setTestDetail(convertTestInfoToTestDetail(testInfo))
        .build();
  }

  @CanIgnoreReturnValue
  public ForwardTestMessageResponse forwardTestMessage(ForwardTestMessageRequest req)
      throws MobileHarnessException {
    String rootTestId = req.getTestId();
    List<String> subTestIdChain =
        req.getTestMessage().getSubTestIdChainList().isEmpty()
            ? ImmutableList.of(rootTestId)
            : req.getTestMessage().getSubTestIdChainList();
    TestMessageInfo testMessageInfo =
        TestMessageInfo.of(
            req.getTestId(),
            req.getTestMessage().getMessageContentMap(),
            subTestIdChain,
            req.getIsRemoteMessage());
    logger.atFine().log("Forward test message to lab: %s", testMessageInfo);
    testMessageManager.sendMessageToTest(testMessageInfo);
    return ForwardTestMessageResponse.getDefaultInstance();
  }

  public GetTestGenDataResponse getTestGenData(GetTestGenDataRequest req, boolean encodeFilePath)
      throws MobileHarnessException {
    TestInfo testInfo = testRunnerHolder.getTestInfo(req.getTestId());
    try {
      return labResponseProtoGenerator
          .createGetTestGenDataResponse(testInfo, encodeFilePath, req)
          .build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_EXEC_TEST_GET_TEST_GEN_DATA_INTERRUPTED, "Interrupted", e);
    }
  }

  public void enableForceCleanUpForDrainTimeout() {
    forceCleanUpForDrainTimeout = true;
  }

  /**
   * Iteratively populate {@link GetTestStatusResponse} with testInfo of root test and all of its
   * sub-tests.
   *
   * @param deviceFeatures is Optional for the main test while it is null for subtests.
   */
  GetTestStatusResponse.Builder createGetTestStatusResponse(
      TestInfo testInfo,
      @Nullable GetTestStatusRequest req,
      Optional<List<DeviceFeature>> deviceFeatures) {
    return labResponseProtoGenerator.createGetTestStatusResponse(
        testInfo,
        req,
        deviceFeatures,
        Optional.of(forwardingTestMessageBuffer),
        forceCleanUpForDrainTimeout);
  }

  /**
   * Converts the internal {@link TestInfo} object to proto buffer version with the detailed test
   * stats.
   */
  private Test convertTestInfoToTestDetail(TestInfo testInfo) {
    ResultTypeWithCause testResult = testInfo.result().toNewResult().get();
    Test.Builder testProto =
        Test.newBuilder()
            .setId(testInfo.locator().getId())
            .setName(testInfo.locator().getName())
            .setJob(convertJobInfo(testInfo.jobInfo()))
            .setStatus(testInfo.status().get())
            .setResult(TestResult.valueOf(testResult.type().name()))
            .setCreateTime(testInfo.timing().getCreateTime().toEpochMilli())
            .setModifyTime(testInfo.timing().getModifyTime().toEpochMilli());
    // TODO: stop writing to deprecated_test_result_cause when client version >
    // 4.188.0.
    testResult.cause().ifPresent(testProto::setDeprecatedResultCause);
    testResult.causeProto().ifPresent(testProto::setResultCause);
    testProto.setLog(testInfo.log().get(0));
    testProto.addAllError(testInfo.errors().getAll());
    testProto.addAllProperty(StrPairUtil.convertMapToList(testInfo.properties().getAll()));
    return testProto.build();
  }

  /** Converts the internal {@link JobInfo} object to proto buffer version. */
  private Stat.Job convertJobInfo(JobInfo jobInfo) {
    Stat.Job.Builder jobProto = Stat.Job.newBuilder();
    jobProto.setId(jobInfo.locator().getId());
    jobProto.setName(jobInfo.locator().getName());
    jobProto.setUser(jobInfo.jobUser().getRunAs());
    jobProto.setType(JobTypeUtil.toString(jobInfo.type()));
    jobProto.setTestTimeoutMs(jobInfo.setting().getTimeout().getTestTimeoutMs());
    jobProto.addAllParam(StrPairUtil.convertMapToList(jobInfo.params().getAll()));
    return jobProto.build();
  }
}
