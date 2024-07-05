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

package com.google.devtools.mobileharness.infra.lab;

import static com.google.common.collect.ImmutableList.toImmutableList;
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
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AllocationWithStats;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.AtsMode;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.AtsModeModule;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionRequest;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.shared.version.rpc.stub.grpc.VersionGrpcStub;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
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
          "java/com/google/devtools/mobileharness/infra/lab/lab_server_oss_deploy.jar");
  private static final String API_CONFIG_FILE_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/lab/api_config.textproto");

  private StringBuilder labServerStdoutBuilder;
  private StringBuilder labServerStderrBuilder;

  private CountDownLatch labServerFoundDevice;

  private int masterPort;
  private int labServerGrpcPort;
  private int labServerRpcPort;
  private int labServerSocketPort;
  private Command labServerCommand;

  private CommandProcess labServerProcess;
  private ManagedChannel labServerChannel;
  @Bind private Sleeper sleeper;
  @Bind private ListeningScheduledExecutorService listeningScheduledExecutorService;
  @Bind private ExecutorService executorService;
  @Bind private ListeningExecutorService listeningExecutorService;

  @Bind private Clock clock;

  @Inject private AtsMode atsMode;

  @Before
  public void setUp() throws Exception {
    sleeper = Sleeper.defaultSleeper();
    clock = Clock.systemUTC();
    listeningExecutorService = ThreadPools.createStandardThreadPool("ats-mode-thread-pool");
    executorService = listeningExecutorService;
    listeningScheduledExecutorService =
        ThreadPools.createStandardScheduledThreadPool(
            "ats-mode-scheduled-thread-pool", /* corePoolSize= */ 5);
    Guice.createInjector(BoundFieldModule.of(this), new AtsModeModule()).injectMembers(this);

    masterPort = PortProber.pickUnusedPort();
    labServerGrpcPort = PortProber.pickUnusedPort();
    labServerRpcPort = PortProber.pickUnusedPort();
    labServerSocketPort = PortProber.pickUnusedPort();

    String labServerPublicDirPath = tmpFolder.newFolder("lab_server_public_dir").toString();
    String labServerTmpDirPath = tmpFolder.newFolder("lab_server_tmp_dir").toString();

    labServerStdoutBuilder = new StringBuilder();
    labServerStderrBuilder = new StringBuilder();

    labServerFoundDevice = new CountDownLatch(1);

    labServerCommand =
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
                            "--disable_device_reboot=true",
                            "--disable_wifi_util_func=true",
                            "--enable_android_device_ready_check=false",
                            "--enable_cloud_logging=false",
                            "--enable_device_state_change_recover=false",
                            "--enable_device_system_settings_change=false",
                            "--enable_external_master_server=true",
                            "--enable_file_cleaner=false",
                            "--enable_stubby_rpc_server=false",
                            "--external_adb_initializer_template=true",
                            "--grpc_port=" + labServerGrpcPort,
                            "--master_grpc_target=localhost:" + masterPort,
                            "--mute_android=false",
                            "--no_op_device_num=1",
                            "--public_dir=" + labServerPublicDirPath,
                            "--rpc_port=" + labServerRpcPort,
                            "--serv_via_cloud_rpc=false",
                            "--set_test_harness_property=false",
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

                      if (stderr.contains("New device NoOpDevice-0")) {
                        labServerFoundDevice.countDown();
                      }
                    }))
            .successfulStartCondition(line -> line.contains("Lab server successfully started"))
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
    startServersAndWaitUntilReady();

    GetVersionResponse getVersionResponse =
        new VersionGrpcStub(labServerChannel).getVersion(GetVersionRequest.getDefaultInstance());

    assertThat(getVersionResponse)
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            GetVersionResponse.newBuilder().setVersion(Version.LAB_VERSION.toString()).build());
  }

  @Test
  public void checkLabServerLog() throws Exception {
    startServersAndWaitUntilReady();

    logger.atInfo().log("Running lab server for a while...");
    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(10L));

    assertWithMessage(
            "A normal lab server run should not print exception stack traces, which will confuse"
                + " users and affect debuggability when debugging lab server logs.\n"
                + "lab server stderr")
        .that(labServerStderrBuilder.toString())
        .doesNotContain("\tat ");
  }

  @Test
  public void allocateDevice() throws Exception {
    startServersAndWaitUntilReady();

    // Creates JobInfo and TestInfo.
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("fake_job_id", "fake_job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("NoOpDevice")
                    .setDriver("NoOpDriver")
                    .addDecorator("NoOpDecorator")
                    .build())
            .setJobUser(JobUser.newBuilder().setRunAs("fake_owner").build())
            .build();
    jobInfo.dimensions().add("fake_dimension_name", "fake_dimension_value");
    jobInfo.tests().add("fake_test_id", "fake_test_name");

    // Creates DeviceAllocator.
    DeviceAllocator deviceAllocator =
        atsMode.createDeviceAllocator(jobInfo, /* globalInternalBus= */ null);
    Optional<ExceptionDetail> setUpError = deviceAllocator.setUp();
    if (setUpError.isPresent()) {
      throw ErrorModelConverter.toMobileHarnessException(setUpError.get());
    }

    // Polls allocations.
    List<AllocationWithStats> allocations;
    int count = 0;
    while (true) {
      allocations = deviceAllocator.pollAllocations();
      count++;
      if (!allocations.isEmpty() || count > 20) {
        break;
      }
      Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1L));
    }

    // Checks the allocation.
    assertThat(allocations.stream().map(AllocationWithStats::allocation).collect(toImmutableList()))
        .comparingElementsUsing(
            Correspondence.from(
                (Allocation allocation, String uuidSuffix) ->
                    requireNonNull(allocation)
                        .getDevice()
                        .id()
                        .endsWith(requireNonNull(uuidSuffix)),
                "has a device UUID ending with"))
        .containsExactly("NoOpDevice-0");
    assertThat(allocations.get(0).allocation().getDevice().labLocator().ports().getAll())
        .containsExactly(
            PortType.LAB_SERVER_RPC,
            labServerRpcPort,
            PortType.LAB_SERVER_SOCKET,
            labServerSocketPort,
            PortType.LAB_SERVER_GRPC,
            labServerGrpcPort);
  }

  private void startServersAndWaitUntilReady()
      throws MobileHarnessException, InterruptedException, IOException, ExecutionException {
    logger.atInfo().log("Starting AtsMode, port=%s", masterPort);
    atsMode.initialize(null);
    ImmutableList<BindableService> bindableServices = atsMode.provideServicesForWorkers();
    NettyServerBuilder nettyServerBuilder =
        NettyServerBuilder.forPort(masterPort).executor(listeningExecutorService);
    bindableServices.forEach(nettyServerBuilder::addService);
    nettyServerBuilder.build().start();

    logger.atInfo().log("Starting lab server, command=%s", labServerCommand);
    labServerProcess = new CommandExecutor().start(labServerCommand);

    // Waits until lab server starts and detects devices successfully.
    try {
      assertWithMessage("Lab server didn't start successfully")
          .that(labServerProcess.successfulStartFuture().get(60L, SECONDS))
          .isTrue();
    } catch (TimeoutException e) {
      throw new AssertionError("Lab server didn't start in 60 seconds", e);
    }
    assertWithMessage("Lab server didn't detect devices in 15 seconds")
        .that(labServerFoundDevice.await(15L, SECONDS))
        .isTrue();
  }
}
