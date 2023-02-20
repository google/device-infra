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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.devtools.mobileharness.shared.util.command.LineCallback.does;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.shared.util.port.PortProber;
import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.BuiltinSessionPlugin;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ChannelFactory;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.protobuf.FieldMask;
import io.grpc.ManagedChannel;
import java.time.Duration;
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

    CountDownLatch serverStartedLatch = new CountDownLatch(1);
    AtomicBoolean serverStartedSuccessfully = new AtomicBoolean();

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
                        }
                      }))
              .onExit(result -> serverStartedLatch.countDown())
              .redirectStderr(false)
              .needStdoutInResult(false)
              .needStderrInResult(false);
      logger.atInfo().log("Starting server, command=%s", serverCommand);
      serverProcess = new CommandExecutor().start(serverCommand);

      // Waits until the server starts successfully.
      assertWithMessage("The server has not started in 10 seconds")
          .that(serverStartedLatch.await(10L, SECONDS))
          .isTrue();
      assertWithMessage("The server does not start successfully")
          .that(serverStartedSuccessfully.get())
          .isTrue();

      ManagedChannel channel = ChannelFactory.createLocalChannel(serverPort);

      // Checks the server version.
      VersionStub versionStub = new VersionStub(channel);
      assertThat(versionStub.getVersion())
          .isEqualTo(
              GetVersionResponse.newBuilder()
                  .setLabVersion(Version.LAB_VERSION.toString())
                  .build());

      SessionStub sessionStub = new SessionStub(channel);

      // Creates a session.
      CreateSessionRequest createSessionRequest =
          CreateSessionRequest.newBuilder()
              .setSessionConfig(
                  SessionConfig.newBuilder()
                      .setSessionName("session_with_no_op_test")
                      .setSessionPluginConfig(
                          SessionPluginConfig.newBuilder()
                              .addBuiltinPlugin(
                                  BuiltinSessionPlugin.newBuilder()
                                      .setLoadingConfig(
                                          SessionPluginLoadingConfig.newBuilder()
                                              .setPluginClassName(
                                                  "com.google.devtools.mobileharness.infra.client."
                                                      + "longrunningservice."
                                                      + "SessionPluginForTesting")))))
              .build();
      CreateSessionResponse createSessionResponse = sessionStub.createSession(createSessionRequest);
      SessionId sessionId = createSessionResponse.getSessionId();

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
                                  .putSessionProperty("job_result_from_job_event", "PASS")))
                  .build());
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
    assertWithMessage("server log").that(serverStderr).contains("Sleep for 2 seconds");

    // Checks warnings in log.
    assertWithMessage(
            "A successful test run should not print exception stack traces, which will confuse"
                + " users and affect debuggability when debugging a failed one.\n"
                + "server log")
        .that(serverStderr)
        .doesNotContain("\tat ");
  }
}
