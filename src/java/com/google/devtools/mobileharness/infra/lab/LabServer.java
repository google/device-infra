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

import static com.google.common.base.StandardSystemProperty.OS_NAME;
import static com.google.common.base.StandardSystemProperty.OS_VERSION;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.lab.Annotations.DebugThreadPool;
import com.google.devtools.mobileharness.infra.lab.Annotations.GlobalEventBus;
import com.google.devtools.mobileharness.infra.lab.Annotations.RpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaStubby;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.infra.lab.controller.JobManager;
import com.google.devtools.mobileharness.infra.lab.controller.LabDimensionManager;
import com.google.devtools.mobileharness.infra.lab.controller.MasterSyncerForDevice;
import com.google.devtools.mobileharness.infra.lab.controller.MasterSyncerForJob;
import com.google.devtools.mobileharness.infra.lab.rpc.service.ExecTestServiceImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.service.PrepareTestServiceImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.service.grpc.ExecTestGrpcImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.service.grpc.PrepareTestGrpcImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.service.grpc.StatGrpcImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.helper.LabSyncHelper;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabSyncStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabSyncGrpcStub;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.LocalLabInfoProvider;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.rpc.service.VersionServiceImpl;
import com.google.devtools.mobileharness.shared.version.rpc.service.grpc.VersionGrpcImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import io.grpc.BindableService;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import javax.inject.Inject;

/** Lab server. */
public class LabServer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Random random = new Random();

  // Injected fields
  private final SettableFuture<TestServices> startingFuture = SettableFuture.create();
  private final ProxyTestManager testManager;
  private final JobManager jobManager;
  private final SystemUtil systemUtil;
  private final NetUtil netUtil;
  private final LocalDeviceManager deviceManager;
  private final PrepareTestServiceImpl prepareTestService;
  private final ExecTestServiceImpl execTestService;
  private final ExternalDeviceManager externalDeviceManager;
  private final EventBus globalInternalBus;
  private final ListeningExecutorService mainThreadPool;
  private final ListeningScheduledExecutorService debugExecutor;
  private final boolean enableStubbyRpcServer;
  private final int rpcPort;

  @Inject
  LabServer(
      ProxyTestManager testManager,
      JobManager jobManager,
      SystemUtil systemUtil,
      NetUtil netUtil,
      LocalDeviceManager deviceManager,
      PrepareTestServiceImpl prepareTestService,
      ExecTestServiceImpl execTestService,
      ExternalDeviceManager externalDeviceManager,
      @GlobalEventBus EventBus globalInternalBus,
      ListeningExecutorService mainThreadPool,
      @DebugThreadPool ListeningScheduledExecutorService debugExecutor,
      @ServViaStubby boolean enableStubbyRpcServer,
      @RpcPort int rpcPort) {
    this.testManager = testManager;
    this.jobManager = jobManager;
    this.systemUtil = systemUtil;
    this.netUtil = netUtil;
    this.deviceManager = deviceManager;
    this.prepareTestService = prepareTestService;
    this.execTestService = execTestService;
    this.externalDeviceManager = externalDeviceManager;
    this.globalInternalBus = globalInternalBus;
    this.mainThreadPool = mainThreadPool;
    this.debugExecutor = debugExecutor;
    this.enableStubbyRpcServer = enableStubbyRpcServer;
    this.rpcPort = rpcPort;
  }

  /** Initializes and runs lab server, and blocks until shutdown. */
  public void run() throws MobileHarnessException, InterruptedException {
    try {

      // Sets public general file directory to BaseDevice.
      BaseDevice.setGenFileDirRoot(DirUtil.getPublicGenDir());
      initHostLevelDeviceDimensions();

      // TODO: Create FileTransferSocketService.

      ApiConfig apiConfig = ApiConfig.getInstance();
      String hostName = netUtil.getLocalHostName();
      apiConfig.init(/* defaultPublic= */ DeviceUtil.inSharedLab(), hostName);

      // Initializes the master service stub. If master host is set to empty, the lab server
      // will run independently without the master server for debugging.
      MasterSyncerForDevice masterSyncerForDevice = null;
      MasterSyncerForJob masterSyncerForJob = null;

      LabSyncStub labSyncStub = null;
      JobSyncStub jobSyncStub = null;

      if (Flags.instance().enableMasterSyncer.getNonNull()) {
        MasterGrpcStubHelper helper =
            new MasterGrpcStubHelper(
                ChannelFactory.createChannel(
                    Flags.instance().masterGrpcTarget.getNonNull(), mainThreadPool));
        labSyncStub = new LabSyncGrpcStub(helper);
      }

      if (labSyncStub != null) {
        LabSyncHelper labSyncHelper =
            new LabSyncHelper(
                labSyncStub,
                rpcPort,
                Flags.instance().socketPort.getNonNull(),
                Flags.instance().grpcPort.getNonNull());
        masterSyncerForDevice = new MasterSyncerForDevice(deviceManager, labSyncHelper);
        globalInternalBus.register(masterSyncerForDevice);
        apiConfig.addObserver(masterSyncerForDevice);
      }

      VersionServiceImpl versionService = new VersionServiceImpl(Version.LAB_VERSION);

      logger.atInfo().log("Lab server %s starts.", Version.LAB_VERSION);

      // Starts controllers. The file cleaner should be the first one to start.
      // TODO: Start fileCleaner.
      logFailure(mainThreadPool.submit(testManager), Level.SEVERE, "Test manager fatal error");
      logFailure(mainThreadPool.submit(deviceManager), Level.SEVERE, "Device manager fatal error");
      // TODO: Start socketFileReceiver.

      if (masterSyncerForDevice != null) {
        logFailure(
            mainThreadPool.submit(masterSyncerForDevice),
            Level.SEVERE,
            "Master syncer for device fatal error");
      }
      if (masterSyncerForJob != null) {
        logFailure(
            mainThreadPool.submit(masterSyncerForJob),
            Level.SEVERE,
            "Master syncer for job fatal error");
      }

      // gRPC services for both local RPC and CloudRPC.
      ImmutableList<BindableService> grpcServices =
          ImmutableList.of(
              new VersionGrpcImpl(versionService),
              new ExecTestGrpcImpl(execTestService),
              new PrepareTestGrpcImpl(prepareTestService),
              new StatGrpcImpl());

      // gRPC services for local RPC only.
      List<BindableService> localGrpcServices = new ArrayList<>(grpcServices);

      localGrpcServices.add(ProtoReflectionService.newInstance());
      localGrpcServices.add(
          Guice.createInjector(
                  new AbstractModule() {
                    @Override
                    protected void configure() {
                      bind(LabInfoProvider.class)
                          .toInstance(new LocalLabInfoProvider(deviceManager));
                    }
                  })
              .getInstance(com.google.devtools.mobileharness.shared.labinfo.LabInfoService.class));

      // Starts gRPC server for local requests only.
      NettyServerBuilder localGrpcServerBuilder =
          NettyServerBuilder.forPort(Flags.instance().grpcPort.getNonNull())
              .executor(mainThreadPool);
      for (BindableService service : localGrpcServices) {
        localGrpcServerBuilder.addService(service);
      }
      localGrpcServerBuilder.build().start();

      // NOTE: Only for debug/test purpose.
      if (Flags.instance().debugRandomExit.getNonNull()) {
        // Every 5 mins, randomly exit.
        Runnable exitTask =
            () -> {
              if (random.nextBoolean()) {
                systemUtil.exit(ExitCode.Shared.DEBUG_ERROR, "Exit upon --debug_random_exit");
              }
            };
        logFailure(
            debugExecutor.scheduleAtFixedRate(
                exitTask,
                /* initialDelay= */ Duration.ofMinutes(5).toMillis(),
                /* period= */ Duration.ofMinutes(5).toMillis(),
                MILLISECONDS),
            Level.SEVERE,
            "Fatal error in exit task");
      }

      logger.atInfo().log("Lab server successfully started");
      startingFuture.set(TestServices.of(testManager, deviceManager));
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException
        | IOException
        | RuntimeException
        | Error e) {
      MobileHarnessException exception =
          new MobileHarnessException(
              InfraErrorId.LAB_UTRS_SERVER_START_ERROR, "Failed to run lab server", e);
      startingFuture.setException(exception);
      throw exception;
    }

    // Waits until interrupted.
    Thread.currentThread().join();
  }

  /**
   * Returns a future which becomes done after the lab server starts.
   *
   * <p>Do NOT make it public in production.
   */
  @VisibleForTesting
  public ListenableFuture<TestServices> getStartingFuture() {
    return startingFuture;
  }

  public void onShutdown() {
    logger.atInfo().log("Lab server is shutting down.");

    SystemUtil.setProcessIsShuttingDown();
    // Shuts down the server and all threads here.
    if (mainThreadPool != null) {
      mainThreadPool.shutdownNow();
    }
    // TODO: Shutdown socketFileReceiver.

    Set<Integer> processIds = null;
    try {
      processIds = systemUtil.getProcessesByPort(rpcPort);
    } catch (MobileHarnessException | InterruptedException e) {
      logger.atWarning().log(
          "Failed to get child process id of lab server (ignored): %s", e.getMessage());
    }
    if (processIds != null) {
      for (int processId : processIds) {
        try {
          systemUtil.killDescendantAndZombieProcesses(processId, KillSignal.SIGKILL);
          logger.atInfo().log(
              "Killed child processes of lab server rpc server with pid %d", processId);
        } catch (MobileHarnessException | InterruptedException e) {
          logger.atWarning().log(
              "Failed to kill child processes of parent pid %d (ignored): %s",
              processId, e.getMessage());
        }
      }
    }
  }

  /** Initializes environment by setting appropriate env vars and setting logger. */
  public static void initializeEnv() throws MobileHarnessException {
    LocalFileUtil localFileUtil = new LocalFileUtil();
    SystemUtil systemUtil = new SystemUtil();

    // Loggers should be initialized it only after parsing flags, in order to get the parameters to
    // initialize the log file handler.
    try {
      localFileUtil.prepareDir(DirUtil.getDefaultLogDir());
      localFileUtil.grantFileOrDirFullAccess(DirUtil.getDefaultLogDir());
      // Prepares the lab server's temporary log directory.
      localFileUtil.prepareDir(DirCommon.getTempDirRoot());
      localFileUtil.grantFileOrDirFullAccess(DirCommon.getTempDirRoot());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_INIT_ENV_PREPARE_DIR_ERROR, "Failed to setup local file dirs!", e);
    }
    MobileHarnessLogger.init(DirUtil.getDefaultLogDir());

    // Sets the system property TEST_TMPDIR, so that TestUtil will use it as tmp dir. If we don't
    // set it, the TestUtil will use /var/folders/.../$USER/tmp on Mac by default, where we cannot
    // change the attributes of the folders. Before we set the property "java.io.tmpdir" to be
    // "/tmp", and TestUtil will create tmp dir under "/tmp/$USER/tmp/". We run the lab server as
    // sudo, so the tmp dir is "/tmp/root/tmp/". Now it introduces failures when run tests against
    // iOS simulator. Because we run the commands to control the iOS simulator as non-root user,
    // cannot read or write files from or to the root's folder, see: b/22616416.
    System.setProperty("TEST_TMPDIR", DirUtil.getTempDir());
    if (systemUtil.isOnMac()) {
      // Disable exporting hsperf. Because that includes things which read /proc which doesn't
      // exist on OSX.
      System.setProperty("com.google.monitoring.streamz.JvmMetrics.export_hsperf", "false");
    }
  }

  private void initHostLevelDeviceDimensions() throws MobileHarnessException, InterruptedException {
    // Required dimensions
    // Adds "pool:shared" to lab required dimensions for m&m labs.
    if (DeviceUtil.inSharedLab()) {
      LabDimensionManager.getInstance()
          .getRequiredLocalDimensions()
          .add(Name.POOL, Value.POOL_SHARED);
    }

    // Supported dimensions
    if (systemUtil.isOnLinux()) {
      // By default, all newly upgraded Linux labs will support container-mode tests.
      LabDimensionManager.getInstance()
          .getSupportedLocalDimensions()
          .add(Name.LAB_SUPPORTS_CONTAINER, Value.TRUE);
    }
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.HOST_NAME.name()), netUtil.getLocalHostName());
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.HOST_IP.name()), netUtil.getUniqueHostIpOrEmpty().orElse(""));
    netUtil
        .getLocalHostLocation()
        .ifPresent(
            labLocation ->
                LabDimensionManager.getInstance()
                    .getSupportedLocalDimensions()
                    .add(Ascii.toLowerCase(Name.LAB_LOCATION.name()), labLocation));
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.HOST_OS.name()), OS_NAME.value());
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.HOST_VERSION.name()), Version.LAB_VERSION.toString());
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(
            Ascii.toLowerCase(Name.LOCATION_TYPE.name()),
            Ascii.toLowerCase(netUtil.getLocalHostLocationType().name()));
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.HOST_OS_VERSION.name()), OS_VERSION.value());
  }

  /** Test services created by lab server. */
  @AutoValue
  @VisibleForTesting()
  public abstract static class TestServices {

    public abstract ProxyTestManager testManager();

    public abstract LocalDeviceManager deviceManager();

    public static TestServices of(ProxyTestManager testManager, LocalDeviceManager deviceManager) {
      return new AutoValue_LabServer_TestServices(testManager, deviceManager);
    }
  }
}
