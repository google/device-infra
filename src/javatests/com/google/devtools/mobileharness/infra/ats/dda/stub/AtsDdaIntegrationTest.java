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

package com.google.devtools.mobileharness.infra.ats.dda.stub;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.devtools.mobileharness.shared.util.command.LineCallback.does;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.infra.ats.dda.stub.AtsDdaStub.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
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
public class AtsDdaIntegrationTest {

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
          // Adds debug info to a failed test.
          Exception debugInfo =
              new IllegalStateException(
                  String.format(
                      "debug info:\n"
                          + "session_info=%s\n"
                          + "session_error=[%s]\n"
                          + "olc_server_stdout:\n"
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
                      sessionInfo,
                      Optional.ofNullable(sessionInfo)
                          .flatMap(SessionInfo::sessionError)
                          .map(Throwables::getStackTraceAsString)
                          .orElse("null"),
                      olcServerStdoutBuilder,
                      olcServerStderrBuilder,
                      labServerStdoutBuilder,
                      labServerStderrBuilder));
          debugInfo.setStackTrace(new StackTraceElement[0]);
          e.addSuppressed(debugInfo);
        }
      };

  private static final String OLC_SERVER_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/"
              + "infra/ats/common/olcserver/ats_olc_server_deploy.jar");
  private static final String LAB_SERVER_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/infra/lab/lab_server_oss_deploy.jar");

  private final CommandExecutor commandExecutor = new CommandExecutor();
  private final SystemUtil systemUtil = new SystemUtil();

  private final StringBuilder olcServerStdoutBuilder = new StringBuilder();
  private final StringBuilder olcServerStderrBuilder = new StringBuilder();
  private final StringBuilder labServerStdoutBuilder = new StringBuilder();
  private final StringBuilder labServerStderrBuilder = new StringBuilder();

  private final CountDownLatch driverStarted = new CountDownLatch(1);
  private final CountDownLatch driverWokeUp = new CountDownLatch(1);

  private SessionInfo sessionInfo;

  private int olcServerPort;
  private ManagedChannel olcServerChannel;
  private AtsDdaStub atsDdaStub;

  private CommandProcess olcServerProcess;
  private CommandProcess labServerProcess;

  @Before
  public void setUp() throws Exception {
    olcServerPort = PortProber.pickUnusedPort();
    olcServerChannel = ChannelFactory.createLocalChannel(olcServerPort, directExecutor());
    atsDdaStub = new AtsDdaStub(olcServerChannel);
  }

  @After
  public void tearDown() {
    if (olcServerProcess != null) {
      olcServerProcess.kill();
    }
    if (labServerProcess != null) {
      labServerProcess.kill();
    }
    if (olcServerChannel != null) {
      olcServerChannel.shutdown();
    }
  }

  @Test
  public void run() throws Exception {
    startServers();

    // Creates session.
    String sessionId =
        atsDdaStub.createSession(
            "fake_session",
            ImmutableMap.of(
                "model", "pixel", "sdk_version", "24", "control_id", "AndroidRealDevice-2"),
            Duration.ofHours(1L));

    // Gets session info.
    for (int i = 0; i < 30; i++) {
      sessionInfo = atsDdaStub.getSession(sessionId);
      if (sessionInfo.allocatedDevice().isPresent()
          || sessionInfo.sessionStatus() == SessionStatus.SESSION_FINISHED) {
        break;
      }
      Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1L));
    }

    // Verifies the session status and the allocated device.
    assertThat(sessionInfo.sessionStatus()).isEqualTo(SessionStatus.SESSION_RUNNING);
    assertThat(sessionInfo.allocatedDevice().orElseThrow())
        .comparingExpectedFieldsOnly()
        .ignoringExtraRepeatedFieldElements()
        .isEqualTo(
            DeviceInfo.newBuilder()
                .setDeviceFeature(
                    DeviceFeature.newBuilder()
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addSupportedDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("control_id")
                                        .setValue("AndroidRealDevice-2"))))
                .build());

    // Verifies the driver started successfully.
    assertWithMessage("The driver has not started in 15 seconds")
        .that(driverStarted.await(15L, SECONDS))
        .isTrue();

    // Cancels the session
    assertThat(atsDdaStub.cancelSession(sessionId)).isTrue();

    // Verifies the driver is woke up successfully.
    assertWithMessage("The driver has not been woke up in 15 seconds")
        .that(driverWokeUp.await(15L, SECONDS))
        .isTrue();

    // Checks warnings in logs.
    String checkWarningsMessagePrefix =
        "A successful run should not print exception stack traces, which will confuse"
            + " users and affect debuggability when debugging a failed one.\n";
    assertWithMessage(checkWarningsMessagePrefix + "OLC server stderr")
        .that(olcServerStderrBuilder.toString())
        .doesNotContain("\tat ");
    assertWithMessage(checkWarningsMessagePrefix + "Lab server stderr")
        .that(labServerStderrBuilder.toString())
        .doesNotContain("\tat ");
  }

  private void startServers()
      throws IOException, CommandStartException, InterruptedException, ExecutionException {
    int deviceNum = 5;

    // Starts the OLC server.
    CountDownLatch olcServerAllRemoteDevicesFound = new CountDownLatch(deviceNum);
    Command olcServerCommand =
        Command.of(
                systemUtil
                    .getJavaCommandCreator()
                    .createJavaCommand(
                        OLC_SERVER_FILE_PATH,
                        ImmutableList.of(
                            "--enable_ats_mode=true",
                            "--enable_client_experiment_manager=false",
                            "--enable_client_file_transfer=false",
                            "--enable_grpc_lab_server=true",
                            "--olc_server_port=" + olcServerPort,
                            "--public_dir=" + tmpFolder.newFolder("olc_server_public_dir"),
                            "--tmp_dir_root=" + tmpFolder.newFolder("olc_server_tmp_dir")),
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

                      if (stderr.contains("Sign up lab") && stderr.contains("AndroidRealDevice-")) {
                        olcServerAllRemoteDevicesFound.countDown();
                      }
                    }))
            .successfulStartCondition(line -> line.contains("OLC server started"))
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
    CountDownLatch labServerAllLocalDevicesFound = new CountDownLatch(deviceNum);

    int labServerGrpcPort = PortProber.pickUnusedPort();
    int labServerRpcPort = PortProber.pickUnusedPort();
    int labServerSocketPort = PortProber.pickUnusedPort();

    Command labServerCommand =
        Command.of(
                systemUtil
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
                            "--master_grpc_target=localhost:" + olcServerPort,
                            "--no_op_device_num=" + deviceNum,
                            "--no_op_device_type=AndroidRealDevice",
                            "--public_dir=" + tmpFolder.newFolder("lab_server_public_dir"),
                            "--rpc_port=" + labServerRpcPort,
                            "--serv_via_cloud_rpc=false",
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

                      if (stderr.contains("New device AndroidRealDevice-")) {
                        labServerAllLocalDevicesFound.countDown();
                      } else if (stderr.contains("Sleep for ")) {
                        driverStarted.countDown();
                      } else if (stderr.contains("Wake up from sleep")) {
                        driverWokeUp.countDown();
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
    assertWithMessage("Lab server didn't detect all devices in 15 seconds")
        .that(labServerAllLocalDevicesFound.await(15L, SECONDS))
        .isTrue();

    // Verifies the remote device manager receives device signup successfully.
    assertWithMessage("The remote device manager has not received all device signups in 15 seconds")
        .that(olcServerAllRemoteDevicesFound.await(15L, SECONDS))
        .isTrue();
  }
}
