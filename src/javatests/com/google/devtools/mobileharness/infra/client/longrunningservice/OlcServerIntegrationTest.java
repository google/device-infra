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

package com.google.devtools.mobileharness.infra.client.longrunningservice;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerLogs.SERVER_STARTED_SIGNAL;
import static com.google.devtools.mobileharness.shared.util.command.LineCallback.does;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.truth.Correspondence;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.Failure;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.Success;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionPluginForTestingProto.SessionPluginForTestingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionPluginForTestingProto.SessionPluginForTestingOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfigs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetAllSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SubscribeSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SubscribeSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.devtools.mobileharness.shared.util.base.CountDownSet;
import com.google.devtools.mobileharness.shared.util.comm.relay.client.ClientCreator;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.Destination;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.ServiceLocator;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.MonitoredStringBuilders;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionRequest;
import com.google.devtools.mobileharness.shared.version.rpc.stub.grpc.VersionGrpcStub;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OlcServerIntegrationTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final PrintTestName printTestName = new PrintTestName();
  @Rule public final MonitoredStringBuilders stringBuilders = new MonitoredStringBuilders();

  private static final String OLC_SERVER_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/client/"
              + "longrunningservice/olc_server_for_testing_deploy.jar");
  private static final String OLC_SERVER_WITH_LOCAL_MODE_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/client/"
              + "longrunningservice/olc_server_for_testing_local_mode_deploy.jar");
  private static final String LAB_SERVER_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/infra/lab/lab_server_oss_deploy.jar");
  private static final String API_CONFIG_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/client/"
              + "longrunningservice/api_config.textproto");

  private static final String PLUGIN_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.client.longrunningservice.SessionPluginForTesting";
  private static final GetSessionResponse GET_SESSION_RESPONSE =
      GetSessionResponse.newBuilder()
          .setSessionDetail(
              SessionDetail.newBuilder()
                  .setSessionOutput(
                      SessionOutput.newBuilder()
                          .putSessionProperty("job_result", "PASS")
                          .putSessionProperty("job_result_from_job_event", "PASS")
                          .putSessionPluginOutput(
                              PLUGIN_CLASS_NAME,
                              SessionPluginOutput.newBuilder()
                                  .setOutput(
                                      Any.pack(
                                          SessionPluginForTestingOutput.newBuilder()
                                              .setJobResultTypeName("PASS")
                                              .build()))
                                  .build())))
          .build();

  private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("NoOpDevice-\\d+");

  private final CommandExecutor commandExecutor = new CommandExecutor();
  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  private int olcServerPort;
  private int atsWorkerGrpcPort;
  private ManagedChannel olcServerChannel;
  private CommandProcess olcServerProcess;

  private CommandProcess labServerProcess;

  private Path sessionLogFile;

  @Bind private MasterGrpcStubHelper masterGrpcStubHelper;
  @Bind private final Clock clock = Clock.systemUTC();

  @Inject private LabInfoGrpcStub labInfoGrpcStub;

  @Before
  public void setUp() throws Exception {
    olcServerPort = PortProber.pickUnusedPort();
    atsWorkerGrpcPort = PortProber.pickUnusedPort();
    olcServerChannel = ChannelFactory.createLocalChannel(olcServerPort, directExecutor());
    masterGrpcStubHelper = new MasterGrpcStubHelper(olcServerChannel);

    sessionLogFile =
        tmpFolder
            .newFolder("olc_server_session_log")
            .toPath()
            .resolve("olc_server_session_log.txt");

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @After
  public void tearDown() {
    if (olcServerProcess != null) {
      olcServerProcess.kill();
    }
    if (labServerProcess != null) {
      labServerProcess.kill();
    }
  }

  @Test
  public void noOpTest_localMode() throws Exception {
    String deviceControlId = "NoOpDevice-4";
    startServers(/* enableAtsMode= */ false, /* enableSchedulerShuffle= */ false);

    // Checks the server version.
    VersionStub versionStub = new VersionStub(olcServerChannel);
    assertThat(versionStub.getVersion())
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            GetVersionResponse.newBuilder().setLabVersion(Version.LAB_VERSION.toString()).build());

    // Gets the server log.
    OlcServerLogCollector olcServerLogCollector = new OlcServerLogCollector();
    ControlStub controlStub = new ControlStub(olcServerChannel);
    StreamObserver<GetLogRequest> requestObserver = controlStub.getLog(olcServerLogCollector);
    requestObserver.onNext(GetLogRequest.newBuilder().setEnable(true).build());

    // Creates a session.
    SessionStub sessionStub = new SessionStub(olcServerChannel);
    CreateSessionRequest createSessionRequest =
        createCreateSessionRequest(
            SessionPluginForTestingConfig.newBuilder()
                .setNoOpDriverSleepTimeSec(2)
                .putJobDeviceDimensions("control_id", deviceControlId)
                .putJobDeviceDimensions("fake_dimension_name", "fake_dimension_value")
                .build());
    CreateSessionResponse createSessionResponse = sessionStub.createSession(createSessionRequest);
    SessionId sessionId = createSessionResponse.getSessionId();

    // Verifies the server cannot be killed.
    KillServerResponse killServerResponse =
        controlStub.killServer(KillServerRequest.getDefaultInstance());
    assertThat(killServerResponse)
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            KillServerResponse.newBuilder()
                .setFailure(
                    Failure.newBuilder()
                        .addUnfinishedSessions(
                            SessionDetail.newBuilder()
                                .setSessionId(sessionId)
                                .setSessionConfig(
                                    SessionConfig.newBuilder()
                                        .setSessionName("session_with_no_op_test"))))
                .build());

    // Waits until the session finishes.
    GetSessionResponse getSessionResponse =
        waitUntilSessionFinish(sessionStub, sessionId, Duration.ofMinutes(1L));

    // Checks the session output.
    assertThat(getSessionResponse)
        .comparingExpectedFieldsOnly()
        .ignoringExtraRepeatedFieldElements()
        .isEqualTo(createGetSessionResponse(deviceControlId));

    List<SessionDetail> allSessions =
        sessionStub
            .getAllSessions(GetAllSessionsRequest.getDefaultInstance())
            .getSessionDetailList();
    assertThat(allSessions).containsExactly(getSessionResponse.getSessionDetail());

    // Verifies the server is killed.
    assertThat(olcServerProcess.isAlive()).isTrue();
    killServerResponse = controlStub.killServer(KillServerRequest.getDefaultInstance());
    assertThat(killServerResponse)
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            KillServerResponse.newBuilder().setSuccess(Success.getDefaultInstance()).build());
    // Avoid the stacktrace of StatusRuntimeException due to inappropriate close of the steam.
    requestObserver.onCompleted();
    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(6L));
    assertThat(olcServerProcess.isAlive()).isFalse();

    String olcServerStderr = stringBuilders.getOrCreate("olc_server_stderr").toString();

    // Verifies the driver has run.
    assertWithMessage("OLC server stderr").that(olcServerStderr).contains("Sleep for 2 seconds");

    // Checks warnings in log.
    assertWithMessage(
            "A successful test run should not print exception stack traces, which will confuse"
                + " users and affect debuggability when debugging a failed one.\n"
                + "OLC server stderr")
        .that(olcServerStderr)
        .doesNotContain("\tat ");

    // Checks the OLC server log.
    String olcServerLog = olcServerLogCollector.getLog();
    assertWithMessage("server log").that(olcServerLog).contains("Sleep for 2 seconds");

    // Checks the session log.
    String sessionLog = localFileUtil.readFile(sessionLogFile);
    assertThat(sessionLog).contains("Starting session runner " + sessionId.getId());
    assertThat(sessionLog).contains("Session finished, session_id=" + sessionId.getId());
  }

  @Test
  public void noOpTest_atsMode() throws Exception {
    String deviceControlId = "NoOpDevice-3";
    startServers(/* enableAtsMode= */ true, /* enableSchedulerShuffle= */ true);

    // Checks the server version.
    VersionStub versionStub = new VersionStub(olcServerChannel);
    assertThat(versionStub.getVersion())
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            GetVersionResponse.newBuilder().setLabVersion(Version.LAB_VERSION.toString()).build());

    // Checks the lab info service.
    GetLabInfoResponse getLabInfoResponse =
        labInfoGrpcStub.getLabInfo(GetLabInfoRequest.getDefaultInstance());
    List<LabData> labDataList =
        getLabInfoResponse.getLabQueryResult().getLabView().getLabDataList();
    assertWithMessage("getLabInfoResponse=[%s]", getLabInfoResponse).that(labDataList).hasSize(1);
    assertWithMessage("getLabInfoResponse=[%s]", getLabInfoResponse)
        .that(labDataList.get(0).getDeviceList().getDeviceInfoList())
        .comparingElementsUsing(
            Correspondence.from(
                (DeviceInfo deviceInfo, String uuidSuffix) ->
                    requireNonNull(deviceInfo)
                        .getDeviceLocator()
                        .getId()
                        .endsWith(requireNonNull(uuidSuffix)),
                "has a device UUID ending with"))
        .containsExactly(
            "NoOpDevice-0", "NoOpDevice-1", "NoOpDevice-2", "NoOpDevice-3", "NoOpDevice-4");

    // Creates a session.
    SessionStub sessionStub = new SessionStub(olcServerChannel);
    String fakeJobFilePath = tmpFolder.newFile().getAbsolutePath();
    CreateSessionRequest createSessionRequest =
        createCreateSessionRequest(
            SessionPluginForTestingConfig.newBuilder()
                .setNoOpDriverSleepTimeSec(2)
                .putExtraJobFiles("fake_job_file_tag", fakeJobFilePath)
                .putJobDeviceDimensions("control_id", deviceControlId)
                .putJobDeviceDimensions("fake_dimension_name", "fake_dimension_value")
                .putJobDeviceDimensions(
                    "fake_required_dimension_name", "fake_required_dimension_value")
                .build());
    CreateSessionResponse createSessionResponse = sessionStub.createSession(createSessionRequest);
    SessionId sessionId = createSessionResponse.getSessionId();

    // Waits until the session finishes.
    GetSessionResponse getSessionResponse =
        waitUntilSessionFinish(sessionStub, sessionId, Duration.ofMinutes(1L));

    // Checks the session output.
    assertThat(getSessionResponse)
        .comparingExpectedFieldsOnly()
        .ignoringExtraRepeatedFieldElements()
        .isEqualTo(createGetSessionResponse(deviceControlId));

    String olcServerStderr = stringBuilders.getOrCreate("olc_server_stderr").toString();
    String labServerStderr = stringBuilders.getOrCreate("lab_server_stderr").toString();

    // Verifies the driver has run.
    assertWithMessage("lab server stderr").that(labServerStderr).contains("Sleep for 2 seconds");

    // Verifies the allocation is correct.
    assertWithMessage("olc server stderr")
        .that(olcServerStderr)
        .containsMatch("Allocated devices.*" + deviceControlId);

    // Verifies job/test files have been transferred.
    assertWithMessage("lab server stderr")
        .that(labServerStderr)
        .contains(
            String.format(
                "Job/test files were handled, job_files={%s=[%s]}, test_files={}",
                "fake_job_file_tag", fakeJobFilePath));

    // Checks warnings in log.
    String errorMessagePrefix =
        "A successful test run should not print exception stack traces, which will confuse"
            + " users and affect debuggability when debugging a failed one.\n\n";
    assertWithMessage(errorMessagePrefix + "OLC server stderr")
        .that(olcServerStderr)
        .doesNotContain("\tat ");
    assertWithMessage(errorMessagePrefix + "lab server stderr")
        .that(labServerStderr)
        .doesNotContain("\tat ");

    // Checks the session log.
    String sessionLog = localFileUtil.readFile(sessionLogFile);
    assertThat(sessionLog).contains("Starting session runner " + sessionId.getId());
    assertThat(sessionLog).contains("Session finished, session_id=" + sessionId.getId());

    // Verifies the server is killed.
    ControlStub controlStub = new ControlStub(olcServerChannel);
    assertThat(olcServerProcess.isAlive()).isTrue();
    KillServerResponse killServerResponse =
        controlStub.killServer(KillServerRequest.getDefaultInstance());
    assertThat(killServerResponse)
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            KillServerResponse.newBuilder().setSuccess(Success.getDefaultInstance()).build());
    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(6L));
    assertThat(olcServerProcess.isAlive()).isFalse();
  }

  @Test
  public void noOpTest_failedToAllocate_atsMode() throws Exception {
    startServers(/* enableAtsMode= */ true, /* enableSchedulerShuffle= */ true);

    // Checks the server version.
    VersionStub versionStub = new VersionStub(olcServerChannel);
    assertThat(versionStub.getVersion())
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            GetVersionResponse.newBuilder().setLabVersion(Version.LAB_VERSION.toString()).build());

    // Checks the lab info service.
    GetLabInfoResponse getLabInfoResponse =
        labInfoGrpcStub.getLabInfo(GetLabInfoRequest.getDefaultInstance());
    List<LabData> labDataList =
        getLabInfoResponse.getLabQueryResult().getLabView().getLabDataList();
    assertWithMessage("getLabInfoResponse=[%s]", getLabInfoResponse).that(labDataList).hasSize(1);
    assertWithMessage("getLabInfoResponse=[%s]", getLabInfoResponse)
        .that(labDataList.get(0).getDeviceList().getDeviceInfoList())
        .comparingElementsUsing(
            Correspondence.from(
                (DeviceInfo deviceInfo, String uuidSuffix) ->
                    requireNonNull(deviceInfo)
                        .getDeviceLocator()
                        .getId()
                        .endsWith(requireNonNull(uuidSuffix)),
                "has a device UUID ending with"))
        .containsExactly(
            "NoOpDevice-0", "NoOpDevice-1", "NoOpDevice-2", "NoOpDevice-3", "NoOpDevice-4");

    // Creates a session.
    SessionStub sessionStub = new SessionStub(olcServerChannel);
    String fakeJobFilePath = tmpFolder.newFile().getAbsolutePath();
    CreateSessionRequest createSessionRequest =
        createCreateSessionRequest(
            SessionPluginForTestingConfig.newBuilder()
                .setNoOpDriverSleepTimeSec(2)
                .putExtraJobFiles("fake_job_file_tag", fakeJobFilePath)
                .putJobDeviceDimensions("fake_dimension_name", "fake_dimension_value")
                .setStartTimeoutSec(5)
                .build());
    CreateSessionResponse createSessionResponse = sessionStub.createSession(createSessionRequest);
    SessionId sessionId = createSessionResponse.getSessionId();

    // Waits until the session finishes.
    GetSessionResponse getSessionResponse =
        waitUntilSessionFinish(sessionStub, sessionId, Duration.ofSeconds(8L));

    // Checks the session output.
    assertThat(getSessionResponse)
        .comparingExpectedFieldsOnly()
        .ignoringExtraRepeatedFieldElements()
        .isEqualTo(createGetSessionResponseWithError());

    String olcServerStderr = stringBuilders.getOrCreate("olc_server_stderr").toString();
    // Verifies allocation timeout.
    assertWithMessage("olc server stderr")
        .that(olcServerStderr)
        .containsMatch("Timeout because no device is allocated after start_timeout 5 seconds.");

    // Verifies the required dimension is not satisfied.
    assertWithMessage("olc server stderr")
        .that(olcServerStderr)
        .containsMatch(
            "Job does not satisfy the required dimensions "
                + "of any NoOpDevice"
                + " \\{fake_required_dimension_name=\\[fake_required_dimension_value\\]\\}.");

    // Checks the session log.
    String sessionLog = localFileUtil.readFile(sessionLogFile);
    assertThat(sessionLog).contains("Starting session runner " + sessionId.getId());
    assertThat(sessionLog).contains("Session finished, session_id=" + sessionId.getId());

    // Verifies the server is killed.
    ControlStub controlStub = new ControlStub(olcServerChannel);
    assertThat(olcServerProcess.isAlive()).isTrue();
    KillServerResponse killServerResponse =
        controlStub.killServer(KillServerRequest.getDefaultInstance());
    assertThat(killServerResponse)
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            KillServerResponse.newBuilder().setSuccess(Success.getDefaultInstance()).build());
    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(6L));
    assertThat(olcServerProcess.isAlive()).isFalse();
  }

  @Test
  public void grpcRelay() throws Exception {
    startServers(/* enableAtsMode= */ true, /* enableSchedulerShuffle= */ true);

    // Gets LabLocator.
    GetLabInfoResponse getLabInfoResponse =
        labInfoGrpcStub.getLabInfo(GetLabInfoRequest.getDefaultInstance());
    LabLocator labLocator =
        getLabInfoResponse
            .getLabQueryResult()
            .getLabView()
            .getLabDataList()
            .get(0)
            .getLabInfo()
            .getLabLocator();

    // Creates relay channel.
    Channel relayChannel =
        ClientCreator.createChannel(
            olcServerChannel,
            Destination.newBuilder()
                .setServiceLocator(
                    ServiceLocator.newBuilder()
                        .setLabLocator(
                            DestinationProto.LabLocator.newBuilder().setLabLocator(labLocator)))
                .build());
    VersionGrpcStub versionStub = new VersionGrpcStub(relayChannel);

    // Gets lab server version.
    VersionServiceProto.GetVersionResponse getVersionResponse =
        versionStub.getVersion(GetVersionRequest.getDefaultInstance());

    assertThat(getVersionResponse)
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            VersionServiceProto.GetVersionResponse.newBuilder()
                .setVersion(Version.LAB_VERSION.toString())
                .build());
  }

  private void startServers(boolean enableAtsMode, boolean enableSchedulerShuffle)
      throws IOException, CommandStartException, InterruptedException, ExecutionException {
    int noOpDeviceNum = 5;

    // Starts the OLC server.
    ImmutableList<String> deviceIds =
        IntStream.range(0, noOpDeviceNum)
            .boxed()
            .map(i -> "NoOpDevice-" + i)
            .collect(toImmutableList());
    CountDownSet<String> olcServerUndetectedLocalDevices = new CountDownSet<>(deviceIds);
    CountDownSet<String> olcServerUnreportedRemoteDevices = new CountDownSet<>(deviceIds);

    StringBuilder olcServerStdout = stringBuilders.getOrCreate("olc_server_stdout");
    StringBuilder olcServerStderr = stringBuilders.getOrCreate("olc_server_stderr");
    Command olcServerCommand =
        Command.of(
                new SystemUtil()
                    .getJavaCommandCreator()
                    .createJavaCommand(
                        enableAtsMode ? OLC_SERVER_FILE_PATH : OLC_SERVER_WITH_LOCAL_MODE_FILE_PATH,
                        ImmutableList.of(
                            "--adb_dont_kill_server=true",
                            "--android_device_daemon=false",
                            "--api_config=" + API_CONFIG_FILE_PATH,
                            "--clear_android_device_multi_users=false",
                            "--detect_adb_device=false",
                            "--disable_calling=false",
                            "--disable_device_reboot=true",
                            "--disable_wifi_util_func=true",
                            "--enable_android_device_ready_check=false",
                            "--enable_ats_mode=" + enableAtsMode,
                            "--enable_client_experiment_manager=false",
                            "--enable_client_file_transfer=false",
                            "--enable_device_state_change_recover=false",
                            "--enable_device_system_settings_change=false",
                            "--enable_grpc_relay=" + enableAtsMode,
                            "--enable_grpc_lab_server=true",
                            "--enable_simple_scheduler_shuffle=" + enableSchedulerShuffle,
                            "--external_adb_initializer_template=true",
                            "--mute_android=false",
                            "--no_op_device_num=" + noOpDeviceNum,
                            "--olc_server_port=" + olcServerPort,
                            "--ats_worker_grpc_port=" + atsWorkerGrpcPort,
                            "--public_dir=" + tmpFolder.newFolder("olc_server_public_dir"),
                            "--resource_dir_name=olc_server_res_files",
                            "--set_test_harness_property=false",
                            "--tmp_dir_root=" + tmpFolder.newFolder("olc_server_tmp_dir")),
                        ImmutableList.of()))
            .onStdout(
                does(
                    stdout -> {
                      System.out.printf("olc_server_stdout %s\n", stdout);
                      olcServerStdout.append(stdout).append('\n');
                    }))
            .onStderr(
                does(
                    stderr -> {
                      System.err.printf("olc_server_stderr %s\n", stderr);
                      olcServerStderr.append(stderr).append('\n');

                      if (stderr.contains("New device NoOpDevice-")) {
                        findAllDeviceIds(stderr).forEach(olcServerUndetectedLocalDevices::remove);
                      } else if (stderr.contains("Sign up lab") && stderr.contains("NoOpDevice-")) {
                        findAllDeviceIds(stderr).forEach(olcServerUnreportedRemoteDevices::remove);
                      }
                    }))
            .successfulStartCondition(line -> line.contains(SERVER_STARTED_SIGNAL))
            .redirectStderr(false)
            .needStdoutInResult(false)
            .needStderrInResult(false);
    logger.atInfo().log("Starting OLC server, command=%s", olcServerCommand);
    olcServerProcess = commandExecutor.start(olcServerCommand);

    // Waits until the server starts successfully.
    try {
      assertWithMessage("The OLC server does not start successfully")
          .that(olcServerProcess.successfulStartFuture().get(15L, SECONDS))
          .isTrue();
    } catch (TimeoutException e) {
      throw new AssertionError("The OLC server has not started in 15 seconds", e);
    }

    // Starts the lab server.
    if (enableAtsMode) {
      CountDownSet<String> labServerUndetectedLocalDevices = new CountDownSet<>(deviceIds);

      int labServerGrpcPort = PortProber.pickUnusedPort();
      int labServerRpcPort = PortProber.pickUnusedPort();
      int labServerSocketPort = PortProber.pickUnusedPort();

      StringBuilder labServerStdout = stringBuilders.getOrCreate("lab_server_stdout");
      StringBuilder labServerStderr = stringBuilders.getOrCreate("lab_server_stderr");
      Command labServerCommand =
          Command.of(
                  new SystemUtil()
                      .getJavaCommandCreator()
                      .createJavaCommand(
                          LAB_SERVER_FILE_PATH,
                          ImmutableList.of(
                              "--adb_dont_kill_server=true",
                              "--android_device_daemon=false",
                              "--api_config=" + API_CONFIG_FILE_PATH,
                              "--clear_android_device_multi_users=false",
                              "--detect_adb_device=false",
                              "--disable_calling=false",
                              "--disable_device_reboot_for_ro_properties=true",
                              "--disable_wifi_util_func=true",
                              "--enable_android_device_ready_check=false",
                              "--enable_cloud_logging=false",
                              "--enable_device_state_change_recover=false",
                              "--enable_device_system_settings_change=false",
                              "--enable_external_master_server=true",
                              "--enable_file_cleaner=false",
                              "--enable_stubby_rpc_server=false",
                              "--enable_trace_span_processor=false",
                              "--external_adb_initializer_template=true",
                              "--grpc_port=" + labServerGrpcPort,
                              "--master_grpc_target=localhost:" + atsWorkerGrpcPort,
                              "--mute_android=false",
                              "--no_op_device_num=" + noOpDeviceNum,
                              "--public_dir=" + tmpFolder.newFolder("lab_server_public_dir"),
                              "--resource_dir_name=lab_server_res_files",
                              "--rpc_port=" + labServerRpcPort,
                              "--serv_via_cloud_rpc=false",
                              "--set_test_harness_property=false",
                              "--socket_port=" + labServerSocketPort,
                              "--tmp_dir_root=" + tmpFolder.newFolder("lab_server_tmp_dir")),
                          ImmutableList.of()))
              .onStdout(
                  does(
                      stdout -> {
                        System.out.printf("lab_server_stdout %s\n", stdout);
                        labServerStdout.append(stdout).append('\n');
                      }))
              .onStderr(
                  does(
                      stderr -> {
                        System.err.printf("lab_server_stderr %s\n", stderr);
                        labServerStderr.append(stderr).append('\n');

                        if (stderr.contains("New device NoOpDevice-")) {
                          findAllDeviceIds(stderr).forEach(labServerUndetectedLocalDevices::remove);
                        }
                      }))
              .successfulStartCondition(line -> line.contains("Lab server successfully started"))
              .redirectStderr(false)
              .needStdoutInResult(false)
              .needStderrInResult(false);

      logger.atInfo().log("Starting lab server server, command=%s", labServerCommand);
      labServerProcess = commandExecutor.start(labServerCommand);

      // Waits until lab server starts and detects devices successfully.
      try {
        assertWithMessage("Lab server didn't start successfully")
            .that(labServerProcess.successfulStartFuture().get(60L, SECONDS))
            .isTrue();
      } catch (TimeoutException e) {
        throw new AssertionError("Lab server didn't start in 60 seconds", e);
      }
      assertWithMessage("Lab server didn't detect all devices in 15 seconds\nundetected devices")
          .that(labServerUndetectedLocalDevices.await(Duration.ofSeconds(15L)))
          .isEmpty();
    }

    if (enableAtsMode) {
      // Verifies the remote device manager receives device signup successfully.
      assertWithMessage(
              "The remote device manager has not received all device signups in 15 seconds\n"
                  + "unreported devices")
          .that(olcServerUnreportedRemoteDevices.await(Duration.ofSeconds(15L)))
          .isEmpty();
    } else {
      // Verifies the local device manager starts successfully.
      assertWithMessage(
              "The local device manager has not detected all devices in 15 seconds\n"
                  + "undetected devices")
          .that(olcServerUndetectedLocalDevices.await(Duration.ofSeconds(15L)))
          .isEmpty();
    }
  }

  private static GetSessionResponse waitUntilSessionFinish(
      SessionStub sessionStub, SessionId sessionId, Duration timeout)
      throws ExecutionException, TimeoutException, InterruptedException {
    SubscribeSessionResponseObserver subscribeSessionResponseObserver =
        new SubscribeSessionResponseObserver();
    StreamObserver<SubscribeSessionRequest> subscribeSessionRequestObserver =
        sessionStub.subscribeSession(subscribeSessionResponseObserver);
    subscribeSessionRequestObserver.onNext(
        SubscribeSessionRequest.newBuilder()
            .setGetSessionRequest(GetSessionRequest.newBuilder().setSessionId(sessionId))
            .build());
    ListenableFuture<GetSessionResponse> resultFuture =
        subscribeSessionResponseObserver.getResultFuture();
    return resultFuture.get(timeout.toMillis(), MILLISECONDS);
  }

  private static class SubscribeSessionResponseObserver
      implements StreamObserver<SubscribeSessionResponse> {

    private final SettableFuture<GetSessionResponse> resultFuture = SettableFuture.create();

    private ListenableFuture<GetSessionResponse> getResultFuture() {
      return resultFuture;
    }

    @Override
    public void onNext(SubscribeSessionResponse value) {
      if (value.getGetSessionResponse().getSessionDetail().getSessionStatus()
          == SessionStatus.SESSION_FINISHED) {
        resultFuture.set(value.getGetSessionResponse());
      }
    }

    @Override
    public void onError(Throwable t) {
      resultFuture.setException(t);
    }

    @Override
    public void onCompleted() {
      resultFuture.setException(
          new IllegalStateException("Session subscriber closed without a finished session"));
    }
  }

  private CreateSessionRequest createCreateSessionRequest(
      SessionPluginForTestingConfig sessionPluginConfig) {
    return CreateSessionRequest.newBuilder()
        .setSessionConfig(
            SessionConfig.newBuilder()
                .setSessionName("session_with_no_op_test")
                .putSessionProperty(
                    SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH,
                    sessionLogFile.toString())
                .setSessionPluginConfigs(
                    SessionPluginConfigs.newBuilder()
                        .addSessionPluginConfig(
                            SessionPluginConfig.newBuilder()
                                .setLoadingConfig(
                                    SessionPluginLoadingConfig.newBuilder()
                                        .setPluginClassName(PLUGIN_CLASS_NAME))
                                .setExecutionConfig(
                                    SessionPluginExecutionConfig.newBuilder()
                                        .setConfig(Any.pack(sessionPluginConfig))
                                        .build()))))
        .build();
  }

  private static GetSessionResponse createGetSessionResponse(String deviceControlId) {
    GetSessionResponse.Builder builder = GET_SESSION_RESPONSE.toBuilder();
    builder
        .getSessionDetailBuilder()
        .getSessionOutputBuilder()
        .putSessionProperty("allocated_device_control_id", deviceControlId);
    return builder.build();
  }

  private static GetSessionResponse createGetSessionResponseWithError() {
    return GetSessionResponse.newBuilder()
        .setSessionDetail(
            SessionDetail.newBuilder()
                .setSessionOutput(
                    SessionOutput.newBuilder()
                        .putSessionProperty("job_result", "ERROR")
                        .putSessionProperty("job_result_from_job_event", "ERROR")
                        .putSessionPluginOutput(
                            PLUGIN_CLASS_NAME,
                            SessionPluginOutput.newBuilder()
                                .setOutput(
                                    Any.pack(
                                        SessionPluginForTestingOutput.newBuilder()
                                            .setJobResultTypeName("ERROR")
                                            .build()))
                                .build())))
        .build();
  }

  private static class OlcServerLogCollector implements StreamObserver<GetLogResponse> {

    @GuardedBy("itself")
    private final StringBuilder logStringBuilder = new StringBuilder();

    private String getLog() {
      synchronized (logStringBuilder) {
        return logStringBuilder.toString();
      }
    }

    @Override
    public void onNext(GetLogResponse response) {
      synchronized (logStringBuilder) {
        for (LogRecord logRecord : response.getLogRecords().getLogRecordList()) {
          logStringBuilder.append(logRecord.getFormattedLogRecord());
        }
      }
    }

    @Override
    public void onError(Throwable e) {
      logger.atWarning().withCause(e).log("Failed to get log from server");
    }

    @Override
    public void onCompleted() {
      logger.atInfo().log("Completed to get log from server");
    }
  }

  private static ImmutableSet<String> findAllDeviceIds(String string) {
    ImmutableSet.Builder<String> result = ImmutableSet.builder();
    Matcher matcher = DEVICE_ID_PATTERN.matcher(string);
    while (matcher.find()) {
      result.add(matcher.group());
    }
    return result.build();
  }
}
