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

package com.google.wireless.qa.mobileharness.lab;

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
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionRequest;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.shared.version.rpc.stub.grpc.VersionGrpcStub;
import io.grpc.ManagedChannel;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class LabServerIntegrationTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Rule
  public TestWatcher testWatcher =
      new TestWatcher() {

        @Override
        protected void starting(Description description) {
          // Prints test name to help debug lab server output in test logs.
          logger.atInfo().log(
              "\n========================================\n"
                  + "Starting test: %s\n"
                  + "========================================\n",
              description.getDisplayName());
        }

        @Override
        protected void failed(Throwable e, Description description) {
          // Adds lab server stdout/stderr to a failed test.
          Exception labServerOutput =
              new IllegalStateException(
                  String.format(
                      "lab_server_stdout=[%s], lab_server_stderr=[%s]",
                      labServerStdoutBuilder, labServerStderrBuilder));
          labServerOutput.setStackTrace(new StackTraceElement[0]);
          e.addSuppressed(labServerOutput);
        }
      };

  private static final String LAB_SERVER_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/wireless/qa/mobileharness/lab/lab_server_oss_deploy.jar");

  private StringBuilder labServerStdoutBuilder;
  private StringBuilder labServerStderrBuilder;

  private CountDownLatch labServerStartedOrFailedToStart;
  private AtomicBoolean labServerStartedSuccessfully;
  private CountDownLatch labServerFoundDevice;

  private Command labServerCommand;

  private CommandProcess labServerProcess;
  private ManagedChannel labServerChannel;

  @Before
  public void setUp() throws Exception {
    int labServerGrpcPort = PortProber.pickUnusedPort();
    int labServerRpcPort = PortProber.pickUnusedPort();
    int labServerSocketPort = PortProber.pickUnusedPort();

    String labServerPublicDirPath = tmpFolder.newFolder("lab_server_public_dir").toString();
    String labServerTmpDirPath = tmpFolder.newFolder("lab_server_tmp_dir").toString();

    labServerStdoutBuilder = new StringBuilder();
    labServerStderrBuilder = new StringBuilder();

    labServerStartedOrFailedToStart = new CountDownLatch(1);
    labServerStartedSuccessfully = new AtomicBoolean();
    labServerFoundDevice = new CountDownLatch(1);

    labServerCommand =
        Command.of(
                new SystemUtil()
                    .getJavaCommandCreator()
                    .createJavaCommand(
                        LAB_SERVER_FILE_PATH,
                        ImmutableList.of(
                            "--detect_adb_device=false",
                            "--enable_cloud_logging=false",
                            "--enable_emulator_detection=false",
                            "--enable_file_cleaner=false",
                            "--enable_master_syncer=false",
                            "--enable_stubby_rpc_server=false",
                            "--grpc_port=" + labServerGrpcPort,
                            "--no_op_device_num=1",
                            "--public_dir=" + labServerPublicDirPath,
                            "--rpc_port=" + labServerRpcPort,
                            "--serv_via_cloud_rpc=false",
                            "--socket_port=" + labServerSocketPort,
                            "--tmp_dir_root=" + labServerTmpDirPath),
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

                      if (stderr.contains("UTRS successfully started")) {
                        labServerStartedSuccessfully.set(true);
                        labServerStartedOrFailedToStart.countDown();
                      } else if (stderr.contains("New device NoOpDevice-0")) {
                        labServerFoundDevice.countDown();
                      }
                    }))
            .onExit(result -> labServerStartedOrFailedToStart.countDown())
            .redirectStderr(false)
            .needStdoutInResult(false)
            .needStderrInResult(false);

    labServerChannel = ChannelFactory.createLocalChannel(labServerGrpcPort, directExecutor());
  }

  @After
  public void tearDown() {
    if (labServerProcess != null) {
      labServerProcess.kill();
    }
    if (labServerChannel != null) {
      labServerChannel.shutdown();
    }
  }

  @Test
  public void getVersion() throws Exception {
    startLabServerAndWaitUntilReady();

    GetVersionResponse getVersionResponse =
        new VersionGrpcStub(labServerChannel).getVersion(GetVersionRequest.getDefaultInstance());

    assertThat(getVersionResponse)
        .isEqualTo(
            GetVersionResponse.newBuilder().setVersion(Version.LAB_VERSION.toString()).build());
  }

  @Test
  public void checkLabServerLog() throws Exception {
    startLabServerAndWaitUntilReady();

    logger.atInfo().log("Running lab server for a while...");
    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(10L));

    assertWithMessage(
            "A normal lab server run should not print exception stack traces, which will confuse"
                + " users and affect debuggability when debugging lab server logs.\n"
                + "lab server stderr")
        .that(labServerStderrBuilder.toString())
        .doesNotContain("\tat ");
  }

  private void startLabServerAndWaitUntilReady()
      throws CommandStartException, InterruptedException {
    logger.atInfo().log("Starting lab server, command=%s", labServerCommand);
    labServerProcess = new CommandExecutor().start(labServerCommand);

    // Waits until lab server starts and detects devices successfully.
    assertWithMessage("Lab server didn't start in 60 seconds")
        .that(labServerStartedOrFailedToStart.await(60L, SECONDS))
        .isTrue();
    assertWithMessage("Lab server didn't start successfully")
        .that(labServerStartedSuccessfully.get())
        .isTrue();
    assertWithMessage("Lab server didn't detect devices in 15 seconds")
        .that(labServerFoundDevice.await(15L, SECONDS))
        .isTrue();
  }
}
