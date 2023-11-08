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

package com.google.devtools.mobileharness.infra.lab.rpc.service.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.infra.lab.controller.FilePublisher;
import com.google.devtools.mobileharness.infra.lab.controller.ForwardingTestMessageBuffer;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.SubTestGenDataResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.SubTestStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.TestMessage;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Lab server response proto generator. Generates the responses based on client side request and
 * test information.
 */
public class LabResponseProtoGenerator {

  /** Logger for this service. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Public test generated file publisher. */
  private final FilePublisher filePublisher;

  private final LocalFileUtil fileUtil;

  @Inject
  public LabResponseProtoGenerator(FilePublisher filePublisher, LocalFileUtil fileUtil) {
    this.filePublisher = filePublisher;
    this.fileUtil = fileUtil;
  }

  /**
   * Iteratively populate {@link GetTestGenDataResponse} with testInfo of the root test and
   * sub-tests.
   */
  public GetTestGenDataResponse.Builder createGetTestGenDataResponse(
      TestInfo testInfo, boolean encodeFilePath, GetTestGenDataRequest req)
      throws MobileHarnessException, InterruptedException {
    String testId = testInfo.locator().getId();
    // Test properties.
    GetTestGenDataResponse.Builder builder =
        GetTestGenDataResponse.newBuilder()
            .addAllTestProperty(StrPairUtil.convertMapToList(testInfo.properties().getAll()));

    // Test warnings.
    builder.addAllTestWarning(
        testInfo.warnings().getAll().stream()
            .map(ErrorModelConverter::toExceptionDetailWithoutNamespace)
            .collect(toImmutableList()));

    // Generated files.
    if (testInfo.hasGenFileDir()) {
      String genFileDir = testInfo.getGenFileDir();
      fileUtil.grantFileOrDirFullAccessRecursively(genFileDir);
      for (String filePath : fileUtil.listFilePaths(genFileDir, true)) {
        String genFileRelatedPath = PathUtil.makeRelative(genFileDir, filePath);

        if (encodeFilePath) {
          genFileRelatedPath = filePublisher.encodeFilePath(genFileRelatedPath);
        }
        builder.addGenFileRelatedPath(genFileRelatedPath);
      }
      if (builder.getGenFileRelatedPathCount() > 0) {
        builder.setGenFileDir(genFileDir);
      }
      logger.atInfo().log(
          "Get gen data of test %s: %d properties, %d warnings, %d gen files",
          testId,
          builder.getTestPropertyCount(),
          builder.getTestWarningCount(),
          builder.getGenFileRelatedPathCount());
    }

    // Create {@link SubTestGenDataResponse} with requested sub-testInfo.
    // These sub-testInfo are already known by client.
    for (GetTestGenDataRequest subTestReq : req.getSubTestList()) {
      String subTestId = subTestReq.getTestId();
      TestInfo subTestInfo = testInfo.subTests().getById(subTestId);
      if (subTestInfo == null) {
        continue;
      }
      SubTestGenDataResponse subTestResp =
          SubTestGenDataResponse.newBuilder()
              .setGenData(
                  createGetTestGenDataResponse(subTestInfo, encodeFilePath, subTestReq).build())
              .setTestId(subTestId)
              .build();
      builder.addSubTest(subTestResp);
    }
    return builder;
  }

  /**
   * Iteratively populate {@link GetTestStatusResponse} based on testInfo and getTestStatusRequest.
   *
   * @param testInfo the testInfo of current test.
   * @param req the GetTestStatusRequest proto sent from client side.
   * @param forwardingTestMessageBuffer optional message buffer that contains test message to be
   *     added in the response proto.
   * @param forceCleanUpForDrainTimeout force clean up flag. Will force RUNNING test to go to DONE
   *     state.
   * @return the populated GetTestStatusResponse proto.
   */
  public GetTestStatusResponse.Builder createGetTestStatusResponse(
      TestInfo testInfo,
      @Nullable GetTestStatusRequest req,
      Optional<List<DeviceFeature>> deviceFeatures,
      Optional<ForwardingTestMessageBuffer> forwardingTestMessageBuffer,
      boolean forceCleanUpForDrainTimeout) {
    String testId = testInfo.locator().getId();
    TestStatus testStatus = testInfo.status().get();
    ResultTypeWithCause testResult = testInfo.result().toNewResult().get();
    List<TestMessageInfo> bufferedMessages = ImmutableList.of();
    if (forwardingTestMessageBuffer.isPresent()) {
      bufferedMessages = forwardingTestMessageBuffer.get().pollForwardingTestMessages(testId);
    }
    List<TestMessage> bufferedTestMessages =
        bufferedMessages.isEmpty()
            ? ImmutableList.of()
            : bufferedMessages.stream()
                .map(
                    message ->
                        TestMessage.newBuilder()
                            .putAllMessageContent(message.message())
                            .addAllSubTestIdChain(message.subTestIdChain())
                            .build())
                .collect(toImmutableList());
    logger.atFiner().log(
        "Test %s: status=%s, result=%s, buffered_message_size=%d",
        testId, testStatus.name(), testResult, bufferedMessages.size());

    // Return Error state for any running tests when force clean up is enabled to avoid throwing
    // InfraError from the client side in this case.
    if (forceCleanUpForDrainTimeout && testStatus == TestStatus.RUNNING) {
      logger.atWarning().log("Forcibly clean up test: %s.", testId);
      testStatus = TestStatus.DONE;
      testResult =
          testInfo
              .result()
              .toNewResult()
              .setNonPassing(
                  com.google.devtools.mobileharness.api.model.proto.Test.TestResult.ERROR,
                  new MobileHarnessException(
                      InfraErrorId.TR_TEST_DRAIN_TIMEOUT_AND_FORCE_CLEAN_UP,
                      "Test is interrupted and killed by drain timeout."))
              .get();
    }

    GetTestStatusResponse.Builder builder =
        GetTestStatusResponse.newBuilder()
            .setTestStatus(testStatus)
            .setTestResult(TestResult.valueOf(testResult.type().name()))
            .addAllTestMessage(bufferedTestMessages);
    // TODO: stop writing to deprecated_test_result_cause when client version >
    // 4.188.0.
    testResult.cause().ifPresent(builder::setDeprecatedTestResultCause);
    testResult.causeProto().ifPresent(builder::setTestResultCause);
    if (req != null) {
      // Received testLogOffset for this testInfo in {@link GetTestStatusRequest}.
      builder.setTestLog(testInfo.log().get(req.getTestLogOffset()));
    } else {
      // Not received testLogOffset for this testInfo in {@link GetTestStatusRequest}.
      // This is a new subTest that is not known by client, return all log from beginning.
      builder.setTestLog(testInfo.log().get(0));
    }

    // This assumes that each of the sub-tests of testInfo has a unique test ID.
    // To make sure the same testInfo won't be added again, if already requested by the client.
    Set<String> requestedTestId = new HashSet<>();

    if (req != null) {
      // create {@link SubTestStatusResponse} with requested sub-testInfo
      // These sub-testInfo are already known by client
      for (GetTestStatusRequest subReq : req.getSubTestList()) {
        String subTestId = subReq.getTestId();
        TestInfo subTestInfo = testInfo.subTests().getById(subTestId);
        if (subTestInfo == null) {
          continue;
        }
        requestedTestId.add(subTestId);
        SubTestStatusResponse subTestResp =
            SubTestStatusResponse.newBuilder()
                .setStatus(
                    createGetTestStatusResponse(
                            subTestInfo,
                            subReq,
                            /* deviceFeatures= */ Optional.empty(),
                            forwardingTestMessageBuffer,
                            forceCleanUpForDrainTimeout)
                        .build())
                .setTestId(subTestInfo.locator().getId())
                .setTestName(subTestInfo.locator().getName())
                .build();
        builder.addSubTest(subTestResp);
      }
    }

    // Create {@link SubTestStatusResponse} with new sub-testInfo.
    // These sub-testInfo are unknown to client (created after last sync).
    for (TestInfo newSubTestInfo : testInfo.subTests().getAll().values()) {
      if (!requestedTestId.contains(newSubTestInfo.locator().getId())) {
        SubTestStatusResponse subTestResp =
            SubTestStatusResponse.newBuilder()
                .setStatus(
                    createGetTestStatusResponse(
                            newSubTestInfo,
                            null,
                            /* deviceFeatures= */ Optional.empty(),
                            forwardingTestMessageBuffer,
                            forceCleanUpForDrainTimeout)
                        .build())
                .setTestId(newSubTestInfo.locator().getId())
                .setTestName(newSubTestInfo.locator().getName())
                .build();
        builder.addSubTest(subTestResp);
      }
    }

    // Only add device features when the test is done.
    if (testStatus == TestStatus.DONE && deviceFeatures.isPresent()) {
      builder.addAllDeviceFeature(deviceFeatures.get());
    }

    return builder;
  }
}
