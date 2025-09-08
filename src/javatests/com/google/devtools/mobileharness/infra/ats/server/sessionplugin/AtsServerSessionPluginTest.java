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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.in.Decorators;
import com.google.devtools.mobileharness.api.model.job.in.Dimensions;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.common.XtsTypeLoader;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.AtsServerSessionNotification;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CancelSession;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.ErrorReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestContext;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionNotificationEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionNotification;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.LocalSessionStub;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.DeviceRequirement;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import com.google.protobuf.TextFormat;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsServerSessionPluginTest {
  private static final String ANDROID_XTS_ZIP = "file:///path/to/xts/zip/file";
  private static final String OUTPUT_FILE_UPLOAD_URL = "file:///path/to/output";

  private NewMultiCommandRequest request = NewMultiCommandRequest.getDefaultInstance();
  private CommandInfo commandInfo = CommandInfo.getDefaultInstance();
  private Timing timing;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionInfo sessionInfo;
  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private SessionResultHandlerUtil sessionResultHandlerUtil;
  @Bind @Mock private XtsJobCreator xtsJobCreator;
  @Bind @Mock private CommandExecutor commandExecutor;
  @Bind @Mock private Clock clock;
  @Bind @Mock private XtsTypeLoader xtsTypeLoader;
  @Bind @Mock private LocalSessionStub localSessionStub;
  @Bind @Mock private TestMessageUtil testMessageUtil;
  @Bind @Mock private Sleeper sleeper;
  @Bind @Spy private LocalFileUtil localFileUtil = new LocalFileUtil();

  @Mock private JobInfo jobInfo;
  @Mock private JobInfo jobInfo2;
  @Mock private JobInfo moblyJobInfo;
  @Mock private JobInfo moblyJobInfo2;
  @Mock private Files files;
  private Properties properties;
  @Mock private TestInfo testInfo;
  @Mock private TestInfos testInfos;
  @Mock private TestLocator testLocator;
  @Mock private SubDeviceSpecs subDeviceSpecs;
  private Properties testProperties;

  @Captor private ArgumentCaptor<UnaryOperator<RequestDetail>> unaryOperatorCaptor;
  @Inject private AtsServerSessionPlugin plugin;

  @Before
  public void setup() throws Exception {
    String publicDir = tmpFolder.newFolder("public_dir").getAbsolutePath();
    flags.setAllFlags(ImmutableMap.of("public_dir", publicDir));
    Instant baseTime = Instant.ofEpochMilli(1000);
    timing = new Timing(baseTime);
    timing.start(baseTime.plusMillis(1));
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionInfo.getSessionId()).thenReturn("session_id");
    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    properties = new Properties(timing);
    when(jobInfo.properties()).thenReturn(properties);
    when(jobInfo.type()).thenReturn(JobType.newBuilder().setDriver("XtsTradefedTest").build());
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.RUNNING));
    when(jobInfo.resultWithCause())
        .thenReturn(new Result(timing.toNewTiming(), new Params(timing).toNewParams()));
    when(jobInfo2.locator()).thenReturn(new JobLocator("job_id2", "job_name2"));
    when(jobInfo2.properties()).thenReturn(properties);
    when(jobInfo2.type()).thenReturn(JobType.newBuilder().setDriver("XtsTradefedTest").build());
    when(jobInfo2.status()).thenReturn(new Status(timing).set(TestStatus.RUNNING));
    when(jobInfo2.resultWithCause())
        .thenReturn(new Result(timing.toNewTiming(), new Params(timing).toNewParams()));
    when(moblyJobInfo.locator()).thenReturn(new JobLocator("mobly_job_id", "mobly_job_name"));
    when(moblyJobInfo.properties()).thenReturn(properties);
    when(moblyJobInfo.type()).thenReturn(JobType.newBuilder().setDriver("MoblyTest").build());
    when(moblyJobInfo.status()).thenReturn(new Status(timing).set(TestStatus.RUNNING));
    when(moblyJobInfo.resultWithCause())
        .thenReturn(new Result(timing.toNewTiming(), new Params(timing).toNewParams()));
    when(moblyJobInfo2.locator()).thenReturn(new JobLocator("mobly_job_id2", "mobly_job_name2"));
    when(moblyJobInfo2.properties()).thenReturn(properties);
    when(moblyJobInfo2.type()).thenReturn(JobType.newBuilder().setDriver("MoblyTest").build());
    when(moblyJobInfo2.status()).thenReturn(new Status(timing).set(TestStatus.RUNNING));
    when(moblyJobInfo2.resultWithCause())
        .thenReturn(new Result(timing.toNewTiming(), new Params(timing).toNewParams()));
    when(xtsJobCreator.createXtsTradefedTestJob(any()))
        .thenReturn(ImmutableList.of(jobInfo))
        .thenReturn(ImmutableList.of(jobInfo2));
    when(xtsJobCreator.createXtsNonTradefedJobs(any()))
        .thenReturn(ImmutableList.of(moblyJobInfo))
        .thenReturn(ImmutableList.of(moblyJobInfo2));
    when(jobInfo.files()).thenReturn(files);
    when(jobInfo2.files()).thenReturn(files);
    when(moblyJobInfo.files()).thenReturn(files);
    when(moblyJobInfo2.files()).thenReturn(files);
    when(files.getAll()).thenReturn(ImmutableMultimap.of());
    when(sessionRequestHandlerUtil.addNonTradefedModuleInfo(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    Mockito.doNothing().when(localFileUtil).mergeDir(any(Path.class), any(Path.class));
    commandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("cts -m module1 --logcat-on-failure")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("device_id_1")
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("device_id_2")
                    .build())
            .build();
    request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(commandInfo)
            .setTestEnvironment(
                TestEnvironment.newBuilder()
                    .setRetryCommandLine("retry --retry 0")
                    .setOutputFileUploadUrl(OUTPUT_FILE_UPLOAD_URL)
                    .build())
            .setMaxRetryOnTestFailures(3)
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());
    LinkedListMultimap<String, TestInfo> testInfosMap = LinkedListMultimap.create();
    testInfosMap.put("test_name", testInfo);
    when(testInfos.getAll()).thenReturn(testInfosMap);

    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.locator()).thenReturn(testLocator);
    when(testLocator.getId()).thenReturn("test_id");
    testProperties = new Properties(timing);
    SubDeviceSpec subDeviceSpec1 =
        SubDeviceSpec.createForTesting(
            DeviceRequirement.create(
                "AndroidRealDevice", new Decorators(), new Dimensions().add("uuid", "device_id_1")),
            new ScopedSpecs(new Timing()),
            new Timing());
    SubDeviceSpec subDeviceSpec2 =
        SubDeviceSpec.createForTesting(
            DeviceRequirement.create(
                "AndroidRealDevice", new Decorators(), new Dimensions().add("uuid", "device_id_2")),
            new ScopedSpecs(new Timing()),
            new Timing());
    when(subDeviceSpecs.getAllSubDevices())
        .thenReturn(ImmutableList.of(subDeviceSpec1, subDeviceSpec2));
    when(jobInfo.subDeviceSpecs()).thenReturn(subDeviceSpecs);
    when(jobInfo2.subDeviceSpecs()).thenReturn(subDeviceSpecs);
    testProperties.add(XtsConstants.TRADEFED_TESTS_PASSED, "10");
    testProperties.add(XtsConstants.TRADEFED_TESTS_FAILED, "10");
    when(testInfo.properties()).thenReturn(testProperties);
    when(jobInfo.tests()).thenReturn(testInfos);
    when(jobInfo2.tests()).thenReturn(testInfos);

    when(testInfo.timing()).thenReturn(timing);
    var unused = timing.end(baseTime.plusMillis(2));
    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(testInfo.resultWithCause()).thenReturn(result);
    when(clock.instant()).thenReturn(baseTime);
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    when(xtsTypeLoader.getXtsType(any(), any())).thenReturn("cts");
    when(commandExecutor.run(any())).thenReturn("command output.");
    when(sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(any()))
        .thenReturn(
            ImmutableList.of(
                com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec
                    .getDefaultInstance(),
                com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec
                    .getDefaultInstance()));
    when(sessionRequestHandlerUtil.getHostIp(any())).thenReturn("127.0.0.1");
  }

  @Test
  public void onSessionStarting_success() throws Exception {
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(1000L))
        .thenReturn(Instant.ofEpochMilli(2000L))
        .thenReturn(Instant.ofEpochMilli(3000L));
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    assertThat(requestDetail.getState()).isEqualTo(RequestState.RUNNING);
    assertThat(requestDetail.getCommandInfosList()).containsExactly(commandInfo);
    assertThat(requestDetail.getCreateTime()).isEqualTo(Timestamps.fromMillis(1000L));
    assertThat(requestDetail.getStartTime()).isEqualTo(Timestamps.fromMillis(2000L));
    assertThat(requestDetail.getUpdateTime()).isEqualTo(Timestamps.fromMillis(3000L));
    assertThat(requestDetail.getMaxRetryOnTestFailures())
        .isEqualTo(request.getMaxRetryOnTestFailures());
    assertThat(
            requestDetail.getCommandDetailsMap().values().iterator().next().getDeviceSerialsList())
        .containsExactly("device_id_1", "device_id_2");
  }

  @Test
  public void onSessionStarting_addDummyCommandDetail() throws Exception {
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(1000L))
        .thenReturn(Instant.ofEpochMilli(2000L))
        .thenReturn(Instant.ofEpochMilli(3000L))
        .thenReturn(Instant.ofEpochMilli(4000L))
        .thenReturn(Instant.ofEpochMilli(5000L));
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));

    // Verify dummy command detail
    RequestDetail requestDetail = unaryOperatorCaptor.getAllValues().get(0).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail dummyCommandDetail =
        requestDetail.getCommandDetailsMap().values().iterator().next();
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(dummyCommandDetail.getId()).isEqualTo(commandId);
    assertThat(dummyCommandDetail.getRequestId()).isEqualTo("session_id");
    assertThat(dummyCommandDetail.getState()).isEqualTo(CommandState.RUNNING);
    assertThat(dummyCommandDetail.getStartTime()).isEqualTo(Timestamps.fromMillis(3000L));
    assertThat(dummyCommandDetail.getCreateTime()).isEqualTo(Timestamps.fromMillis(4000L));
    assertThat(dummyCommandDetail.getUpdateTime()).isEqualTo(Timestamps.fromMillis(5000L));
    assertThat(dummyCommandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());

    // Verify final command detail
    RequestDetail finalRequestDetail = unaryOperatorCaptor.getAllValues().get(1).apply(null);
    assertThat(
            finalRequestDetail
                .getCommandDetailsMap()
                .values()
                .iterator()
                .next()
                .getDeviceSerialsList())
        .containsExactly("device_id_1", "device_id_2");
  }

  @Test
  public void onSessionStarting_requestHasZeroTradefedJob_tryCreateNonTradefedJob()
      throws Exception {
    // Intentionally make it fail to create any tradefed test.
    doThrow(new MobileHarnessException(InfraErrorId.XTS_NO_MATCHED_TRADEFED_MODULES, "error"))
        .when(xtsJobCreator)
        .createXtsTradefedTestJob(any());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(moblyJobInfo);
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(
            requestDetail.getCommandDetailsMap().values().iterator().next().getDeviceSerialsList())
        .containsExactly("device_id_1", "device_id_2");
  }

  @Test
  public void onSessionStarting_requestHasNeitherTradefedJobNorNonTradefedJob_cancelSession()
      throws Exception {
    // Intentionally make it fail to create any tradefed test and fail non tradefed test check.
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of());
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo, never()).addJob(any());
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);
    // Contains the failed command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getErrorReason()).isEqualTo(ErrorReason.INVALID_REQUEST);
    assertThat(requestDetail.getErrorMessage())
        .isEqualTo("No jobs were created for session： session_id ");
  }

  @Test
  public void onSessionStarting_addTfJobsHadException_requestDetailContainBasicInfo()
      throws Exception {
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(1000L))
        .thenReturn(Instant.ofEpochMilli(2000L))
        .thenReturn(Instant.ofEpochMilli(3000L));
    MobileHarnessException mhException =
        new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "error");
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenThrow(mhException);
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());

    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);
    assertThat(requestDetail.getCommandInfosList()).containsExactly(commandInfo);
    assertThat(requestDetail.getCreateTime()).isEqualTo(Timestamps.fromMillis(1000L));
    assertThat(requestDetail.getStartTime()).isEqualTo(Timestamps.fromMillis(2000L));
    assertThat(requestDetail.getMaxRetryOnTestFailures())
        .isEqualTo(request.getMaxRetryOnTestFailures());
  }

  @Test
  public void onSessionStarting_addNonTfJobsHadException_requestDetailContainBasicInfo()
      throws Exception {
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(1000L))
        .thenReturn(Instant.ofEpochMilli(2000L))
        .thenReturn(Instant.ofEpochMilli(3000L));
    MobileHarnessException mhException =
        new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "error");
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of());
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenThrow(mhException);
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());

    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    verify(sessionInfo, never()).addJob(any());
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);
    assertThat(requestDetail.getCommandInfosList()).containsExactly(commandInfo);
    assertThat(requestDetail.getCreateTime()).isEqualTo(Timestamps.fromMillis(1000L));
    assertThat(requestDetail.getStartTime()).isEqualTo(Timestamps.fromMillis(2000L));
    assertThat(requestDetail.getMaxRetryOnTestFailures())
        .isEqualTo(request.getMaxRetryOnTestFailures());
  }

  @Test
  public void onSessionStarting_manuallyCancelSession_noJobCreated() throws Exception {
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(1000L))
        .thenReturn(Instant.ofEpochMilli(2000L))
        .thenReturn(Instant.ofEpochMilli(3000L));
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of());
    AtsServerSessionNotification notification =
        AtsServerSessionNotification.newBuilder()
            .setCancelSession(CancelSession.getDefaultInstance())
            .build();
    plugin.onSessionNotification(
        new SessionNotificationEvent(
            sessionInfo,
            SessionNotification.newBuilder().setNotification(Any.pack(notification)).build(),
            TextFormat.printer()));
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo, never()).addJob(any());
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    assertThat(requestDetail.getState()).isEqualTo(RequestState.CANCELED);
    assertThat(requestDetail.getCommandInfosList()).containsExactly(commandInfo);
    assertThat(requestDetail.getCreateTime()).isEqualTo(Timestamps.fromMillis(1000L));
    assertThat(requestDetail.getStartTime()).isEqualTo(Timestamps.fromMillis(2000L));
    assertThat(requestDetail.getMaxRetryOnTestFailures())
        .isEqualTo(request.getMaxRetryOnTestFailures());
  }

  @Test
  public void onJobEnded_tradefedJobEnded_triggerNonTradefedJob() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);

    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    // Verify added non tradefed jobs.
    verify(sessionInfo).addJob(moblyJobInfo);
    // Set 3 times: 2 in onSessionStarting, 1 in onJobEnded.
    verify(sessionInfo, times(3))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(requestDetail.containsCommandDetails(commandId)).isTrue();
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().get(commandId);
    assertThat(commandDetail.getOriginalCommandInfo()).isEqualTo(commandInfo);
  }

  @Test
  public void onTestStarting_cancelSession_sendCancelMessageToTest() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    AtsServerSessionNotification notification =
        AtsServerSessionNotification.newBuilder()
            .setCancelSession(CancelSession.getDefaultInstance())
            .build();
    plugin.onSessionNotification(
        new SessionNotificationEvent(
            sessionInfo,
            SessionNotification.newBuilder().setNotification(Any.pack(notification)).build(),
            TextFormat.printer()));
    plugin.onTestStarting(new TestStartingEvent(testInfo, null, DeviceInfo.getDefaultInstance()));

    verify(testMessageUtil).sendProtoMessageToTest(eq(testInfo), any());
  }

  @Test
  public void onJobEnded_manuallyCancelSession_skipCreatingNonTradefedJob() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());

    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    verify(sessionInfo).addJob(jobInfo);

    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.RUNNING));

    AtsServerSessionNotification notification =
        AtsServerSessionNotification.newBuilder()
            .setCancelSession(CancelSession.getDefaultInstance())
            .build();
    plugin.onTestStarting(new TestStartingEvent(testInfo, null, DeviceInfo.getDefaultInstance()));
    plugin.onSessionNotification(
        new SessionNotificationEvent(
            sessionInfo,
            SessionNotification.newBuilder().setNotification(Any.pack(notification)).build(),
            TextFormat.printer()));

    verify(testMessageUtil).sendProtoMessageToTest(eq(testInfo), any());

    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    // Verify added non tradefed jobs.
    verify(sessionInfo, never()).addJob(moblyJobInfo);
    // Set 5 times: 2 in onSessionStarting, 1 in onTestStarting, 1 in onSessionNotification, 1 in
    // onJobEnded.
    verify(sessionInfo, times(5))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.CANCELED);
  }

  @Test
  public void onJobEnded_tradefedJobEnded_addCommandAttemptIdAndDeviceSerials() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    when(jobInfo.timing()).thenReturn(timing);
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);

    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    // Set 3 times: 2 in onSessionStarting, 1 in onJobEnded.
    verify(sessionInfo, times(3))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().get(commandId);
    assertThat(commandDetail.getOriginalCommandInfo()).isEqualTo(commandInfo);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.RUNNING);
    assertThat(isValidUuid(commandDetail.getCommandAttemptId())).isTrue();
    assertThat(commandDetail.getDeviceSerialsList()).containsExactly("device_id_1", "device_id_2");
  }

  @Test
  public void onJobEnded_multipleTradefedJobsEnded_lastOneTriggerNonTradefedJob() throws Exception {
    request = request.toBuilder().addCommands(commandInfo).build();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo, jobInfo2));
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    // Verify non-TF jobs are created but not added.
    verify(xtsJobCreator, times(2)).createXtsNonTradefedJobs(any());
    verify(sessionInfo, never()).addJob(moblyJobInfo);
    verify(sessionInfo, never()).addJob(moblyJobInfo2);

    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);

    // Verify first jobInfo end signal won't add non-TF jobs.
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));
    verify(sessionInfo).addJob(jobInfo2);
    verify(sessionInfo, never()).addJob(moblyJobInfo);
    verify(sessionInfo, never()).addJob(moblyJobInfo2);

    when(jobInfo2.timing()).thenReturn(timing);
    when(jobInfo2.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result2 = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(jobInfo2.resultWithCause()).thenReturn(result2);
    JobType jobType2 = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo2.type()).thenReturn(jobType2);

    // Verify added non tradefed jobs when the last jobInfo has ended.
    plugin.onJobEnded(new JobEndEvent(jobInfo2, null));
    verify(sessionInfo).addJob(moblyJobInfo);
    verify(sessionInfo).addJob(moblyJobInfo2);
    verify(xtsJobCreator, times(2)).createXtsNonTradefedJobs(any());
  }

  @Test
  public void onJobEnded_nonTradefedJobEnded_onlyUpdateSessionOutput() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    timing.start();
    var unused = timing.end();
    when(jobInfo.timing()).thenReturn(timing);
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("MoblyDriver").build();
    when(jobInfo.type()).thenReturn(jobType);

    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    // Verify that plugin didn't create any new job.
    verify(sessionInfo).addJob(jobInfo);
    // Set 3 times: 2 in onSessionStarting, 1 in onJobEnded.
    verify(sessionInfo, times(3))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next())
        .isEqualTo(commandId);
  }

  @Test
  public void onJobEnded_nonTradefedJobEnded_atsModuleRunResultFileWritten() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    when(jobInfo.type()).thenReturn(JobType.newBuilder().setDriver("MoblyDriver").build());
    Properties jobProperties = new Properties(timing);
    jobProperties.add(Job.IS_XTS_NON_TF_JOB, "true");
    when(jobInfo.properties()).thenReturn(jobProperties);

    com.google.devtools.mobileharness.api.model.job.out.Result result =
        mock(com.google.devtools.mobileharness.api.model.job.out.Result.class);
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
                /* cause= */ null));
    when(testInfo.resultWithCause()).thenReturn(result);
    when(testInfo.getGenFileDir()).thenReturn("/tmp/test_gen_file_dir");

    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    verify(localFileUtil)
        .writeToFile("/tmp/test_gen_file_dir/ats_module_run_result.textproto", "result: PASS\n");
  }

  @Test
  public void onSessionEnded_retryNeeded_createRetrySession() throws Exception {
    request = request.toBuilder().setMaxRetryOnTestFailures(1).build();
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();

    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(testInfo.resultWithCause()).thenReturn(result);

    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));
    CreateSessionResponse response =
        CreateSessionResponse.newBuilder()
            .setSessionId(SessionId.newBuilder().setId("retry_session_id"))
            .build();
    when(localSessionStub.createSession(any())).thenReturn(response);
    com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result.Builder
        resultBuilder =
            com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result
                .newBuilder()
                .setSummary(
                    Summary.newBuilder().setPassed(5).setFailed(5).setModulesTotal(1).build());
    when(sessionResultHandlerUtil.processResult(
            any(), any(), any(), any(), eq(ImmutableList.of(jobInfo)), any()))
        .thenReturn(Optional.of(resultBuilder.build()));

    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));
    ArgumentCaptor<CreateSessionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateSessionRequest.class);
    verify(localSessionStub).createSession(requestCaptor.capture());
    SessionRequest sessionRequest =
        requestCaptor
            .getValue()
            .getSessionConfig()
            .getSessionPluginConfigs()
            .getSessionPluginConfig(0)
            .getExecutionConfig()
            .getConfig()
            .unpack(SessionRequest.class);
    NewMultiCommandRequest newMultiCommandRequest = sessionRequest.getNewMultiCommandRequest();
    assertThat(newMultiCommandRequest.getMaxRetryOnTestFailures()).isEqualTo(0);
    assertThat(newMultiCommandRequest.getRetryPreviousSessionId()).isEqualTo("session_id");
    assertThat(newMultiCommandRequest.getAllPreviousSessionIdsList()).containsExactly("session_id");
    assertThat(newMultiCommandRequest.getCommandsList()).containsExactly(commandInfo);

    // sessionInfo.setSessionPluginOutput() is called 4 times: 2 in onSessionStarting, 1 in
    // onJobEnded, 1 in onSessionEnded.
    // OnSessionStarting() after creating tradefed jobs. Second time in OnJobEnded()
    // after job ended signal trigger session output update. 3th time in OnSessionEnded().
    verify(sessionInfo, times(4))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next())
        .isEqualTo(commandId);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.COMPLETED);
    verifyResourceCleanup(testInfo, jobInfo);
  }

  @Test
  public void onSessionEnded_retryNeededHasCurrentContext_createRetrySession() throws Exception {
    request = request.toBuilder().setMaxRetryOnTestFailures(1).build();
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();

    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(testInfo.resultWithCause()).thenReturn(result);

    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));
    CreateSessionResponse response =
        CreateSessionResponse.newBuilder()
            .setSessionId(SessionId.newBuilder().setId("retry_session_id"))
            .build();
    when(localSessionStub.createSession(any())).thenReturn(response);
    com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result.Builder
        resultBuilder =
            com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result
                .newBuilder()
                .setSummary(
                    Summary.newBuilder().setPassed(5).setFailed(5).setModulesTotal(1).build());
    when(sessionResultHandlerUtil.processResult(
            any(), any(), any(), any(), eq(ImmutableList.of(jobInfo)), any()))
        .thenReturn(Optional.of(resultBuilder.build()));
    Mockito.doReturn(true).when(localFileUtil).isFileExist(any(Path.class));

    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));
    ArgumentCaptor<CreateSessionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateSessionRequest.class);
    verify(localSessionStub).createSession(requestCaptor.capture());
    SessionRequest sessionRequest =
        requestCaptor
            .getValue()
            .getSessionConfig()
            .getSessionPluginConfigs()
            .getSessionPluginConfig(0)
            .getExecutionConfig()
            .getConfig()
            .unpack(SessionRequest.class);
    NewMultiCommandRequest newMultiCommandRequest = sessionRequest.getNewMultiCommandRequest();
    assertThat(newMultiCommandRequest.getMaxRetryOnTestFailures()).isEqualTo(0);
    assertThat(newMultiCommandRequest.getRetryPreviousSessionId()).isEqualTo("session_id");
    assertThat(newMultiCommandRequest.getAllPreviousSessionIdsList()).containsExactly("session_id");
    assertThat(newMultiCommandRequest.getCommandsCount()).isEqualTo(1);
    assertThat(newMultiCommandRequest.getCommandsList().get(0).getCommandLine())
        .isEqualTo(request.getTestEnvironment().getRetryCommandLine());
    assertThat(newMultiCommandRequest.getCommands(0).getDeviceDimensionsList())
        .containsExactlyElementsIn(request.getCommands(0).getDeviceDimensionsList());

    // sessionInfo.setSessionPluginOutput() is called 4 times: 2 in onSessionStarting, 1 in
    // onJobEnded, 1 in onSessionEnded.
    // OnSessionStarting() after creating tradefed jobs. Second time in OnJobEnded()
    // after job ended signal trigger session output update. 3th time in OnSessionEnded().
    verify(sessionInfo, times(4))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next())
        .isEqualTo(commandId);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.COMPLETED);
    assertThat(requestDetail.getTestContextMap().get(commandId))
        .isEqualTo(newMultiCommandRequest.getPrevTestContext());
    verifyResourceCleanup(testInfo, jobInfo);
  }

  @Test
  public void onSessionEnded_retryNeededHasCurrentContext_emptyRetryCommand() throws Exception {
    request =
        request.toBuilder()
            .setMaxRetryOnTestFailures(1)
            .setTestEnvironment(request.getTestEnvironment().toBuilder().clearRetryCommandLine())
            .build();
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();

    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(testInfo.resultWithCause()).thenReturn(result);

    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));
    CreateSessionResponse response =
        CreateSessionResponse.newBuilder()
            .setSessionId(SessionId.newBuilder().setId("retry_session_id"))
            .build();
    when(localSessionStub.createSession(any())).thenReturn(response);
    com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result.Builder
        resultBuilder =
            com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result
                .newBuilder()
                .setSummary(
                    Summary.newBuilder().setPassed(5).setFailed(5).setModulesTotal(1).build());
    when(sessionResultHandlerUtil.processResult(
            any(), any(), any(), any(), eq(ImmutableList.of(jobInfo)), any()))
        .thenReturn(Optional.of(resultBuilder.build()));
    Mockito.doReturn(true).when(localFileUtil).isFileExist(any(Path.class));

    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));
    ArgumentCaptor<CreateSessionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateSessionRequest.class);
    verify(localSessionStub).createSession(requestCaptor.capture());
    SessionRequest sessionRequest =
        requestCaptor
            .getValue()
            .getSessionConfig()
            .getSessionPluginConfigs()
            .getSessionPluginConfig(0)
            .getExecutionConfig()
            .getConfig()
            .unpack(SessionRequest.class);
    NewMultiCommandRequest newMultiCommandRequest = sessionRequest.getNewMultiCommandRequest();
    assertThat(newMultiCommandRequest.getMaxRetryOnTestFailures()).isEqualTo(0);
    assertThat(newMultiCommandRequest.getRetryPreviousSessionId()).isEqualTo("session_id");
    assertThat(newMultiCommandRequest.getAllPreviousSessionIdsList()).containsExactly("session_id");
    assertThat(newMultiCommandRequest.getCommandsCount()).isEqualTo(1);
    assertThat(newMultiCommandRequest.getCommandsList().get(0).getCommandLine())
        .isEqualTo("retry --retry 0");
    verifyResourceCleanup(testInfo, jobInfo);
  }

  @Test
  public void onSessionEnded_retryNeededAndHasPrevContext_createRetrySession() throws Exception {
    String originalCommandLine = "run cts -m OriginalModule";
    request =
        request.toBuilder()
            .setPrevTestContext(
                TestContext.newBuilder().setCommandLine(originalCommandLine).build())
            .setMaxRetryOnTestFailures(1)
            .build();
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();

    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(testInfo.resultWithCause()).thenReturn(result);

    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));
    CreateSessionResponse response =
        CreateSessionResponse.newBuilder()
            .setSessionId(SessionId.newBuilder().setId("retry_session_id"))
            .build();
    when(localSessionStub.createSession(any())).thenReturn(response);
    com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result.Builder
        resultBuilder =
            com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result
                .newBuilder()
                .setSummary(
                    Summary.newBuilder().setPassed(5).setFailed(5).setModulesTotal(1).build());
    when(sessionResultHandlerUtil.processResult(
            any(), any(), any(), any(), eq(ImmutableList.of(jobInfo)), any()))
        .thenReturn(Optional.of(resultBuilder.build()));

    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));
    ArgumentCaptor<CreateSessionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateSessionRequest.class);
    verify(localSessionStub).createSession(requestCaptor.capture());
    SessionRequest sessionRequest =
        requestCaptor
            .getValue()
            .getSessionConfig()
            .getSessionPluginConfigs()
            .getSessionPluginConfig(0)
            .getExecutionConfig()
            .getConfig()
            .unpack(SessionRequest.class);
    NewMultiCommandRequest newMultiCommandRequest = sessionRequest.getNewMultiCommandRequest();
    assertThat(newMultiCommandRequest.getMaxRetryOnTestFailures()).isEqualTo(0);
    assertThat(newMultiCommandRequest.getRetryPreviousSessionId()).isEqualTo("session_id");
    assertThat(newMultiCommandRequest.getAllPreviousSessionIdsList()).containsExactly("session_id");
    assertThat(newMultiCommandRequest.getCommandsList().get(0).getCommandLine())
        .isEqualTo(originalCommandLine);

    // sessionInfo.setSessionPluginOutput() is called 4 times: 2 in onSessionStarting, 1 in
    // onJobEnded, 1 in onSessionEnded.
    // OnSessionStarting() after creating tradefed jobs. Second time in OnJobEnded()
    // after job ended signal trigger session output update. 3th time in OnSessionEnded().
    verify(sessionInfo, times(4))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId = UUID.nameUUIDFromBytes(originalCommandLine.getBytes(UTF_8)).toString();
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next())
        .isEqualTo(commandId);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.COMPLETED);
    verifyResourceCleanup(testInfo, jobInfo);
  }

  @Test
  public void onSessionEnded_sessionFailedAndRetryDisabled_noRetry() throws Exception {
    // Disallow retry.
    request = request.toBuilder().setMaxRetryOnTestFailures(0).build();
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();

    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(testInfo.resultWithCause()).thenReturn(result);

    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));
    CreateSessionResponse response =
        CreateSessionResponse.newBuilder()
            .setSessionId(SessionId.newBuilder().setId("retry_session_id"))
            .build();
    when(localSessionStub.createSession(any())).thenReturn(response);
    com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result.Builder
        resultBuilder =
            com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result
                .newBuilder()
                .setSummary(Summary.newBuilder().setPassed(5).setFailed(5).build());
    when(sessionResultHandlerUtil.processResult(
            any(), any(), any(), any(), eq(ImmutableList.of(jobInfo)), any()))
        .thenReturn(Optional.of(resultBuilder.build()));

    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));

    // Verify didn't run retry as retry is not allowed.
    verify(localSessionStub, never()).createSession(any());

    // sessionInfo.setSessionPluginOutput() is called 4 times: 2 in onSessionStarting, 1 in
    // onJobEnded, 1 in onSessionEnded.
    // OnSessionStarting() after creating tradefed jobs. Second times in OnJobEnded()
    // after job ended signal trigger session output update. 3th time in
    // OnSessionEnded() after handling the session result and after setting error message.
    verify(sessionInfo, times(4))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next())
        .isEqualTo(commandId);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.COMPLETED);
    verifyResourceCleanup(testInfo, jobInfo);
  }

  @Test
  public void onSessionEnded_sessionSucceeded_noRetry() throws Exception {
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();

    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(testInfo.resultWithCause()).thenReturn(result);

    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result.Builder
        resultBuilder =
            com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result
                .newBuilder()
                .setSummary(Summary.newBuilder().setPassed(5).setFailed(0).build());
    when(sessionResultHandlerUtil.processResult(
            any(), any(), any(), any(), eq(ImmutableList.of(jobInfo)), any()))
        .thenReturn(Optional.of(resultBuilder.build()));
    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));
    verify(localSessionStub, never()).createSession(any());

    // sessionInfo.setSessionPluginOutput() is called 4 times: 2 in onSessionStarting, 1 in
    // onJobEnded, 1 in onSessionEnded.
    // OnSessionStarting() after creating tradefed jobs. Second time in OnJobEnded()
    // after job ended signal trigger session output update.
    verify(sessionInfo, times(4))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next())
        .isEqualTo(commandId);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.COMPLETED);
    verifyResourceCleanup(testInfo, jobInfo);
  }

  @Test
  public void onSessionEnded_sessionZeroTotalTests_noRetry() throws Exception {
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();

    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result =
        new Result(timing.toNewTiming(), new Params(timing).toNewParams())
            .setNonPassing(
                TestResult.FAIL,
                new MobileHarnessException(
                    InfraErrorId.XTS_NO_MATCHED_TRADEFED_MODULES, "error message!"));
    when(testInfo.resultWithCause()).thenReturn(result);

    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result.Builder
        resultBuilder =
            com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result
                .newBuilder()
                .setSummary(Summary.newBuilder().setPassed(0).setFailed(0).build());
    when(sessionResultHandlerUtil.processResult(
            any(), any(), any(), any(), eq(ImmutableList.of(jobInfo)), any()))
        .thenReturn(Optional.of(resultBuilder.build()));
    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));
    verify(localSessionStub, never()).createSession(any());

    // sessionInfo.setSessionPluginOutput() is called 4 times: 2 in onSessionStarting, 1 in
    // onJobEnded, 1 in onSessionEnded.
    // OnSessionStarting() after creating tradefed jobs. Second time in OnJobEnded()
    // after job ended signal trigger session output update.
    verify(sessionInfo, times(4))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next())
        .isEqualTo(commandId);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);
    verifyResourceCleanup(testInfo, jobInfo);
  }

  @Test
  public void onSessionEnded_handleResultProcessingThrowException_setErrorState() throws Exception {
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    timing.start();
    var unused = timing.end();

    when(testInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing.toNewTiming(), new Params(timing).toNewParams()).setPass();
    when(testInfo.resultWithCause()).thenReturn(result);

    when(jobInfo.resultWithCause()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));
    IllegalStateException exception = new IllegalStateException("test");
    doThrow(exception)
        .when(sessionResultHandlerUtil)
        .processResult(any(), any(), any(), any(), any(), any());
    assertThrows(
        IllegalStateException.class,
        () -> plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null)));
    // sessionInfo.setSessionPluginOutput() is called 4 times: 2 in onSessionStarting, 1 in
    // onJobEnded, 1 in onSessionEnded (finally block).
    // OnSessionStarting() after creating tradefed jobs. Second time in OnJobEnded()
    // after job ended signal trigger session output update.
    verify(sessionInfo, times(4))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    assertThat(Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null).getState())
        .isEqualTo(RequestState.ERROR);
    verifyResourceCleanup(testInfo, jobInfo);
  }

  @Test
  public void onSessionEnded_invalidRequest_noRetry() throws Exception {
    // Intentionally make it fail to create any tradefed test and fail non tradefed test check.
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of());
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo, never()).addJob(any());
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);
    // Contains the failed command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getErrorReason()).isEqualTo(ErrorReason.INVALID_REQUEST);

    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));
    verify(localSessionStub, never()).createSession(any());

    // Verify total calls: 2 in onSessionStarting, 1 in onSessionEnded.
    verify(sessionInfo, times(3))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail finalRequestDetail =
        Iterables.getLast(unaryOperatorCaptor.getAllValues()).apply(null);
    assertThat(finalRequestDetail.getState()).isEqualTo(RequestState.ERROR);
    verifyResourceCleanup(testInfo, jobInfo);
  }

  private static boolean isValidUuid(String uuid) {
    try {
      UUID.fromString(uuid);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private void verifyResourceCleanup(TestInfo testInfo, JobInfo jobInfo) throws Exception {
    verify(sessionResultHandlerUtil).cleanUpJobGenDirs(ImmutableList.of(jobInfo));
    verify(sessionResultHandlerUtil).cleanUpLabGenFileDir(testInfo);
    verifyUnmountRootDir(DirUtil.getPublicGenDir() + "/session_session_id/file");
  }

  private void verifyUnmountRootDir(String xtsRootDir) throws Exception {
    // Verify that handler has unmounted the zip file after calling cleanup().
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(unmountCommand);
    verify(sleeper).sleep(Duration.ofSeconds(5));
  }
}
