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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.devtools.mobileharness.shared.util.command.LineCallback.does;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.truth.Correspondence;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
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
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import com.google.protobuf.FieldMask;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OlcServerIntegrationTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Rule
  public TestWatcher testWatcher =
      new TestWatcher() {

        @Override
        protected void starting(Description description) {
          // Prints test name to help debug server output in test logs.
          logger.atInfo().log(
              "\n========================================\n"
                  + "Starting test: %s\n"
                  + "========================================\n",
              description.getDisplayName());
        }

        @Override
        protected void failed(Throwable e, Description description) {
          // Adds server stdout/stderr to a failed test.
          Exception serverOutput =
              new IllegalStateException(
                  String.format(
                      "\nolc_server_stdout:\n"
                          + "=====BEGIN===================================\n"
                          + "%s\n"
                          + "=====END=====================================\n"
                          + "olc_server_stderr:\n"
                          + "=====BEGIN===================================\n"
                          + "%s\n"
                          + "=====END=====================================\n"
                          + "lab_server_stdout:\n"
                          + "=====BEGIN===================================\n"
                          + "%s\n"
                          + "=====END=====================================\n"
                          + "lab_server_stderr:\n"
                          + "=====BEGIN===================================\n"
                          + "%s\n"
                          + "=====END=====================================\n",
                      olcServerStdoutBuilder,
                      olcServerStderrBuilder,
                      labServerStdoutBuilder,
                      labServerStderrBuilder));
          serverOutput.setStackTrace(new StackTraceElement[0]);
          e.addSuppressed(serverOutput);
        }
      };

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

  private StringBuilder olcServerStdoutBuilder;
  private StringBuilder olcServerStderrBuilder;

  private StringBuilder labServerStdoutBuilder;
  private StringBuilder labServerStderrBuilder;

  private int olcServerPort;
  private ManagedChannel olcServerChannel;
  private CommandProcess olcServerProcess;

  private CommandProcess labServerProcess;

  @Bind private MasterGrpcStubHelper masterGrpcStubHelper;
  @Bind private Clock clock;

  @Inject private LabInfoGrpcStub labInfoGrpcStub;

  @Before
  public void setUp() throws Exception {
    clock = Clock.systemUTC();

    olcServerStdoutBuilder = new StringBuilder();
    olcServerStderrBuilder = new StringBuilder();

    labServerStdoutBuilder = new StringBuilder();
    labServerStderrBuilder = new StringBuilder();

    olcServerPort = PortProber.pickUnusedPort();
    olcServerChannel = ChannelFactory.createLocalChannel(olcServerPort, directExecutor());
    masterGrpcStubHelper = new MasterGrpcStubHelper(olcServerChannel);

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
    startServers(false);

    // Checks the server version.
    VersionStub versionStub = new VersionStub(olcServerChannel);
    assertThat(versionStub.getVersion())
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
            SessionPluginForTestingConfig.newBuilder().setNoOpDriverSleepTimeSec(2).build());
    CreateSessionResponse createSessionResponse = sessionStub.createSession(createSessionRequest);
    SessionId sessionId = createSessionResponse.getSessionId();

    // Verifies the server cannot be killed.
    KillServerResponse killServerResponse = controlStub.killServer();
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
    assertThat(getSessionResponse).comparingExpectedFieldsOnly().isEqualTo(GET_SESSION_RESPONSE);

    List<SessionDetail> allSessions =
        sessionStub
            .getAllSessions(GetAllSessionsRequest.getDefaultInstance())
            .getSessionDetailList();
    assertThat(allSessions).containsExactly(getSessionResponse.getSessionDetail());

    // Verifies the server is killed.
    assertThat(olcServerProcess.isAlive()).isTrue();
    killServerResponse = controlStub.killServer();
    assertThat(killServerResponse)
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            KillServerResponse.newBuilder().setSuccess(Success.getDefaultInstance()).build());
    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(6L));
    assertThat(olcServerProcess.isAlive()).isFalse();

    String olcServerStderr = olcServerStderrBuilder.toString();

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
  }

  @Test
  public void noOpTest_atsMode() throws Exception {
    startServers(true);

    // Checks the server version.
    VersionStub versionStub = new VersionStub(olcServerChannel);
    assertThat(versionStub.getVersion())
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
        .containsExactly("NoOpDevice-0");

    // Creates a session.
    SessionStub sessionStub = new SessionStub(olcServerChannel);
    String fakeJobFilePath = tmpFolder.newFile().getAbsolutePath();
    CreateSessionRequest createSessionRequest =
        createCreateSessionRequest(
            SessionPluginForTestingConfig.newBuilder()
                .setNoOpDriverSleepTimeSec(2)
                .putExtraJobFiles("fake_job_file_tag", fakeJobFilePath)
                .build());
    CreateSessionResponse createSessionResponse = sessionStub.createSession(createSessionRequest);
    SessionId sessionId = createSessionResponse.getSessionId();

    // Waits until the session finishes.
    GetSessionResponse getSessionResponse =
        waitUntilSessionFinish(sessionStub, sessionId, Duration.ofMinutes(1L));

    // Checks the session output.
    assertThat(getSessionResponse).comparingExpectedFieldsOnly().isEqualTo(GET_SESSION_RESPONSE);

    String olcServerStderr = olcServerStderrBuilder.toString();
    String labServerStderr = labServerStderrBuilder.toString();

    // Verifies the driver has run.
    assertWithMessage("lab server stderr").that(labServerStderr).contains("Sleep for 2 seconds");

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
  }

  private void startServers(boolean enableAtsMode)
      throws IOException, CommandStartException, InterruptedException {
    CommandExecutor commandExecutor = new CommandExecutor();

    // Starts the OLC server.
    CountDownLatch olcServerStartedLatch = new CountDownLatch(1);
    AtomicBoolean olcServerStartedSuccessfully = new AtomicBoolean();
    CountDownLatch olcServerLocalDeviceFound = new CountDownLatch(1);
    CountDownLatch olcServerRemoteDeviceFound = new CountDownLatch(1);

    Command olcServerCommand =
        Command.of(
                new SystemUtil()
                    .getJavaCommandCreator()
                    .createJavaCommand(
                        enableAtsMode ? OLC_SERVER_FILE_PATH : OLC_SERVER_WITH_LOCAL_MODE_FILE_PATH,
                        ImmutableList.of(
                            "--detect_adb_device=false",
                            "--enable_ats_mode=" + enableAtsMode,
                            "--enable_client_experiment_manager=false",
                            "--enable_client_file_transfer=false",
                            "--enable_grpc_lab_server=true",
                            "--external_adb_initializer_template=true",
                            "--log_file_size_no_limit=true",
                            "--no_op_device_num=1",
                            "--olc_server_port=" + olcServerPort,
                            "--public_dir=" + tmpFolder.newFolder("olc_server_public_dir"),
                            "--simplified_log_format=true"),
                        ImmutableList.of()))
            .onStdout(
                does(
                    stdout -> {
                      System.out.printf("olc_server_stdout %s\n", stdout);
                      olcServerStdoutBuilder.append(stdout).append('\n');
                    }))
            .onStderr(
                does(
                    stderr -> {
                      System.err.printf("olc_server_stderr %s\n", stderr);
                      olcServerStderrBuilder.append(stderr).append('\n');

                      if (stderr.contains("OLC server started")) {
                        olcServerStartedSuccessfully.set(true);
                        olcServerStartedLatch.countDown();
                      } else if (stderr.contains("New device NoOpDevice-0")) {
                        olcServerLocalDeviceFound.countDown();
                      } else if (stderr.contains("Sign up lab")
                          && stderr.contains("NoOpDevice-0")) {
                        olcServerRemoteDeviceFound.countDown();
                      }
                    }))
            .onExit(result -> olcServerStartedLatch.countDown())
            .redirectStderr(false)
            .needStdoutInResult(false)
            .needStderrInResult(false);
    logger.atInfo().log("Starting OLC server, command=%s", olcServerCommand);
    olcServerProcess = commandExecutor.start(olcServerCommand);

    // Waits until the server starts successfully.
    assertWithMessage("The OLC server has not started in 15 seconds")
        .that(olcServerStartedLatch.await(15L, SECONDS))
        .isTrue();
    assertWithMessage("The OLC server does not start successfully")
        .that(olcServerStartedSuccessfully.get())
        .isTrue();

    // Starts the lab server.
    if (enableAtsMode) {
      CountDownLatch labServerStartedOrFailedToStart = new CountDownLatch(1);
      AtomicBoolean labServerStartedSuccessfully = new AtomicBoolean();
      CountDownLatch labServerLocalDeviceFound = new CountDownLatch(1);

      int labServerGrpcPort = PortProber.pickUnusedPort();
      int labServerRpcPort = PortProber.pickUnusedPort();
      int labServerSocketPort = PortProber.pickUnusedPort();

      Command labServerCommand =
          Command.of(
                  new SystemUtil()
                      .getJavaCommandCreator()
                      .createJavaCommand(
                          LAB_SERVER_FILE_PATH,
                          ImmutableList.of(
                              "--detect_adb_device=false",
                              "--enable_api_config=false",
                              "--enable_cloud_logging=false",
                              "--enable_device_config_manager=false",
                              "--enable_external_master_server=true",
                              "--enable_file_cleaner=false",
                              "--enable_stubby_rpc_server=false",
                              "--enable_trace_span_processor=false",
                              "--external_adb_initializer_template=true",
                              "--grpc_port=" + labServerGrpcPort,
                              "--log_file_size_no_limit=true",
                              "--master_grpc_target=localhost:" + olcServerPort,
                              "--no_op_device_num=1",
                              "--public_dir=" + tmpFolder.newFolder("lab_server_public_dir"),
                              "--rpc_port=" + labServerRpcPort,
                              "--serv_via_cloud_rpc=false",
                              "--simplified_log_format=true",
                              "--socket_port=" + labServerSocketPort,
                              "--tmp_dir_root=" + tmpFolder.newFolder("lab_server_tmp_dir")),
                          ImmutableList.of()))
              .onStdout(
                  does(
                      stdout -> {
                        System.out.printf("lab_server_stdout %s\n", stdout);
                        labServerStdoutBuilder.append(stdout).append('\n');
                      }))
              .onStderr(
                  does(
                      stderr -> {
                        System.err.printf("lab_server_stderr %s\n", stderr);
                        labServerStderrBuilder.append(stderr).append('\n');

                        if (stderr.contains("Lab server successfully started")) {
                          labServerStartedSuccessfully.set(true);
                          labServerStartedOrFailedToStart.countDown();
                        } else if (stderr.contains("New device NoOpDevice-0")) {
                          labServerLocalDeviceFound.countDown();
                        }
                      }))
              .onExit(result -> labServerStartedOrFailedToStart.countDown())
              .redirectStderr(false)
              .needStdoutInResult(false)
              .needStderrInResult(false);

      logger.atInfo().log("Starting lab server server, command=%s", labServerCommand);
      labServerProcess = commandExecutor.start(labServerCommand);

      // Waits until lab server starts and detects devices successfully.
      assertWithMessage("Lab server didn't start in 60 seconds")
          .that(labServerStartedOrFailedToStart.await(60L, SECONDS))
          .isTrue();
      assertWithMessage("Lab server didn't start successfully")
          .that(labServerStartedSuccessfully.get())
          .isTrue();
      assertWithMessage("Lab server didn't detect devices in 15 seconds")
          .that(labServerLocalDeviceFound.await(15L, SECONDS))
          .isTrue();
    }

    if (enableAtsMode) {
      // Verifies the remote device manager receives device signup successfully.
      assertWithMessage("The remote device manager has not received device signup in 15 seconds")
          .that(olcServerRemoteDeviceFound.await(15L, SECONDS))
          .isTrue();
    } else {
      // Verifies the local device manager starts successfully.
      assertWithMessage("The local device manager has not started in 15 seconds")
          .that(olcServerLocalDeviceFound.await(15L, SECONDS))
          .isTrue();
    }
  }

  private static GetSessionResponse waitUntilSessionFinish(
      SessionStub sessionStub, SessionId sessionId, Duration timeout)
      throws GrpcExceptionWithErrorId, InterruptedException {
    GetSessionResponse getSessionResponse;
    Instant startTime = Instant.now();
    do {
      Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1L));
      getSessionResponse =
          sessionStub.getSession(
              GetSessionRequest.newBuilder()
                  .setSessionId(sessionId)
                  .setFieldMask(FieldMask.newBuilder().addPaths("session_detail.session_status"))
                  .build());
    } while (!getSessionResponse
            .getSessionDetail()
            .getSessionStatus()
            .equals(SessionStatus.SESSION_FINISHED)
        && Instant.now().isBefore(startTime.plus(timeout)));
    return sessionStub.getSession(GetSessionRequest.newBuilder().setSessionId(sessionId).build());
  }

  private static CreateSessionRequest createCreateSessionRequest(
      SessionPluginForTestingConfig sessionPluginConfig) {
    return CreateSessionRequest.newBuilder()
        .setSessionConfig(
            SessionConfig.newBuilder()
                .setSessionName("session_with_no_op_test")
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
}
