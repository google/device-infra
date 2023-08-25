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
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.shared.util.port.PortProber;
import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
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
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.protobuf.Any;
import com.google.protobuf.FieldMask;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OlcServerIntegrationTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  private static final String SERVER_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness"
              + "/infra/client/longrunningservice/OlcServerForTesting_deploy.jar");

  private CommandProcess serverProcess;

  @After
  public void tearDown() {
    if (serverProcess != null) {
      serverProcess.kill();
    }
  }

  @Test
  public void noOpTest() throws Exception {
    int serverPort = PortProber.pickUnusedPort();
    String serverPublicDirPath = tmpFolder.newFolder("olc_server_public_dir").toString();

    StringBuilder serverStdoutBuilder = new StringBuilder();
    StringBuilder serverStderrBuilder = new StringBuilder();
    StringBuilder serverLogBuilder = new StringBuilder();

    CountDownLatch serverStartedLatch = new CountDownLatch(1);
    AtomicBoolean serverStartedSuccessfully = new AtomicBoolean();
    CountDownLatch deviceFound = new CountDownLatch(1);

    try {
      // Starts the server.
      Command serverCommand =
          Command.of(
                  new SystemUtil()
                      .getJavaCommandCreator()
                      .createJavaCommand(
                          SERVER_FILE_PATH,
                          ImmutableList.of(
                              "--olc_server_port=" + serverPort,
                              "--no_op_device_num=1",
                              "--detect_adb_device=false",
                              "--public_dir=" + serverPublicDirPath),
                          ImmutableList.of()))
              .onStdout(
                  does(
                      stdout -> {
                        System.out.printf("server_stdout %s\n", stdout);
                        serverStdoutBuilder.append(stdout).append('\n');
                      }))
              .onStderr(
                  does(
                      stderr -> {
                        System.err.printf("server_stderr %s\n", stderr);
                        serverStderrBuilder.append(stderr).append('\n');

                        if (stderr.contains("OLC server started")) {
                          serverStartedSuccessfully.set(true);
                          serverStartedLatch.countDown();
                        } else if (stderr.contains("New device NoOpDevice-0")) {
                          deviceFound.countDown();
                        }
                      }))
              .onExit(result -> serverStartedLatch.countDown())
              .redirectStderr(false)
              .needStdoutInResult(false)
              .needStderrInResult(false);
      logger.atInfo().log("Starting server, command=%s", serverCommand);
      serverProcess = new CommandExecutor().start(serverCommand);

      // Waits until the server starts successfully.
      assertWithMessage("The server has not started in 15 seconds")
          .that(serverStartedLatch.await(15L, SECONDS))
          .isTrue();
      assertWithMessage("The server does not start successfully")
          .that(serverStartedSuccessfully.get())
          .isTrue();

      // Verifies the local device manager starts successfully.
      assertWithMessage("The local device manager has not started in 5 seconds")
          .that(deviceFound.await(15L, SECONDS))
          .isTrue();

      ManagedChannel channel = ChannelFactory.createLocalChannel(serverPort, directExecutor());

      // Checks the server version.
      VersionStub versionStub = new VersionStub(channel);
      assertThat(versionStub.getVersion())
          .isEqualTo(
              GetVersionResponse.newBuilder()
                  .setLabVersion(Version.LAB_VERSION.toString())
                  .build());

      // Gets the server log.
      ControlStub controlStub = new ControlStub(channel);
      StreamObserver<GetLogRequest> requestObserver =
          controlStub.getLog(
              new StreamObserver<>() {
                @Override
                public void onNext(GetLogResponse response) {
                  response.getLogRecords().getLogRecordList().stream()
                      .map(LogRecord::getFormattedLogRecord)
                      .forEach(serverLogBuilder::append);
                }

                @Override
                public void onError(Throwable e) {
                  logger.atWarning().withCause(e).log("Failed to get log from server");
                }

                @Override
                public void onCompleted() {
                  logger.atInfo().log("Completed to get log from server");
                }
              });
      requestObserver.onNext(GetLogRequest.newBuilder().setEnable(true).build());

      // Creates a session.
      SessionStub sessionStub = new SessionStub(channel);
      String pluginClassName =
          "com.google.devtools.mobileharness.infra.client.longrunningservice"
              + ".SessionPluginForTesting";
      CreateSessionRequest createSessionRequest =
          CreateSessionRequest.newBuilder()
              .setSessionConfig(
                  SessionConfig.newBuilder()
                      .setSessionName("session_with_no_op_test")
                      .setSessionPluginConfigs(
                          SessionPluginConfigs.newBuilder()
                              .addSessionPluginConfig(
                                  SessionPluginConfig.newBuilder()
                                      .setLoadingConfig(
                                          SessionPluginLoadingConfig.newBuilder()
                                              .setPluginClassName(pluginClassName))
                                      .setExecutionConfig(
                                          SessionPluginExecutionConfig.newBuilder()
                                              .setConfig(
                                                  Any.pack(
                                                      SessionPluginForTestingConfig.newBuilder()
                                                          .setNoOpDriverSleepTimeSec(2)
                                                          .build()))
                                              .build()))))
              .build();
      CreateSessionResponse createSessionResponse = sessionStub.createSession(createSessionRequest);
      SessionId sessionId = createSessionResponse.getSessionId();

      // Verifies the server cannot be killed.
      KillServerResponse killServerResponse = controlStub.killServer();
      assertThat(killServerResponse)
          .isEqualTo(KillServerResponse.newBuilder().setSuccessful(false).build());

      // Waits until the session finishes.
      GetSessionResponse getSessionResponse;
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
          .equals(SessionStatus.SESSION_FINISHED));

      // Checks the session output.
      getSessionResponse =
          sessionStub.getSession(GetSessionRequest.newBuilder().setSessionId(sessionId).build());
      assertThat(getSessionResponse)
          .comparingExpectedFieldsOnly()
          .isEqualTo(
              GetSessionResponse.newBuilder()
                  .setSessionDetail(
                      SessionDetail.newBuilder()
                          .setSessionOutput(
                              SessionOutput.newBuilder()
                                  .putSessionProperty("job_result", "PASS")
                                  .putSessionProperty("job_result_from_job_event", "PASS")
                                  .putSessionPluginOutput(
                                      pluginClassName,
                                      SessionPluginOutput.newBuilder()
                                          .setOutput(
                                              Any.pack(
                                                  SessionPluginForTestingOutput.newBuilder()
                                                      .setJobResultTypeName("PASS")
                                                      .build()))
                                          .build())))
                  .build());

      List<SessionDetail> allSessions =
          sessionStub
              .getAllSessions(GetAllSessionsRequest.getDefaultInstance())
              .getSessionDetailList();
      assertThat(allSessions).containsExactly(getSessionResponse.getSessionDetail());

      // Verifies the server is killed.
      assertThat(serverProcess.isAlive()).isTrue();
      killServerResponse = controlStub.killServer();
      assertThat(killServerResponse)
          .isEqualTo(KillServerResponse.newBuilder().setSuccessful(true).build());
      Sleeper.defaultSleeper().sleep(Duration.ofSeconds(6L));
      assertThat(serverProcess.isAlive()).isFalse();
    } catch (
        @SuppressWarnings("InterruptedExceptionSwallowed")
        Throwable e) {
      e.addSuppressed(
          new IllegalStateException(
              String.format(
                  "server_stdout=[%s], server_stderr=[%s]",
                  serverStdoutBuilder, serverStderrBuilder)));
      throw e;
    }

    String serverStderr = serverStderrBuilder.toString();

    // Verifies the driver has run.
    assertWithMessage("server stderr").that(serverStderr).contains("Sleep for 2 seconds");

    // Checks warnings in log.
    assertWithMessage(
            "A successful test run should not print exception stack traces, which will confuse"
                + " users and affect debuggability when debugging a failed one.\n"
                + "server stderr")
        .that(serverStderr)
        .doesNotContain("\tat ");

    // Checks the server log.
    String serverLog = serverLogBuilder.toString();
    assertWithMessage("server log").that(serverLog).contains("Sleep for 2 seconds");
  }
}
