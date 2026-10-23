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
import static com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.OUTPUT_MANIFEST_FILE_NAME;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaDuration;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoDuration;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.XtsTypeLoader;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.ErrorReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestContext;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.CreateJobsResult;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.HandleResultProcessingResult;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.PrepareSessionRequestResult;
import com.google.devtools.mobileharness.infra.ats.server.util.AtsServerSessionUtil;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo.TradefedInvocation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil.XtsTradefedRuntimeInfoFileDetail;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class NewMultiCommandRequestHandlerTest {
  private static final String ANDROID_XTS_ZIP = "file:///path/to/xts/zip/file.zip";
  private static final String DEFAULT_COMMAND_LINE =
      "cts-plan --module module1 --test test1 --logcat-on-failure --shard-count 2"
          + " --parallel-setup true --parallel-setup-timeout 0";
  private static final String DEVICE_ID_1 = "device_id_1";
  private static final String DEVICE_ID_2 = "device_id_2";
  private static final Path TRADEFED_INVOCATION_LOG_DIR = Path.of("/tradefed_invocation_log_dir");
  private static final ResultTypeWithCause PASS_RESULT =
      ResultTypeWithCause.create(TestResult.PASS, null);
  private static final ResultTypeWithCause ERROR_RESULT =
      ResultTypeWithCause.create(
          TestResult.ERROR,
          new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "test failed with exception"));

  private CommandInfo commandInfo = CommandInfo.getDefaultInstance();
  private NewMultiCommandRequest request = NewMultiCommandRequest.getDefaultInstance();
  private String outputFileUploadPath = "";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final SetFlags flags = new SetFlags();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private SessionResultHandlerUtil sessionResultHandlerUtil;
  @Bind @Mock private CommandExecutor commandExecutor;
  @Bind @Mock private Clock clock;
  @Bind @Mock private XtsTypeLoader xtsTypeLoader;
  @Bind @Spy private LocalFileUtil localFileUtil = new LocalFileUtil();
  @Bind @Mock private XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil;
  @Bind @Mock private Sleeper sleeper;
  @Bind @Mock private AtsServerSessionUtil atsServerSessionUtil;

  @Mock private SessionInfo sessionInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  @Mock private TestInfo testInfo;
  @Mock private TestInfos testInfos;
  @Mock private Result newJobResult;
  @Mock private Result newTestResult;
  private final Properties properties = new Properties(new Timing());

  @Inject private NewMultiCommandRequestHandler newMultiCommandRequestHandler;

  @Before
  public void setup() throws Exception {
    String publicDir = tmpFolder.newFolder("public_dir").getAbsolutePath();
    flags.set("public_dir", publicDir);
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionInfo.getSessionId()).thenReturn("session_id");
    properties.add(Job.IS_XTS_TF_JOB, "true");
    properties.add(
        Job.XTS_COMMAND_ID,
        UUID.nameUUIDFromBytes(DEFAULT_COMMAND_LINE.getBytes(UTF_8)).toString());
    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    when(jobInfo.properties()).thenReturn(properties);
    when(jobInfo.files()).thenReturn(files);
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(sessionRequestHandlerUtil.addXtsModuleInfo(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId(DEVICE_ID_1).addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId(DEVICE_ID_2).addType("AndroidOnlineDevice"))
                .build());
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    when(xtsTypeLoader.getXtsType(eq(xtsRootDir), any())).thenReturn("cts");
    doReturn(true).when(localFileUtil).isDirExist(endsWith("/android-cts/testcases"));
    doReturn(ImmutableList.of(new File("some_testcase")))
        .when(localFileUtil)
        .listFiles(endsWith("/android-cts/testcases"), eq(false));
    commandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine(DEFAULT_COMMAND_LINE)
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_2)
                    .build())
            .build();
    outputFileUploadPath = tmpFolder.newFolder("output_file_upload_path").getAbsolutePath();
    request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(commandInfo)
            .setQueueTimeout(toProtoDuration(Duration.ofSeconds(1000L)))
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///data/path/to/file1")
                    .setName("test-name-1")
                    .build())
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///data/path/to/file2")
                    .setName("test-name-2")
                    .build())
            .setTestEnvironment(
                TestEnvironment.newBuilder()
                    .setInvocationTimeout(toProtoDuration(Duration.ofSeconds(2000L)))
                    .setOutputFileUploadUrl("file://" + outputFileUploadPath)
                    .putAllEnvVars(ImmutableMap.of("env_key1", "env_value1"))
                    .setUseParallelSetup(true)
                    .build())
            .build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1000L));
    when(sessionInfo.getSessionProperty(SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH))
        .thenReturn(Optional.of("/path/to/server_session_log.txt"));
    when(sessionResultHandlerUtil.getTradefedInvocationLogDir(any(), any()))
        .thenReturn(TRADEFED_INVOCATION_LOG_DIR);
    doReturn(true)
        .when(localFileUtil)
        .isFileExist(
            eq(TRADEFED_INVOCATION_LOG_DIR.resolve(XtsConstants.TRADEFED_RUNTIME_INFO_FILE_NAME)));
    // Mock the content of the tradefed invocation runtime info log file.
    // The list of tradefed invocations will only be non-empty if there was any invocation error.
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        /* invocations= */ ImmutableList.of(), /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));
    when(sessionRequestHandlerUtil.getSessionSubDeviceSpecList(any(), anyBoolean()))
        .thenReturn(
            ImmutableList.of(
                SubDeviceSpec.getDefaultInstance(), SubDeviceSpec.getDefaultInstance()));
    when(sessionRequestHandlerUtil.getHostIp(any())).thenReturn("127.0.0.1");
    when(atsServerSessionUtil.replacePathForRemoteRunner(anyString()))
        .thenAnswer(
            invocation -> {
              String path = invocation.getArgument(0, String.class);
              return "ats-file-server::"
                  + (path.startsWith("/data/") ? path.substring("/data".length()) : path);
            });
    doCallRealMethod().when(atsServerSessionUtil).insertAdditionalTestResource(any(), any());
  }

  @Test
  public void prepareSessionRequestInfo_invalidCommandLine() throws Exception {
    CommandInfo commandInfoWithInvalidCommandLine =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("cts -m module1 --logcat-on-failure --shard-count")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_2)
                    .build())
            .build();
    request =
        request.toBuilder().clearCommands().addCommands(commandInfoWithInvalidCommandLine).build();
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isEmpty();

    // Verify request detail.
    assertThat(prepareResult.errorReason()).hasValue(ErrorReason.INVALID_REQUEST);
    assertThat(prepareResult.state()).isEqualTo(RequestState.ERROR);

    // Verify command detail.
    assertThat(prepareResult.commandDetail()).isPresent();
    String commandId =
        UUID.nameUUIDFromBytes(commandInfoWithInvalidCommandLine.getCommandLine().getBytes(UTF_8))
            .toString();
    CommandDetail commandDetail = prepareResult.commandDetail().get();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getCommandLine())
        .isEqualTo(commandInfoWithInvalidCommandLine.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void prepareSessionRequestInfo_success() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();
    assertThat(prepareResult.commandDetail()).isPresent();
    CommandDetail commandDetail = prepareResult.commandDetail().get();
    String commandId = commandDetail.getId();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getSessionId()).isEqualTo("session_id");
    assertThat(sessionRequestInfo.getXtsCommandId()).isEqualTo(commandId);
    assertThat(sessionRequestInfo.getTestPlan()).isEqualTo("cts-plan");
    assertThat(sessionRequestInfo.getModuleNamesList()).containsExactly("module1");
    assertThat(
            sessionRequestInfo.hasTestName()
                ? Optional.of(sessionRequestInfo.getTestName())
                : Optional.empty())
        .hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.getXtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.getXtsType()).isEqualTo("cts");
    assertThat(
            sessionRequestInfo.hasAndroidXtsZip()
                ? Optional.of(sessionRequestInfo.getAndroidXtsZip())
                : Optional.empty())
        .hasValue("ats-file-server::" + zipFile);
    assertThat(toJavaDuration(sessionRequestInfo.getStartTimeout()))
        .isEqualTo(Duration.ofSeconds(1000));
    assertThat(toJavaDuration(sessionRequestInfo.getJobTimeout()))
        .isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.getDeviceSerialsList()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(
            sessionRequestInfo.hasShardCount()
                ? Optional.of(sessionRequestInfo.getShardCount())
                : Optional.empty())
        .hasValue(2);
    assertThat(sessionRequestInfo.getEnvVarsMap()).containsExactly("env_key1", "env_value1");
    assertThat(
            sessionRequestInfo.hasRemoteRunnerFilePathPrefix()
                ? Optional.of(sessionRequestInfo.getRemoteRunnerFilePathPrefix())
                : Optional.empty())
        .hasValue("ats-file-server::");

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
  }

  @Test
  public void prepareSessionRequestInfo_forStsWithUnderscoreZip_success() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    request =
        request.toBuilder()
            .clearTestResources()
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///path/to/xts/zip/android-sts.zip_")
                    .setName("android-sts.zip_")
                    .build())
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///data/path/to/file1")
                    .setName("test-name-1")
                    .build())
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///data/path/to/file2")
                    .setName("test-name-2")
                    .build())
            .build();
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/android-sts";
    when(xtsTypeLoader.getXtsType(eq(xtsRootDir), any())).thenReturn("sts");
    doReturn(true).when(localFileUtil).isDirExist(endsWith("/android-sts/testcases"));
    doReturn(ImmutableList.of(new File("some_testcase")))
        .when(localFileUtil)
        .listFiles(endsWith("/android-sts/testcases"), eq(false));

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    String zipFile = "/path/to/xts/zip/android-sts.zip_";
    assertThat(sessionRequestInfo.getXtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.getXtsType()).isEqualTo("sts");
    assertThat(
            sessionRequestInfo.hasAndroidXtsZip()
                ? Optional.of(sessionRequestInfo.getAndroidXtsZip())
                : Optional.empty())
        .hasValue("ats-file-server::" + zipFile);

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
  }

  @Test
  public void prepareSessionRequestInfo_forChunkedCtsZip_success() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    request =
        request.toBuilder()
            .clearTestResources()
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///path/to/xts/zip/android-chunked-cts.zip")
                    .setName("android-chunked-cts.zip")
                    .build())
            .build();
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/android-chunked-cts";
    when(xtsTypeLoader.getXtsType(eq(xtsRootDir), any())).thenReturn("cts");

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    // Verify that the chunked zip is recognized as the xTS zip instead of an extra test resource.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    String zipFile = "/path/to/xts/zip/android-chunked-cts.zip";
    assertThat(sessionRequestInfo.getXtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.getXtsType()).isEqualTo("cts");
    assertThat(
            sessionRequestInfo.hasAndroidXtsZip()
                ? Optional.of(sessionRequestInfo.getAndroidXtsZip())
                : Optional.empty())
        .hasValue("ats-file-server::" + zipFile);
    assertThat(sessionRequestInfo.getAtsServerTestResourcesList()).isEmpty();
  }

  @Test
  public void prepareSessionRequestInfo_withCtsMediaZip_ctsMediaZipIsNotXtsZip() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    request =
        request.toBuilder()
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///data/path/to/android-cts-media-1.5.zip")
                    .setName("android-cts-media-1.5.zip")
                    .build())
            .build();

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    // Verify that the cts media zip is still treated as an additional test resource, and that the
    // xTS zip is still the android-cts.zip resource.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getXtsRootDir())
        .isEqualTo(DirUtil.getPublicGenDir() + "/session_session_id/file");
    assertThat(
            sessionRequestInfo.hasAndroidXtsZip()
                ? Optional.of(sessionRequestInfo.getAndroidXtsZip())
                : Optional.empty())
        .hasValue("ats-file-server::/path/to/xts/zip/file.zip");
    assertThat(sessionRequestInfo.getAtsServerTestResourcesList())
        .contains(
            TestResource.newBuilder()
                .setUrl("file:///data/path/to/android-cts-media-1.5.zip")
                .setName("android-cts-media-1.5.zip")
                .build());
  }

  @Test
  public void prepareSessionRequestInfo_withAcloud_success() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    request =
        request.toBuilder()
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///bin/acloud_prebuilt")
                    .setName("acloud_prebuilt")
                    .build())
            .build();

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();
    assertThat(prepareResult.commandDetail()).isPresent();
    CommandDetail commandDetail = prepareResult.commandDetail().get();
    String commandId = commandDetail.getId();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getXtsCommandId()).isEqualTo(commandId);
    assertThat(sessionRequestInfo.getTestPlan()).isEqualTo("cts-plan");
    assertThat(sessionRequestInfo.getModuleNamesList()).containsExactly("module1");
    assertThat(
            sessionRequestInfo.hasTestName()
                ? Optional.of(sessionRequestInfo.getTestName())
                : Optional.empty())
        .hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.getXtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.getXtsType()).isEqualTo("cts");
    assertThat(
            sessionRequestInfo.hasAndroidXtsZip()
                ? Optional.of(sessionRequestInfo.getAndroidXtsZip())
                : Optional.empty())
        .hasValue("ats-file-server::" + zipFile);
    assertThat(toJavaDuration(sessionRequestInfo.getStartTimeout()))
        .isEqualTo(Duration.ofSeconds(1000));
    assertThat(toJavaDuration(sessionRequestInfo.getJobTimeout()))
        .isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.getDeviceSerialsList()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(
            sessionRequestInfo.hasShardCount()
                ? Optional.of(sessionRequestInfo.getShardCount())
                : Optional.empty())
        .hasValue(2);
    assertThat(sessionRequestInfo.getEnvVarsMap()).containsExactly("env_key1", "env_value1");
    assertThat(
            sessionRequestInfo.hasRemoteRunnerFilePathPrefix()
                ? Optional.of(sessionRequestInfo.getRemoteRunnerFilePathPrefix())
                : Optional.empty())
        .hasValue("ats-file-server::");

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    assertThat(sessionRequestInfo.getAtsServerTestResourcesList())
        .contains(
            TestResource.newBuilder()
                .setUrl("file:///bin/acloud_prebuilt")
                .setName("acloud_prebuilt")
                .build());
  }

  @Test
  public void prepareSessionRequestInfo_localMode_success() throws Exception {
    when(atsServerSessionUtil.isLocalMode()).thenReturn(true);
    doReturn("output")
        .when(localFileUtil)
        .unzipFile(anyString(), anyString(), any(Duration.class), any());
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();
    assertThat(prepareResult.commandDetail()).isPresent();
    CommandDetail commandDetail = prepareResult.commandDetail().get();
    String commandId = commandDetail.getId();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getXtsCommandId()).isEqualTo(commandId);
    assertThat(sessionRequestInfo.getTestPlan()).isEqualTo("cts-plan");
    assertThat(sessionRequestInfo.getModuleNamesList()).containsExactly("module1");
    assertThat(
            sessionRequestInfo.hasTestName()
                ? Optional.of(sessionRequestInfo.getTestName())
                : Optional.empty())
        .hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.getXtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.getXtsType()).isEqualTo("cts");
    assertThat(
            sessionRequestInfo.hasAndroidXtsZip()
                ? Optional.of(sessionRequestInfo.getAndroidXtsZip())
                : Optional.empty())
        .hasValue("ats-file-server::" + zipFile);
    assertThat(toJavaDuration(sessionRequestInfo.getStartTimeout()))
        .isEqualTo(Duration.ofSeconds(1000));
    assertThat(toJavaDuration(sessionRequestInfo.getJobTimeout()))
        .isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.getDeviceSerialsList()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(
            sessionRequestInfo.hasShardCount()
                ? Optional.of(sessionRequestInfo.getShardCount())
                : Optional.empty())
        .hasValue(2);
    assertThat(sessionRequestInfo.getEnvVarsMap()).containsExactly("env_key1", "env_value1");
    assertThat(
            sessionRequestInfo.hasRemoteRunnerFilePathPrefix()
                ? Optional.of(sessionRequestInfo.getRemoteRunnerFilePathPrefix())
                : Optional.empty())
        .hasValue("ats-file-server::");

    // Verify that handler has unzipped the zip file.
    verify(localFileUtil).unzipFile(zipFile, xtsRootDir, Duration.ofHours(1), "");
  }

  @Test
  public void prepareSessionRequestInfo_unzipWithPassword() throws Exception {
    when(atsServerSessionUtil.isLocalMode()).thenReturn(true);
    doReturn("output")
        .when(localFileUtil)
        .unzipFile(anyString(), anyString(), any(Duration.class), anyString());
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    request =
        request.toBuilder()
            .clearTestResources()
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .setPassword("123")
                    .build())
            .build();
    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";

    assertThat(prepareResult.sessionRequestInfo()).isPresent();
    // Verify that handler has unzipped the zip file.
    verify(localFileUtil).unzipFile(zipFile, xtsRootDir, Duration.ofHours(1), "123");
  }

  @Test
  public void prepareSessionRequestInfo_fromRetrySession_success() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    doReturn(Path.of("/path/to/previous_result.pb")).when(localFileUtil).checkFile(any(Path.class));
    String expectedCommandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    String retryCommandLine = "retry --retry 1";
    String prevSessionId = UUID.randomUUID().toString();
    request =
        request.toBuilder()
            .clearCommands()
            .addCommands(commandInfo.toBuilder().setCommandLine(retryCommandLine))
            .setPrevTestContext(
                TestContext.newBuilder()
                    .setCommandLine(commandInfo.getCommandLine())
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl(
                                "file:///path/retry_previous_test_run_id/output/"
                                    + prevSessionId
                                    + "/"
                                    + expectedCommandId
                                    + "/2024.07.16_15.09.01.972_5844.zip")
                            .setName("2024.07.16_15.09.01.972_5844.zip")
                            .build())
                    .build())
            .build();

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    assertThat(prepareResult.commandDetail()).isPresent();
    CommandDetail commandDetail = prepareResult.commandDetail().get();
    assertThat(commandDetail.getCommandLine()).isEqualTo(retryCommandLine);
    assertThat(commandDetail.getId()).isEqualTo(expectedCommandId);

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getXtsCommandId()).isEqualTo(expectedCommandId);
    assertThat(sessionRequestInfo.getTestPlan()).isEqualTo("retry");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.getXtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.getXtsType()).isEqualTo("cts");
    assertThat(
            sessionRequestInfo.hasAndroidXtsZip()
                ? Optional.of(sessionRequestInfo.getAndroidXtsZip())
                : Optional.empty())
        .hasValue("ats-file-server::" + zipFile);
    assertThat(toJavaDuration(sessionRequestInfo.getStartTimeout()))
        .isEqualTo(Duration.ofSeconds(1000));
    assertThat(toJavaDuration(sessionRequestInfo.getJobTimeout()))
        .isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.getDeviceSerialsList()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(sessionRequestInfo.getEnvVarsMap()).containsExactly("env_key1", "env_value1");
    assertThat(
            sessionRequestInfo.hasRetrySessionId()
                ? Optional.of(sessionRequestInfo.getRetrySessionId())
                : Optional.empty())
        .hasValue(prevSessionId);
    String retryResultDir =
        "/path/retry_previous_test_run_id/output/" + prevSessionId + "/" + expectedCommandId;
    assertThat(
            sessionRequestInfo.hasRetryResultDir()
                ? Optional.of(sessionRequestInfo.getRetryResultDir())
                : Optional.empty())
        .hasValue(retryResultDir);

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
  }

  @Test
  public void
      prepareSessionRequestInfo_withPrevContextAndNotRetryCommand_overrideToRetryCommandLine()
          throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    doReturn(Path.of("/path/to/previous_result.pb")).when(localFileUtil).checkFile(any(Path.class));
    String expectedCommandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    String retryCommandLine = "retry --retry 1";
    String prevSessionId = UUID.randomUUID().toString();
    request =
        request.toBuilder()
            .clearCommands()
            .addCommands(commandInfo) // Not a retry command
            .setPrevTestContext(
                TestContext.newBuilder()
                    .setCommandLine(commandInfo.getCommandLine())
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl(
                                "file:///path/retry_previous_test_run_id/output/"
                                    + prevSessionId
                                    + "/"
                                    + expectedCommandId
                                    + "/2024.07.16_15.09.01.972_5844.zip")
                            .setName("2024.07.16_15.09.01.972_5844.zip")
                            .build())
                    .build())
            .setTestEnvironment(
                request.getTestEnvironment().toBuilder().setRetryCommandLine(retryCommandLine))
            .build();

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getTestPlan()).isEqualTo("retry");
    assertThat(sessionRequestInfo.getCommandLineArgs()).isEqualTo(retryCommandLine);
  }

  @Test
  public void prepareSessionRequestInfo_retryResultNotFound_runAsNewAttempt() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    MobileHarnessException fakeException =
        new MobileHarnessException(
            BasicErrorId.LOCAL_FILE_IS_DIR, "Failed to find retry result file");
    doThrow(fakeException).when(localFileUtil).checkFile(any(Path.class));
    String expectedCommandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    request =
        request.toBuilder()
            .setPrevTestContext(
                TestContext.newBuilder()
                    .setCommandLine(commandInfo.getCommandLine())
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl(
                                "file:///path/retry_previous_test_run_id/output/"
                                    + "retry_previous_session_id/"
                                    + expectedCommandId
                                    + "/2024.07.16_15.09.01.972_5844.zip")
                            .setName("2024.07.16_15.09.01.972_5844.zip")
                            .build())
                    .build())
            .build();

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    assertThat(prepareResult.commandDetail()).isPresent();
    CommandDetail commandDetail = prepareResult.commandDetail().get();
    String commandId = commandDetail.getId();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getXtsCommandId()).isEqualTo(commandId);
    assertThat(sessionRequestInfo.getTestPlan()).isEqualTo("cts-plan");
    assertThat(sessionRequestInfo.getModuleNamesList()).containsExactly("module1");
    assertThat(
            sessionRequestInfo.hasTestName()
                ? Optional.of(sessionRequestInfo.getTestName())
                : Optional.empty())
        .hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.getXtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.getXtsType()).isEqualTo("cts");
    assertThat(
            sessionRequestInfo.hasAndroidXtsZip()
                ? Optional.of(sessionRequestInfo.getAndroidXtsZip())
                : Optional.empty())
        .hasValue("ats-file-server::" + zipFile);
    assertThat(toJavaDuration(sessionRequestInfo.getStartTimeout()))
        .isEqualTo(Duration.ofSeconds(1000));
    assertThat(toJavaDuration(sessionRequestInfo.getJobTimeout()))
        .isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.getDeviceSerialsList()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(
            sessionRequestInfo.hasShardCount()
                ? Optional.of(sessionRequestInfo.getShardCount())
                : Optional.empty())
        .hasValue(2);
    assertThat(sessionRequestInfo.getEnvVarsMap()).containsExactly("env_key1", "env_value1");
    assertThat(
            sessionRequestInfo.hasRetrySessionId()
                ? Optional.of(sessionRequestInfo.getRetrySessionId())
                : Optional.empty())
        .isEmpty();
    assertThat(
            sessionRequestInfo.hasRetryResultDir()
                ? Optional.of(sessionRequestInfo.getRetryResultDir())
                : Optional.empty())
        .isEmpty();
    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
  }

  @Test
  public void prepareSessionRequestInfo_fromRetryResultZip_success() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    doReturn(Path.of("/path/to/previous_result.pb")).when(localFileUtil).checkFile(any(Path.class));
    String fileName = "2024.07.16_15.09.01.972_5844.zip";
    request =
        request.toBuilder()
            .setPrevTestContext(
                TestContext.newBuilder()
                    .setCommandLine("cts")
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl("file:///path/output/" + fileName)
                            .setName(
                                "android-cts/results/" // ATS server will add this prefix.
                                    + fileName)
                            .build())
                    .build())
            .build();
    doNothing().when(localFileUtil).prepareDir(anyString());
    doReturn("").when(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));
    doReturn(true).when(localFileUtil).isFileExist(endsWith(SuiteCommon.TEST_RESULT_PB_FILE_NAME));

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getTestPlan()).isEqualTo("retry");
    assertThat(
            sessionRequestInfo.hasRetrySessionId()
                ? Optional.of(sessionRequestInfo.getRetrySessionId())
                : Optional.empty())
        .hasValue(sessionRequestInfo.getRetrySessionId());
    assertThat(
            sessionRequestInfo.hasRetryResultDir()
                ? Optional.of(sessionRequestInfo.getRetryResultDir())
                : Optional.empty())
        .isPresent();
    verify(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));
  }

  @Test
  public void prepareSessionRequestInfo_fromRetryContextWithNoResultZip_shouldSkip()
      throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    request =
        request.toBuilder()
            .setPrevTestContext(
                TestContext.newBuilder()
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl("file:///path/to/some/other/file.txt")
                            .setName("other_file.txt")
                            .build())
                    .build())
            .build();

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getTestPlan()).isEqualTo("cts-plan");
    assertThat(
            sessionRequestInfo.hasRetrySessionId()
                ? Optional.of(sessionRequestInfo.getRetrySessionId())
                : Optional.empty())
        .isEmpty();
    assertThat(
            sessionRequestInfo.hasRetryResultDir()
                ? Optional.of(sessionRequestInfo.getRetryResultDir())
                : Optional.empty())
        .isEmpty();
  }

  @Test
  public void prepareSessionRequestInfo_retryZipExistsButPbMissing_runAsNewAttempt()
      throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Mock that the unzip operation succeeds.
    doReturn("/path/to/unzipped")
        .when(localFileUtil)
        .unzipFile(anyString(), anyString(), any(Duration.class));

    doReturn(Path.of("/path/to/previous_result.pb")).when(localFileUtil).checkFile(any(Path.class));
    doReturn(false).when(localFileUtil).isFileExist(endsWith(SuiteCommon.TEST_RESULT_PB_FILE_NAME));

    String fileName = "2024.07.16_15.09.01.972_5844.zip";
    request =
        request.toBuilder()
            .setPrevTestContext(
                TestContext.newBuilder()
                    .setCommandLine(commandInfo.getCommandLine())
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl("file:///path/output/" + fileName)
                            .setName(
                                "android-cts/results/" // ATS server will add this prefix.
                                    + fileName)
                            .build())
                    .build())
            .build();

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    // Verify sessionRequestInfo indicates it's NOT a retry.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getTestPlan())
        .isEqualTo("cts-plan"); // Should revert to original plan
    assertThat(
            sessionRequestInfo.hasRetrySessionId()
                ? Optional.of(sessionRequestInfo.getRetrySessionId())
                : Optional.empty())
        .isEmpty();
    assertThat(
            sessionRequestInfo.hasRetryResultDir()
                ? Optional.of(sessionRequestInfo.getRetryResultDir())
                : Optional.empty())
        .isEmpty();

    // Verify that unzip was called.
    verify(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));
    // Verify that checkFile was called for test_result.pb.
    verify(localFileUtil).isFileExist(endsWith(SuiteCommon.TEST_RESULT_PB_FILE_NAME));
  }

  @Test
  public void prepareSessionRequestInfo_fromRetryTestRun_success() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    String expectedCommandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    String prevSessionId = UUID.randomUUID().toString();
    doReturn(Path.of("/path/to/previous_result.pb")).when(localFileUtil).checkFile(any(Path.class));
    request =
        request.toBuilder()
            .setPrevTestContext(
                TestContext.newBuilder()
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl(
                                "file:///path/retry_previous_test_run_id/output/"
                                    + prevSessionId
                                    + "/"
                                    + expectedCommandId
                                    + "/2024.07.16_15.09.01.972_5844.zip")
                            .setName("2024.07.16_15.09.01.972_5844.zip")
                            .build())
                    .build())
            .build();

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    assertThat(prepareResult.commandDetail()).isPresent();
    CommandDetail commandDetail = prepareResult.commandDetail().get();
    String commandId = commandDetail.getId();
    assertThat(commandId).isEqualTo(expectedCommandId);
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = prepareResult.sessionRequestInfo().get();
    assertThat(sessionRequestInfo.getXtsCommandId()).isEqualTo(commandId);
    assertThat(sessionRequestInfo.getTestPlan()).isEqualTo("retry");
    assertThat(sessionRequestInfo.getModuleNamesList()).containsExactly("module1");
    assertThat(
            sessionRequestInfo.hasTestName()
                ? Optional.of(sessionRequestInfo.getTestName())
                : Optional.empty())
        .hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.getXtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.getXtsType()).isEqualTo("cts");
    assertThat(
            sessionRequestInfo.hasAndroidXtsZip()
                ? Optional.of(sessionRequestInfo.getAndroidXtsZip())
                : Optional.empty())
        .hasValue("ats-file-server::" + zipFile);
    assertThat(toJavaDuration(sessionRequestInfo.getStartTimeout()))
        .isEqualTo(Duration.ofSeconds(1000));
    assertThat(toJavaDuration(sessionRequestInfo.getJobTimeout()))
        .isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.getDeviceSerialsList()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(
            sessionRequestInfo.hasShardCount()
                ? Optional.of(sessionRequestInfo.getShardCount())
                : Optional.empty())
        .hasValue(2);
    assertThat(sessionRequestInfo.getEnvVarsMap()).containsExactly("env_key1", "env_value1");
    assertThat(
            sessionRequestInfo.hasRetrySessionId()
                ? Optional.of(sessionRequestInfo.getRetrySessionId())
                : Optional.empty())
        .hasValue(prevSessionId);
    String retryResultDir =
        "/path/retry_previous_test_run_id/output/" + prevSessionId + "/" + commandId;
    assertThat(
            sessionRequestInfo.hasRetryResultDir()
                ? Optional.of(sessionRequestInfo.getRetryResultDir())
                : Optional.empty())
        .hasValue(retryResultDir);

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
  }

  @Test
  public void prepareSessionRequestInfo_mountFailedWithMountpointNotEmpty_success()
      throws Exception {
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    CommandException commandException = Mockito.mock(CommandException.class);
    when(commandException.getMessage()).thenReturn("mountpoint is not empty");
    when(commandExecutor.run(mountCommand)).thenThrow(commandException);

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();
    assertThat(prepareResult.state()).isEqualTo(RequestState.RUNNING);

    // Verify that handler has tried to mount the zip file.
    verify(commandExecutor).run(mountCommand);
    // Verify that handler has not unzipped the zip file.
    verify(localFileUtil, never()).unzipFile(anyString(), anyString(), any(Duration.class));
  }

  @Test
  public void prepareSessionRequestInfo_mountFailedUnzipSuccess_success() throws Exception {
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    CommandException commandException = Mockito.mock(CommandException.class);
    when(commandException.getMessage()).thenReturn("fuse-zip not found");
    when(commandExecutor.run(mountCommand)).thenThrow(commandException);
    doReturn("output").when(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();
    assertThat(prepareResult.state()).isEqualTo(RequestState.RUNNING);

    // Verify that handler has tried to mount the zip file.
    verify(commandExecutor).run(mountCommand);
    // Verify that handler has unzipped the zip file.
    verify(localFileUtil).unzipFile(zipFile, xtsRootDir, Duration.ofHours(1));
  }

  @Test
  public void prepareSessionRequestInfo_mountButInvalidRootDir_unzipInstead() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    when(xtsTypeLoader.getXtsType(eq(xtsRootDir), any()))
        .thenThrow(new IllegalStateException("Failed to get XTS type"))
        .thenReturn("cts");
    doReturn("output").when(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    // Verify that handler has unmounted the zip file after mount validation failed.
    verifyUnmountRootDir(xtsRootDir);
    // Verify that handler has unzipped the zip file.
    verify(localFileUtil).unzipFile(zipFile, xtsRootDir, Duration.ofHours(1));
  }

  @Test
  public void prepareSessionRequestInfo_chunkedXtsZip_testCasesRestored() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    doReturn(true).when(localFileUtil).isDirExist(endsWith("/android-cts/chunked-testcases"));
    doReturn(true).when(localFileUtil).isFileExist(endsWith("/android-cts/tools/cts_restorer"));
    doReturn("output").when(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));

    // Trigger the handler.
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isPresent();
    assertThat(prepareResult.state()).isEqualTo(RequestState.RUNNING);

    // Verify that handler has unzipped the zip file instead of using the read-only mounted dir.
    verifyUnmountRootDir(xtsRootDir);
    verify(localFileUtil).unzipFile(zipFile, xtsRootDir, Duration.ofHours(1));
    // Verify that handler has restored the test cases from the chunked test cases dir.
    verify(commandExecutor).run(restoreChunkedTestCasesCommand(xtsRootDir));
  }

  @Test
  public void prepareSessionRequestInfo_chunkedXtsZipWithoutRestorer_errorWithInvalidResourceError()
      throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    doReturn(true).when(localFileUtil).isDirExist(endsWith("/android-cts/chunked-testcases"));
    doReturn("output").when(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isEmpty();
    assertThat(prepareResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(prepareResult.errorReason()).hasValue(ErrorReason.INVALID_RESOURCE);
    verify(commandExecutor, never()).run(restoreChunkedTestCasesCommand(xtsRootDir));
  }

  @Test
  public void
      prepareSessionRequestInfo_restoreChunkedTestCasesFailed_errorWithInvalidResourceError()
          throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    doReturn(true).when(localFileUtil).isDirExist(endsWith("/android-cts/chunked-testcases"));
    doReturn(true).when(localFileUtil).isFileExist(endsWith("/android-cts/tools/cts_restorer"));
    doReturn("output").when(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));
    // A real exception (rather than a Mockito mock) is required here because the error handling
    // path renders the cause's stack trace, which a mocked Throwable does not have.
    MobileHarnessException restoreException =
        new MobileHarnessException(BasicErrorId.COMMAND_EXEC_FAIL, "restore failed");
    when(commandExecutor.run(restoreChunkedTestCasesCommand(xtsRootDir)))
        .thenAnswer(
            invocation -> {
              throw restoreException;
            });

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isEmpty();
    assertThat(prepareResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(prepareResult.errorReason()).hasValue(ErrorReason.INVALID_RESOURCE);
  }

  @Test
  public void
      prepareSessionRequestInfo_mountOrUnzipAndroidXtsZipFailed_errorWithInvalidResourceError()
          throws Exception {
    CommandException commandException = Mockito.mock(CommandException.class);
    when(commandException.getMessage()).thenReturn("");
    when(commandExecutor.run(any())).thenThrow(commandException);

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isEmpty();

    // Verify request detail.
    assertThat(prepareResult.errorReason()).hasValue(ErrorReason.INVALID_RESOURCE);
    assertThat(prepareResult.state()).isEqualTo(RequestState.ERROR);

    // Verify command detail.
    assertThat(prepareResult.commandDetail()).isPresent();

    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    CommandDetail commandDetail = prepareResult.commandDetail().get();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void prepareSessionRequestInfoWithInvalidResource_addResultToSessionOutput()
      throws Exception {
    CommandInfo commandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("cts -m module1 --logcat-on-failure")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .build();
    NewMultiCommandRequest request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(commandInfo)
            .addTestResources(
                TestResource.newBuilder().setUrl(ANDROID_XTS_ZIP).setName("INVALID_NAME").build())
            .build();

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isEmpty();

    // Verify request detail.
    assertThat(prepareResult.errorReason()).hasValue(ErrorReason.INVALID_REQUEST);
    assertThat(prepareResult.state()).isEqualTo(RequestState.ERROR);

    // Verify command detail.
    assertThat(prepareResult.commandDetail()).isPresent();
    CommandDetail commandDetail = prepareResult.commandDetail().get();
    assertThat(commandDetail.getId())
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void prepareSessionRequestInfoWithEmptyCommand_addResultToSessionOutput()
      throws Exception {
    NewMultiCommandRequest request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    assertThat(prepareResult.sessionRequestInfo()).isEmpty();

    // Verify request detail.
    assertThat(prepareResult.errorReason()).hasValue(ErrorReason.INVALID_REQUEST);
    assertThat(prepareResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(prepareResult.errorMessage().get()).contains("COMMAND_NOT_AVAILABLE");
  }

  @Test
  public void handleResultProcessing_passResult() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(10)
                    .setFailed(0)
                    .setModulesTotal(1)
                    .setModulesDone(1)
                    .build())
            .addModuleInfo(
                ReportProto.Module.newBuilder().setName("module1").setFailedTests(0).build())
            .build();
    request = request.toBuilder().setRetryPreviousSessionId("prev_session_id").build();
    mockProcessResult(result);

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    Path outputUploadPath = Path.of(outputFileUploadPath);
    verify(sessionResultHandlerUtil)
        .copyRetryFiles(
            eq(outputUploadPath.resolve("prev_session_id").resolve(commandId).toString()),
            eq(outputUploadPath.resolve("session_id").resolve(commandId).toString()));
    verify(sessionResultHandlerUtil)
        .generateScreenshotsMetadataFile(
            any(), eq(outputUploadPath.resolve("session_id").resolve(commandId)));

    // Verify command detail.
    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(10);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(10);
    assertThat(commandDetail.getFailedModuleCount()).isEqualTo(0);
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(handleResultProcessingResult.testContexts()).hasSize(1);
  }

  @Test
  public void handleResultProcessing_failResult() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(5)
                    .setFailed(5)
                    .setModulesTotal(1)
                    .setModulesDone(0)
                    .build())
            .addModuleInfo(
                ReportProto.Module.newBuilder()
                    .setName("module1")
                    .setFailedTests(5)
                    .setReason(ReportProto.Reason.newBuilder().setMsg("error message").build())
                    .build())
            .build();

    mockProcessResult(result);
    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(5);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(5);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(10);
    assertThat(commandDetail.getFailedModuleCount()).isEqualTo(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
  }

  @Test
  public void handleResultProcessing_failResult_allModulesDone_notCountedAsFailed()
      throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(5)
                    .setFailed(5)
                    .setModulesTotal(1)
                    .setModulesDone(1)
                    .build())
            .addModuleInfo(
                ReportProto.Module.newBuilder().setName("module1").setFailedTests(5).build())
            .build();

    mockProcessResult(result);
    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getFailedModuleCount()).isEqualTo(0);
  }

  @Test
  public void handleResultProcessing_failResult_oneModuleNotDone_countedAsFailed()
      throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(10)
                    .setFailed(0)
                    .setModulesTotal(1)
                    .setModulesDone(0)
                    .build())
            .addModuleInfo(
                ReportProto.Module.newBuilder()
                    .setName("module1")
                    .setFailedTests(0)
                    .setReason(ReportProto.Reason.newBuilder().setMsg("error message").build())
                    .build())
            .build();

    mockProcessResult(result);
    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getFailedModuleCount()).isEqualTo(1);
  }

  @Test
  public void handleResultProcessing_zeroTotalTest_treatAsError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);
    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(0);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void handleResultProcessing_zeroTotalTestAndOneModuleRan_treatAsCompleted()
      throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(0)
                    .setFailed(0)
                    .setModulesTotal(1)
                    .build())
            .build();
    mockProcessResult(result);
    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(0);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
  }

  @Test
  public void handleResultProcessing_zeroTotalTestWithFailedTest_setsError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);
    when(newTestResult.get()).thenReturn(ERROR_RESULT);
    when(testInfo.resultWithCause()).thenReturn(newTestResult);

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorMessage())
        .isEqualTo("test failed with exception [MH|UNDETERMINED|JOB_TIMEOUT|20103]");
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.OMNILAB_ERROR);
  }

  @Test
  public void handleResultProcessing_zeroTotalTestWithFailedJob_setsError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);
    when(newJobResult.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.ERROR,
                new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "job failed with exception")));
    when(jobInfo.resultWithCause()).thenReturn(newJobResult);

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorMessage())
        .isEqualTo("job failed with exception [MH|UNDETERMINED|JOB_TIMEOUT|20103]");
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.OMNILAB_ERROR);
  }

  @Test
  public void handleResultProcessing_noopCommandWithZeroResult_treatAsCompleted() throws Exception {
    commandInfo = commandInfo.toBuilder().setCommandLine("util/timewaster").build();
    properties.add(
        Job.XTS_COMMAND_ID,
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    request = request.toBuilder().clearCommands().addCommands(commandInfo).build();
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);
    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(0);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
  }

  @Test
  public void handleResultProcessing_getMalformedOutputURL_onlyCleanup() throws Exception {
    setUpPassingJobAndTestResults();
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    String malformedOutputFileUploadUrl = "malformed_URL";
    request =
        request.toBuilder()
            .setTestEnvironment(
                request.getTestEnvironment().toBuilder()
                    .setOutputFileUploadUrl(malformedOutputFileUploadUrl)
                    .build())
            .build();
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);
    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    when(files.getAll()).thenReturn(ImmutableMultimap.of());
    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    assertThat(handleResultProcessingResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(handleResultProcessingResult.errorReason().get())
        .isEqualTo(ErrorReason.RESULT_PROCESSING_ERROR);
    verify(sessionResultHandlerUtil, never())
        .processResult(any(), any(), any(), any(), any(), any());
  }

  @Test
  public void handleResultProcessing_processResultFailed_commandError() throws Exception {
    setUpPassingJobAndTestResults();
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    when(sessionInfo.getSessionProperty(SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH))
        .thenReturn(Optional.empty());
    MobileHarnessException mhException =
        new MobileHarnessException(BasicErrorId.LOCAL_FILE_IS_DIR, "Failed to find result file");
    doThrow(mhException)
        .when(sessionResultHandlerUtil)
        .processResult(any(), any(), any(), any(), any(), any());
    Mockito.doNothing().when(localFileUtil).prepareDir(any(Path.class));
    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());

    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    verify(sessionInfo)
        .putSessionProperty(
            SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH,
            outputFileUploadPath
                + "/session_id/olc_server_session_logs/olc_server_session_log.txt");
    assertThat(handleResultProcessingResult.commandDetails()).isNotEmpty();
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.RESULT_PROCESSING_ERROR);
    assertThat(commandDetail.getErrorMessage()).contains("Failed to find result file");
  }

  @Test
  public void handleResultProcessing_nonFileUrl_onlyCleanup() throws Exception {
    setUpPassingJobAndTestResults();
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    String malformedOutputFileUploadUrl = "https://www.google.com";
    request =
        request.toBuilder()
            .setTestEnvironment(
                request.getTestEnvironment().toBuilder()
                    .setOutputFileUploadUrl(malformedOutputFileUploadUrl)
                    .build())
            .build();
    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);
    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    var unused = newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    verify(sessionResultHandlerUtil, never())
        .processResult(any(), any(), any(), any(), any(), any());
  }

  @Test
  public void handleResultProcessing_tradefedError_errorStateWithErrorMessage() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);

    // Mock the content of the tradefed invocation runtime info log file.
    String tradefedInvocationErrorMessage = "example error message";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(0);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage()).isEqualTo(tradefedInvocationErrorMessage);
    assertThat(handleResultProcessingResult.errorReason())
        .hasValue(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(handleResultProcessingResult.errorMessage())
        .hasValue(
            NewMultiCommandRequestHandler.REQUEST_ERROR_MESSAGE_FOR_TRADEFED_INVOCATION_ERROR);
  }

  @Test
  public void handleResultProcessing_commandError_updateRequestError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);

    // Mock the content of the tradefed invocation runtime info log file.
    String tradefedInvocationErrorMessage = "example error message";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);
    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get())
            .setErrorReason(ErrorReason.UNKNOWN_REASON); // Request error reason is unknown
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.errorReason())
        .hasValue(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(handleResultProcessingResult.errorMessage())
        .hasValue(
            NewMultiCommandRequestHandler.REQUEST_ERROR_MESSAGE_FOR_TRADEFED_INVOCATION_ERROR);
  }

  @Test
  public void handleResultProcessing_multipleTradefedErrors_failWithCombinedErrorMessage()
      throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.getDefaultInstance())
            .build();
    mockProcessResult(result);

    // Mock the content of the tradefed invocation runtime info log file.
    String tradefedInvocationErrorMessage1 = "example error message 1";
    String tradefedInvocationErrorMessage2 = "example error message 2";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1),
                                "failed",
                                tradefedInvocationErrorMessage1)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage2)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    JobInfo jobInfo2 = Mockito.mock(JobInfo.class);
    TestInfo testInfo2 = Mockito.mock(TestInfo.class);
    Properties properties2 = new Properties(new Timing());
    properties2.add(Job.IS_XTS_TF_JOB, "true");
    properties2.add(
        Job.XTS_COMMAND_ID,
        UUID.nameUUIDFromBytes(DEFAULT_COMMAND_LINE.getBytes(UTF_8)).toString());
    when(jobInfo2.locator()).thenReturn(new JobLocator("job_id_2", "job_name_2"));
    when(jobInfo2.properties()).thenReturn(properties2);
    when(jobInfo2.files()).thenReturn(files);
    TestInfos testInfos2 = Mockito.mock(TestInfos.class);
    when(jobInfo2.tests()).thenReturn(testInfos2);
    when(testInfos2.getAll()).thenReturn(ImmutableListMultimap.of("test_id_2", testInfo2));
    when(jobInfo2.resultWithCause()).thenReturn(newJobResult);
    when(testInfo2.resultWithCause()).thenReturn(newTestResult);
    when(testInfo2.locator())
        .thenReturn(
            new TestLocator("test_id_2", "test_name_2", new JobLocator("job_id_2", "job_name_2")));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);
    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo, jobInfo2));
    doAnswer(
            invocation -> {
              Path path = invocation.getArgument(0, Path.class);
              LocalFileUtil localFileUtil = new LocalFileUtil();
              localFileUtil.prepareDir(path);
              Path resultFile = path.resolve("result.xml");
              java.nio.file.Files.createFile(resultFile);
              localFileUtil.zipDir(path.toString(), path + ".zip");
              return Optional.of(result);
            })
        .when(sessionResultHandlerUtil)
        .processResult(any(), any(), any(), any(), eq(ImmutableList.of(jobInfo, jobInfo2)), any());

    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage())
        .isEqualTo(tradefedInvocationErrorMessage1 + "\n" + tradefedInvocationErrorMessage2);
  }

  @Test
  public void handleResultProcessing_tradefedErrorAndResultProcessingError_prioritizeTradefedError()
      throws Exception {
    setUpPassingJobAndTestResults();
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // 1. Simulate result processing error
    MobileHarnessException resultProcessingException =
        new MobileHarnessException(BasicErrorId.USER_PLUGIN_ERROR, "Result processing failed");
    doThrow(resultProcessingException)
        .when(sessionResultHandlerUtil)
        .processResult(any(), any(), any(), any(), any(), any());

    // 2. Simulate tradefed invocation error
    String tradefedInvocationErrorMessage = "Tradefed invocation failed";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);
    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    // 3. Verify error prioritization and message combination
    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage()).contains(tradefedInvocationErrorMessage);
    assertThat(commandDetail.getErrorMessage()).contains("Result processing failed");
    assertThat(handleResultProcessingResult.errorReason())
        .hasValue(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(handleResultProcessingResult.errorMessage())
        .hasValue(
            NewMultiCommandRequestHandler.REQUEST_ERROR_MESSAGE_FOR_TRADEFED_INVOCATION_ERROR);
  }

  @Test
  public void handleResultProcessing_requestErrorExist_keepRequestError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);

    // Mock the content of the tradefed invocation runtime info log file.
    String tradefedInvocationErrorMessage = "example error message";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);
    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get())
            .setErrorReason(ErrorReason.INVALID_REQUEST) // Request error reason exists
            .setErrorMessage("Original request error");
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.errorReason()).hasValue(ErrorReason.INVALID_REQUEST);
    assertThat(handleResultProcessingResult.errorMessage()).hasValue("Original request error");
  }

  @Test
  public void handleResultProcessing_getTradefedInvocationLogDirError_commandError()
      throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result = ReportProto.Result.getDefaultInstance();
    mockProcessResult(result);
    when(sessionResultHandlerUtil.getTradefedInvocationLogDir(any(), any()))
        .thenThrow(
            new MobileHarnessException(BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND, "Dir not found"));

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.RESULT_PROCESSING_ERROR);
    assertThat(commandDetail.getErrorMessage())
        .isEqualTo("No valid test cases found in the result.");
  }

  @Test
  public void handleResultProcessing_catastrophicError_overrideModuleCount() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(1)
                    .setFailed(0)
                    .setModulesTotal(1)
                    .setModulesDone(1)
                    .build())
            .build();
    mockProcessResult(result);

    String tradefedInvocationErrorMessage =
        "com.android.tradefed.targetprep.TargetSetupError: Failed to connect";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));

    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage()).contains("TargetSetupError");
  }

  @Test
  public void
      handleResultProcessing_fatalTradefedError_deviceUnresponsiveException_overrideModuleCount()
          throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(1)
                    .setFailed(0)
                    .setModulesTotal(1)
                    .setModulesDone(1)
                    .build())
            .build();
    mockProcessResult(result);

    String tradefedInvocationErrorMessage =
        "com.android.tradefed.device.DeviceUnresponsiveException: Device 4B182JEBF04342 is online"
            + " but unresponsive";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));

    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage()).contains("DeviceUnresponsiveException");
  }

  @Test
  public void
      handleResultProcessing_fatalTradefedError_deviceNotAvailableException_overrideModuleCount()
          throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(1)
                    .setFailed(0)
                    .setModulesTotal(1)
                    .setModulesDone(1)
                    .build())
            .build();
    mockProcessResult(result);

    String tradefedInvocationErrorMessage =
        "com.android.tradefed.device.DeviceNotAvailableException: Could not find device fdf97de2";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));

    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage()).contains("DeviceNotAvailableException");
  }

  @Test
  public void handleResultProcessing_fatalTradefedError_deviceRuntimeException_overrideModuleCount()
      throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(1)
                    .setFailed(0)
                    .setModulesTotal(1)
                    .setModulesDone(1)
                    .build())
            .build();
    mockProcessResult(result);

    String tradefedInvocationErrorMessage =
        "com.android.tradefed.device.DeviceRuntimeException: 'adb -s 4B182JEBF04342 shell pm list"
            + " users' failed: 'adb: device 4B182JEBF04342 not found'";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));

    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage()).contains("DeviceRuntimeException");
  }

  @Test
  public void
      handleResultProcessing_cloudOrchestratorJitEmulatorDeviceNotAvailableWithCompletedModule_toleratedAsCompleted()
          throws Exception {
    setUpPassingJobAndTestResults();
    CommandInfo jitCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine(DEFAULT_COMMAND_LINE)
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("127.0.0.1:6520")
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("127.0.0.1:6521")
                    .build())
            .build();
    request = request.toBuilder().clearCommands().addCommands(jitCommandInfo).build();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(10)
                    .setFailed(0)
                    .setModulesTotal(2)
                    .setModulesDone(1)
                    .build())
            .build();
    mockProcessResult(result);

    String tradefedInvocationErrorMessage =
        "com.android.tradefed.device.DeviceNotAvailableException: Device 127.0.0.1:6520 not"
            + " available";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of("127.0.0.1:6520", "127.0.0.1:6521"),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));

    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.UNKNOWN_REASON);
    assertThat(commandDetail.getErrorMessage()).isEqualTo(tradefedInvocationErrorMessage);
    assertThat(commandDetail.getTotalModuleCount()).isEqualTo(2);
    assertThat(commandDetail.getFailedModuleCount()).isEqualTo(1);
    assertThat(handleResultProcessingResult.errorReason()).hasValue(ErrorReason.UNKNOWN_REASON);
  }

  @Test
  public void
      handleResultProcessing_cloudOrchestratorJitEmulatorDeviceNotAvailableWithZeroCompletedModules_errorState()
          throws Exception {
    setUpPassingJobAndTestResults();
    CommandInfo jitCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine(DEFAULT_COMMAND_LINE)
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("127.0.0.1:6520")
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("127.0.0.1:6521")
                    .build())
            .build();
    request = request.toBuilder().clearCommands().addCommands(jitCommandInfo).build();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(0)
                    .setFailed(0)
                    .setModulesTotal(2)
                    .setModulesDone(0)
                    .build())
            .build();
    mockProcessResult(result);

    String tradefedInvocationErrorMessage =
        "com.android.tradefed.device.DeviceNotAvailableException: Device 127.0.0.1:6520 not"
            + " available";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of("127.0.0.1:6520", "127.0.0.1:6521"),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));

    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage()).isEqualTo(tradefedInvocationErrorMessage);
  }

  @Test
  public void
      handleResultProcessing_cloudOrchestratorJitEmulatorTargetSetupErrorWithCompletedModule_errorState()
          throws Exception {
    setUpPassingJobAndTestResults();
    CommandInfo jitCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine(DEFAULT_COMMAND_LINE)
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("127.0.0.1:6520")
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("127.0.0.1:6521")
                    .build())
            .build();
    request = request.toBuilder().clearCommands().addCommands(jitCommandInfo).build();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(10)
                    .setFailed(0)
                    .setModulesTotal(2)
                    .setModulesDone(1)
                    .build())
            .build();
    mockProcessResult(result);

    String tradefedInvocationErrorMessage =
        "com.android.tradefed.targetprep.TargetSetupError: Failed to push file";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of("127.0.0.1:6520", "127.0.0.1:6521"),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));

    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage()).isEqualTo(tradefedInvocationErrorMessage);
  }

  @Test
  public void handleResultProcessing_generatesManifestFile() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(10).setFailed(0).build())
            .build();
    mockProcessResult(result);
    String logFile1 = "result.xsl";
    String logFile2 = "logs/olc_server_session_logs/log.txt";
    // Any existing manifest file with the name "FILES" won't be included in the new generated
    // manifest file:
    String logFile3 = "some-subdir/FILES";
    doAnswer(
            invocation -> {
              String outputDir = invocation.getArgument(0, String.class);
              return ImmutableList.of(
                  new File(outputDir + "/" + logFile1),
                  new File(outputDir + "/" + logFile2),
                  new File(outputDir + "/" + logFile3));
            })
        .when(localFileUtil)
        .listFiles(any(String.class), /* recursively= */ eq(true));

    HandleResultProcessingResult unused = createJobAndHandleResultProcessing(request);

    verify(localFileUtil).removeFileOrDir(endsWith(logFile3));
    verify(localFileUtil).writeToFile(isA(String.class), eq(logFile1 + "\n" + logFile2));
  }

  @Test
  public void cleanup_success() throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    assertThat(
            newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo).state())
        .isEqualTo(RequestState.RUNNING);

    // Verify that handler has mounted the zip file.
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);

    // Verify that handler has unmounted the zip file after calling cleanup().
    newMultiCommandRequestHandler.cleanup(sessionInfo);
    verifyUnmountRootDir(xtsRootDir);
  }

  @Test
  public void cleanup_noXtsZip_success() throws Exception {
    when(atsServerSessionUtil.isLocalMode()).thenReturn(true);
    doReturn("output")
        .when(localFileUtil)
        .unzipFile(anyString(), anyString(), any(Duration.class), any());
    doNothing().when(localFileUtil).removeFileOrDir(anyString());
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    assertThat(
            newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo).state())
        .isEqualTo(RequestState.RUNNING);

    // Verify that handler has mounted the zip file.
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    verify(localFileUtil)
        .unzipFile("/path/to/xts/zip/file.zip", xtsRootDir, Duration.ofHours(1), "");
    newMultiCommandRequestHandler.cleanup(sessionInfo);
    verify(localFileUtil).removeFileOrDir(xtsRootDir);
  }

  @Test
  public void cleanup_unmountAndRemoveAndroidXtsZipFailed_logWarningAndProceed() throws Exception {
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    when(commandExecutor.run(mountCommand)).thenReturn("COMMAND_OUTPUT");

    // Prepare session request info so that the xts zip file can be mounted.
    assertThat(
            newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo).state())
        .isEqualTo(RequestState.RUNNING);

    // Mock an exception to be thrown when running the unmount command.
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    when(commandExecutor.run(unmountCommand)).thenThrow(Mockito.mock(CommandException.class));

    // Mock an exception to be thrown when removing the xts root dir through local file util.
    doThrow(Mockito.mock(CommandException.class))
        .when(localFileUtil)
        .removeFileOrDir(eq(xtsRootDir));

    newMultiCommandRequestHandler.cleanup(sessionInfo);
  }

  @Test
  public void cleanup_cleanupJobGenDirsFailed_logWarningAndProceed() throws Exception {
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    when(commandExecutor.run(mountCommand)).thenReturn("COMMAND_OUTPUT");

    // Prepare session request info so that the xts zip file can be mounted.
    assertThat(
            newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo).state())
        .isEqualTo(RequestState.RUNNING);

    // Throw exception when running unmount command.
    MobileHarnessException mhException = Mockito.mock(CommandException.class);
    doThrow(mhException).when(sessionResultHandlerUtil).cleanUpJobGenDirs(any());
    newMultiCommandRequestHandler.cleanup(sessionInfo);
  }

  private void setUpPassingJobAndTestResults() {
    when(jobInfo.resultWithCause()).thenReturn(newJobResult);
    when(testInfo.resultWithCause()).thenReturn(newTestResult);
    when(testInfo.locator())
        .thenReturn(new TestLocator("test_id", "test_name", new JobLocator("job_id", "job_name")));
    when(newJobResult.get()).thenReturn(PASS_RESULT);
    when(newTestResult.get()).thenReturn(PASS_RESULT);
  }

  private void verifyUnmountRootDir(String xtsRootDir) throws Exception {
    // Verify that handler has unmounted the zip file after calling cleanup().
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(unmountCommand);
    verify(sleeper).sleep(Duration.ofSeconds(5));
  }

  private static Command restoreChunkedTestCasesCommand(String xtsRootDir) {
    return Command.of(
            xtsRootDir + "/android-cts/tools/cts_restorer",
            "--chunked-dir-path",
            xtsRootDir + "/android-cts/chunked-testcases",
            "--output-dir-path",
            xtsRootDir + "/android-cts/testcases")
        .timeout(Duration.ofHours(1));
  }

  private void mockProcessResult(ReportProto.Result result) throws Exception {
    doAnswer(
            invocation -> {
              Path path = invocation.getArgument(0, Path.class);
              LocalFileUtil localFileUtil = new LocalFileUtil();
              localFileUtil.prepareDir(path);
              Path resultFile = path.resolve("result.xml");
              java.nio.file.Files.createFile(resultFile);
              localFileUtil.zipDir(path.toString(), path + ".zip");
              return Optional.of(result);
            })
        .when(sessionResultHandlerUtil)
        .processResult(any(), any(), any(), any(), eq(ImmutableList.of(jobInfo)), any());
  }

  private HandleResultProcessingResult createJobAndHandleResultProcessing(
      NewMultiCommandRequest request) throws Exception {
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    PrepareSessionRequestResult prepareResult =
        newMultiCommandRequestHandler.prepareSessionRequestInfo(request, sessionInfo);
    assertThat(prepareResult.sessionRequestInfo()).isPresent();

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putCommandDetails(
                prepareResult.commandDetail().get().getId(), prepareResult.commandDetail().get());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    Path outputPath = Path.of(outputFileUploadPath + "/session_id/" + commandId);
    Path logPath = outputPath.resolve("logs");
    ArgumentCaptor<Path> pathCaptor1 = ArgumentCaptor.forClass(Path.class);
    verify(sessionResultHandlerUtil)
        .processResult(
            pathCaptor1.capture(),
            eq(logPath),
            eq(null),
            eq(null),
            eq(ImmutableList.of(jobInfo)),
            eq(prepareResult.sessionRequestInfo().get()));

    String path1 = pathCaptor1.getValue().toString();
    Instant now = Instant.now();
    DateTimeFormatter dateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu.MM.dd_HH.mm").withZone(ZoneId.systemDefault());
    String fileNamePrefix = dateTimeFormatter.format(now);
    if (!path1.startsWith(outputPath + "/" + fileNamePrefix)) {
      // The file name prefix should be the previous minute if it's not the current minute.
      // This can happen because the time used in the test code is a little later than the time used
      // in the production code.
      fileNamePrefix = dateTimeFormatter.format(now.minus(Duration.ofMinutes(1)));
    }
    assertThat(path1).startsWith(outputPath + "/" + fileNamePrefix);
    // Verify test context.
    TestContext testContext = handleResultProcessingResult.testContexts().get(commandId);
    assertThat(testContext.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(testContext.getTestResourceCount()).isEqualTo(1);
    TestResource testResource = testContext.getTestResource(0);
    assertThat(testResource.getName()).startsWith(fileNamePrefix);
    assertThat(testResource.getName()).endsWith(".zip");
    assertThat(testResource.getUrl())
        .startsWith("file://" + outputPath + "/" + testResource.getName());
    return handleResultProcessingResult;
  }

  @Test
  public void createSlateJobs_success() throws Exception {
    when(sessionRequestHandlerUtil.createJobGenDir(anyString())).thenReturn(Path.of("/tmp/gen"));
    when(sessionRequestHandlerUtil.createJobTmpDir(anyString())).thenReturn(Path.of("/tmp/tmp"));

    CommandInfo slateCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("slate --target team.module.task_id1")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .build();

    NewMultiCommandRequest slateRequest =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(slateCommandInfo)
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///path/to/slate_binary")
                    .setName("slate_binary")
                    .build())
            .build();

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createSlateJobs(slateRequest, sessionInfo);

    assertThat(createJobsResult.jobInfos()).hasSize(1);
    JobInfo slateJob = createJobsResult.jobInfos().get(0);
    assertThat(slateJob.params().get("target")).isEqualTo("team.module.task_id1");
  }

  @Test
  public void createSlateJobs_multipleTargets() throws Exception {
    when(sessionRequestHandlerUtil.createJobGenDir(anyString())).thenReturn(Path.of("/tmp/gen"));
    when(sessionRequestHandlerUtil.createJobTmpDir(anyString())).thenReturn(Path.of("/tmp/tmp"));

    CommandInfo slateCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("slate --target target1 target2 target3")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .build();

    NewMultiCommandRequest slateRequest =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(slateCommandInfo)
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///path/to/slate_binary")
                    .setName("slate_binary")
                    .build())
            .build();

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createSlateJobs(slateRequest, sessionInfo);

    assertThat(createJobsResult.jobInfos()).hasSize(1);
    JobInfo slateJob = createJobsResult.jobInfos().get(0);
    assertThat(slateJob.params().get("target")).isEqualTo("target1,target2,target3");
  }

  @Test
  public void createSlateJobs_multipleTargets_shortFlag() throws Exception {
    when(sessionRequestHandlerUtil.createJobGenDir(anyString())).thenReturn(Path.of("/tmp/gen"));
    when(sessionRequestHandlerUtil.createJobTmpDir(anyString())).thenReturn(Path.of("/tmp/tmp"));

    CommandInfo slateCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("slate -t target1 target2")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .build();

    NewMultiCommandRequest slateRequest =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(slateCommandInfo)
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///path/to/slate_binary")
                    .setName("slate_binary")
                    .build())
            .build();

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createSlateJobs(slateRequest, sessionInfo);

    assertThat(createJobsResult.jobInfos()).hasSize(1);
    JobInfo slateJob = createJobsResult.jobInfos().get(0);
    assertThat(slateJob.params().get("target")).isEqualTo("target1,target2");
  }

  @Test
  public void createSlateJobs_targetEquals() throws Exception {
    when(sessionRequestHandlerUtil.createJobGenDir(anyString())).thenReturn(Path.of("/tmp/gen"));
    when(sessionRequestHandlerUtil.createJobTmpDir(anyString())).thenReturn(Path.of("/tmp/tmp"));

    CommandInfo slateCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("slate --target=targetEquals1")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .build();

    NewMultiCommandRequest slateRequest =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(slateCommandInfo)
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///path/to/slate_binary")
                    .setName("slate_binary")
                    .build())
            .build();

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createSlateJobs(slateRequest, sessionInfo);

    assertThat(createJobsResult.jobInfos()).hasSize(1);
    JobInfo slateJob = createJobsResult.jobInfos().get(0);
    assertThat(slateJob.params().get("target")).isEqualTo("targetEquals1");
  }

  @Test
  public void createSlateJobs_targetEqualsShort() throws Exception {
    when(sessionRequestHandlerUtil.createJobGenDir(anyString())).thenReturn(Path.of("/tmp/gen"));
    when(sessionRequestHandlerUtil.createJobTmpDir(anyString())).thenReturn(Path.of("/tmp/tmp"));

    CommandInfo slateCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("slate -t=targetEqualsShort1")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .build();

    NewMultiCommandRequest slateRequest =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(slateCommandInfo)
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///path/to/slate_binary")
                    .setName("slate_binary")
                    .build())
            .build();

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createSlateJobs(slateRequest, sessionInfo);

    assertThat(createJobsResult.jobInfos()).hasSize(1);
    JobInfo slateJob = createJobsResult.jobInfos().get(0);
    assertThat(slateJob.params().get("target")).isEqualTo("targetEqualsShort1");
  }

  @Test
  public void createSlateJobs_multipleDevices() throws Exception {
    when(sessionRequestHandlerUtil.createJobGenDir(anyString())).thenReturn(Path.of("/tmp/gen"));
    when(sessionRequestHandlerUtil.createJobTmpDir(anyString())).thenReturn(Path.of("/tmp/tmp"));

    CommandInfo slateCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("slate --target team.module.task_id1")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_2)
                    .build())
            .build();

    NewMultiCommandRequest slateRequest =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(slateCommandInfo)
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///path/to/slate_binary")
                    .setName("slate_binary")
                    .build())
            .build();

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createSlateJobs(slateRequest, sessionInfo);

    assertThat(createJobsResult.jobInfos()).hasSize(1);
    JobInfo slateJob = createJobsResult.jobInfos().get(0);
    assertThat(slateJob.subDeviceSpecs().getAllSubDevices()).hasSize(2);
  }

  @Test
  public void createSlateJobs_noDevices_errorResult() throws Exception {
    CommandInfo slateCommandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("slate --target team.module.task_id1")
            .build();

    NewMultiCommandRequest slateRequest =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(slateCommandInfo)
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///path/to/slate_binary")
                    .setName("slate_binary")
                    .build())
            .build();

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createSlateJobs(slateRequest, sessionInfo);

    assertThat(createJobsResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(createJobsResult.errorReason()).hasValue(ErrorReason.INVALID_REQUEST);
  }

  @Test
  public void handleNonTradefedJobEnd_nonTradefedJob_preparesLogDirNameAndCallsResultHandler()
      throws Exception {
    properties.add(Job.IS_XTS_NON_TF_JOB, "true");
    when(testInfo.locator())
        .thenReturn(new TestLocator("test_id", "test_name", new JobLocator("job_id", "job_name")));
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();

    newMultiCommandRequestHandler.handleNonTradefedJobEnd(jobInfo, requestDetail);

    assertThat(requestDetail.getNonTradefedLogDirNamesList()).isNotEmpty();
    verify(sessionResultHandlerUtil).handleNonTradefedJobEnd(jobInfo);
  }

  @Test
  public void handleNonTradefedJobEnd_notNonTradefedJob_doesNothing() throws Exception {
    properties.add(Job.IS_XTS_NON_TF_JOB, "false");
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();

    newMultiCommandRequestHandler.handleNonTradefedJobEnd(jobInfo, requestDetail);

    assertThat(requestDetail.getNonTradefedLogDirNamesList()).isEmpty();
    verify(sessionResultHandlerUtil, never()).handleNonTradefedJobEnd(jobInfo);
  }

  @Test
  public void handleNonTradefedJobEnd_preconditionJob_doesNothing() throws Exception {
    properties.add(Job.IS_XTS_NON_TF_JOB, "true");
    properties.add(XtsConstants.XTS_JOB_NAME, XtsConstants.SETUP_JOB_NAME);
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();

    newMultiCommandRequestHandler.handleNonTradefedJobEnd(jobInfo, requestDetail);

    assertThat(requestDetail.getNonTradefedLogDirNamesList()).isEmpty();
    verify(sessionResultHandlerUtil, never()).handleNonTradefedJobEnd(jobInfo);
  }

  @Test
  public void createOutputManifestFile_preservesFilesEndingWithFilesAndRemovesOldManifest()
      throws Exception {
    File outputDir = tmpFolder.newFolder("output_dir");
    Path outputDirPath = outputDir.toPath();

    File regularFile = new File(outputDir, "test.log");
    localFileUtil.writeToFile(regularFile.getAbsolutePath(), "log content");

    File fileEndingWithFiles = new File(outputDir, "TEST_FILES");
    localFileUtil.writeToFile(fileEndingWithFiles.getAbsolutePath(), "test files content");

    File subDir = new File(outputDir, "sub_dir");
    localFileUtil.prepareDir(subDir.getAbsolutePath());

    File subFileEndingWithFiles = new File(subDir, "DATA_FILES");
    localFileUtil.writeToFile(subFileEndingWithFiles.getAbsolutePath(), "data files content");

    File subFileNamedFiles = new File(subDir, OUTPUT_MANIFEST_FILE_NAME);
    localFileUtil.writeToFile(subFileNamedFiles.getAbsolutePath(), "sub files content");

    File oldManifestFile = new File(outputDir, OUTPUT_MANIFEST_FILE_NAME);
    localFileUtil.writeToFile(oldManifestFile.getAbsolutePath(), "stale_entry.txt");

    newMultiCommandRequestHandler.createOutputManifestFile(outputDirPath);

    assertThat(localFileUtil.isFileExist(regularFile.getAbsolutePath())).isTrue();
    assertThat(localFileUtil.isFileExist(fileEndingWithFiles.getAbsolutePath())).isTrue();
    assertThat(localFileUtil.isFileExist(subFileEndingWithFiles.getAbsolutePath())).isTrue();
    assertThat(localFileUtil.isFileExist(subFileNamedFiles.getAbsolutePath())).isFalse();

    assertThat(localFileUtil.isFileExist(oldManifestFile.getAbsolutePath())).isTrue();
    String manifestContent = localFileUtil.readFile(oldManifestFile.getAbsolutePath());
    assertThat(manifestContent.split("\n"))
        .asList()
        .containsExactly("test.log", "TEST_FILES", "sub_dir/DATA_FILES");
    assertThat(manifestContent.split("\n")).asList().doesNotContain(OUTPUT_MANIFEST_FILE_NAME);
    assertThat(manifestContent.split("\n")).asList().doesNotContain("sub_dir/FILES");
    assertThat(manifestContent.split("\n")).asList().doesNotContain("stale_entry.txt");
  }

  @Test
  public void createOutputManifestFile_emptyDirectory_createsEmptyManifest() throws Exception {
    File outputDir = tmpFolder.newFolder("empty_output_dir");
    Path outputDirPath = outputDir.toPath();

    newMultiCommandRequestHandler.createOutputManifestFile(outputDirPath);

    File manifestFile = new File(outputDir, OUTPUT_MANIFEST_FILE_NAME);
    assertThat(localFileUtil.isFileExist(manifestFile.getAbsolutePath())).isTrue();
    assertThat(localFileUtil.readFile(manifestFile.getAbsolutePath())).isEmpty();
  }
}
