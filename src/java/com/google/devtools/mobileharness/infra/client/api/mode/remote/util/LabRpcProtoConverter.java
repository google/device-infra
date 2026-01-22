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

package com.google.devtools.mobileharness.infra.client.api.mode.remote.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.trace.proto.SpanProto.ParentSpan;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.devtools.mobileharness.shared.util.time.TimeoutUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestRequest.Job;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestRequest.Test;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.SubTestStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.TestMessage;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWalker;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Parses Lab side responses, including test status protos and test gen data protos. */
public class LabRpcProtoConverter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  public KickOffTestRequest generateKickOffTestRequestFrom(
      TestInfo testInfo,
      List<DeviceLocator> deviceLocators,
      Set<String> labResolveFiles,
      ParentSpan parentSpan)
      throws MobileHarnessException, InterruptedException {
    String testId = testInfo.locator().getId();
    JobInfo jobInfo = testInfo.jobInfo();
    JobSpecWalker.Visitor escapePathVisitor = new EscapePathVisitor(labResolveFiles);
    JobSpec jobSpec = JobSpecWalker.resolve(jobInfo.protoSpec().getProto(), escapePathVisitor);
    ScopedSpecs scopedSpecs = jobInfo.scopedSpecs();
    JobSpec scopedJobSpec = scopedSpecs.toJobSpec(JobSpecHelper.getDefaultHelper());
    JobSpec resolvedScopedJobSpec = JobSpecWalker.resolve(scopedJobSpec, escapePathVisitor);
    ScopedSpecs resolvedScopedSpecs = new ScopedSpecs(null);
    resolvedScopedSpecs.addAll(resolvedScopedJobSpec);

    return KickOffTestRequest.newBuilder()
        .addAllDeviceId(
            deviceLocators.stream().map(DeviceLocator::getSerial).collect(toImmutableList()))
        .setClientVersion(Version.CLIENT_VERSION.toString())
        .setJob(
            Job.newBuilder()
                .setJobId(jobInfo.locator().getId())
                .setJobName(jobInfo.locator().getName())
                .setJobFeature(jobInfo.toFeature())
                .addAllJobParam(StrPairUtil.convertMapToList(jobInfo.params().getAll()))
                .setTimeout(getTestTimeout(jobInfo.setting().getNewTimeout()))
                .setJobSpec(jobSpec)
                .setJobScopedSpecsJson(resolvedScopedSpecs.toJsonString())
                .setJobCreateTimeMs(jobInfo.timing().getCreateTime().toEpochMilli())
                .setJobStartTimeMs(jobInfo.timing().getStartTimeNonNull().toEpochMilli())
                .addAllDeviceScopedSpecsJson(
                    jobInfo.subDeviceSpecs().getAllSubDevices().stream()
                        .map(s -> s.scopedSpecs().toJsonString())
                        .collect(toImmutableList()))
                .addAllJobProperty(StrPairUtil.convertMapToList(jobInfo.properties().getAll())))
        .setTest(
            Test.newBuilder()
                .setTestId(testId)
                .setTestName(testInfo.locator().getName())
                .setTestCreateTimeMs(testInfo.timing().getCreateTime().toEpochMilli())
                .setTestStartTimeMs(testInfo.timing().getStartTimeNonNull().toEpochMilli())
                .addAllTestProperty(StrPairUtil.convertMapToList(testInfo.properties().getAll())))
        .setParentSpan(parentSpan)
        .build();
  }

  /** A visitor that escapes all paths in the job spec. */
  private class EscapePathVisitor extends JobSpecWalker.Visitor {

    private final Set<String> labResolveFiles;

    EscapePathVisitor(Set<String> labResolveFiles) {
      this.labResolveFiles = labResolveFiles;
    }

    @Override
    public void visitPrimitiveFileField(Message.Builder builder, FieldDescriptor field) {
      if (field.getType() != FieldDescriptor.Type.STRING) {
        return;
      }

      if (field.isRepeated()) {
        List<String> paths = new ArrayList<>();

        int size = builder.getRepeatedFieldCount(field);
        for (int i = 0; i < size; i++) {
          String escapedPath = getEscapedPath((String) builder.getRepeatedField(field, i));
          paths.add(escapedPath);
        }
        builder.setField(field, paths);
      } else {
        String escapedPath = getEscapedPath((String) builder.getField(field));
        builder.setField(field, escapedPath);
      }
    }

    private String getEscapedPath(String path) {
      if (localFileUtil.isLocalFileOrDir(path)) {
        return path;
      }
      if (labResolveFiles.contains(path)) {
        return path;
      }
      return localFileUtil.escapeFilePath(path.replace("::", "/"));
    }
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

  /** Recursively update root testInfo and all its sub-tests */
  public void updateTestStatus(
      GetTestStatusResponse resp,
      TestInfo testInfo,
      Map<String, Integer> remoteLogOffset,
      @Nullable TestMessageManager testMessageManager,
      @Nullable TestMessagePoster testMessagePoster)
      throws MobileHarnessException {
    String testId = testInfo.locator().getId();
    String remoteLog = resp.getTestLog();
    testInfo.log().append(remoteLog);
    remoteLogOffset.put(testId, remoteLogOffset.getOrDefault(testId, 0) + remoteLog.length());

    testInfo.status().set(resp.getTestStatus());
    setTestResultByGetTestStatusResponse(resp, testInfo, testInfo.resultWithCause());

    // Update sub-testInfo when sub-tests are available.
    Set<String> leftOverSubTestIds =
        testInfo.subTests().getAll().values().stream()
            .map(t -> t.locator().getId())
            .collect(toCollection(LinkedHashSet::new));
    for (SubTestStatusResponse subTestResp : resp.getSubTestList()) {
      String subTestId = subTestResp.getTestId();
      TestInfo subTestInfo = testInfo.subTests().getById(subTestId);
      if (subTestInfo == null) {
        // This is a new sub-test created after last sync at the lab server side.
        subTestInfo = testInfo.subTests().add(subTestId, subTestResp.getTestName());
      } else {
        leftOverSubTestIds.remove(subTestId);
      }
      updateTestStatus(
          subTestResp.getStatus(),
          subTestInfo,
          remoteLogOffset,
          testMessageManager,
          testMessagePoster);
    }
    for (String subTestId : leftOverSubTestIds) {
      testInfo.subTests().remove(subTestId);
      testInfo.log().atInfo().alsoTo(logger).log("The sub test %s has been removed.", subTestId);
    }

    if (testMessageManager != null && testMessagePoster != null) {
      // Forwards test messages to the client side.
      // This part needs to be after adding new sub tests.
      // This part only works for top-level test because sub test resp do not have test message
      // list.
      for (TestMessage testMessage : resp.getTestMessageList()) {
        // Here test messages of the sub-tests will be forwarded and logged with root test ID.
        List<String> subTestIdChain =
            testMessage.getSubTestIdChainList().isEmpty()
                ? ImmutableList.of(testId)
                : testMessage.getSubTestIdChainList();
        TestMessageInfo testMessageInfo =
            TestMessageInfo.of(
                testId, testMessage.getMessageContentMap(), subTestIdChain, /* isRemote= */ true);
        logger.atFine().log("Forward test message to client: %s", testMessageInfo);
        // TODO: Uses a thread pool to asynchronously forward messages.
        testMessageManager.sendMessageToTest(testMessagePoster, testMessageInfo);
      }
    }
  }

  private static void setTestResultByGetTestStatusResponse(
      GetTestStatusResponse resp, TestInfo testInfo, Result testResult) {
    TestResult resultType;
    if (resp.hasTestResultType()) {
      resultType = resp.getTestResultType();
    } else {
      resultType = TestResult.valueOf(resp.getTestResult().name());
    }
    ExceptionProto.ExceptionDetail resultCause =
        resp.hasTestResultCause() ? resp.getTestResultCause() : null;

    setTestResultByResultCause(resultType, resultCause, testInfo, testResult);
  }

  private static void setTestResultByResultCause(
      TestResult resultType,
      ExceptionProto.ExceptionDetail resultCause,
      TestInfo testInfo,
      Result testResult) {

    switch (resultType) {
      case PASS:
        testResult.setPass();
        if (resultCause != null) {
          logger.atWarning().log(
              "GetTestStatusResponse has PASS test result with test result cause [%s]",
              resultCause);
        }
        break;
      case UNKNOWN:
        logger.atFine().log("Ignore UNKNOWN test result from lab side");
        break;
      default:
        if (resultCause == null) {
          testResult.setNonPassing(
              resultType,
              new MobileHarnessException(
                  InfraErrorId.LAB_NON_PASSING_TEST_RESULT_WITHOUT_CAUSE,
                  String.format(
                      "Test result [%s] from lab side without result cause", resultType)));
        } else {
          testResult.setNonPassing(resultType, resultCause);
        }
    }
  }

  public void updateTestInfoFromTestGenData(GetTestGenDataResponse resp, TestInfo testInfo) {
    String testId = testInfo.locator().getId();
    String subTestLogPostfix =
        testInfo.isRootTest()
            ? ""
            : String.format(" for sub_test %s(%s)", testInfo.locator().getName(), testId);
    // Gets the test generated properties.
    if (resp.getTestPropertyCount() == 0) {
      testInfo
          .getRootTest()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("No generated property%s", subTestLogPostfix);
    } else {
      List<StrPair> properties = new ArrayList<>(resp.getTestPropertyList());
      StrPairUtil.sort(properties);
      StringBuilder buf =
          new StringBuilder("Generated properties").append(subTestLogPostfix).append(':');
      for (StrPair property : properties) {
        testInfo.properties().add(property.getName(), property.getValue());
        buf.append("\n- ");
        buf.append(property.getName());
        buf.append(" = ");
        buf.append(property.getValue());
      }
      testInfo.getRootTest().log().atInfo().alsoTo(logger).log("%s", buf.toString());
    }

    // Copies the lab server side error.
    if (resp.getTestWarningExceptionDetailCount() > 0) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Lab Server side test warning count: %d", resp.getTestWarningExceptionDetailCount());
      resp.getTestWarningExceptionDetailList()
          .forEach(
              exceptionDetail -> {
                String errorMessage = exceptionDetail.getSummary().getMessage();
                testInfo
                    .warnings()
                    .add(
                        exceptionDetail.toBuilder()
                            .setSummary(
                                exceptionDetail.getSummary().toBuilder()
                                    .setMessage("(L)" + errorMessage)
                                    .build())
                            .build());
              });
    } else {
      testInfo
          .getRootTest()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("No test warnings on Lab Server side%s", subTestLogPostfix);
    }
  }
}
