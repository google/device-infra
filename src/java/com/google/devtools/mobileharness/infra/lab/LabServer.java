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

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
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
import com.google.devtools.deviceinfra.host.daemon.health.HealthStatusManager;
import com.google.devtools.deviceinfra.host.daemon.health.HealthStatusManagerModule;
import com.google.devtools.deviceinfra.host.daemon.health.proto.HealthGrpc;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfigFileProcessor;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.messaging.MessagingService;
import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.lab.Annotations.DebugThreadPool;
import com.google.devtools.mobileharness.infra.lab.Annotations.GlobalEventBus;
import com.google.devtools.mobileharness.infra.lab.Annotations.RpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaStubby;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.infra.lab.controller.DeviceConfigManager;
import com.google.devtools.mobileharness.infra.lab.controller.FileClassifier;
import com.google.devtools.mobileharness.infra.lab.controller.JobManager;
import com.google.devtools.mobileharness.infra.lab.controller.LabDimensionManager;
import com.google.devtools.mobileharness.infra.lab.controller.LocalFileBasedDeviceConfigManager;
import com.google.devtools.mobileharness.infra.lab.controller.MasterSyncerForDevice;
import com.google.devtools.mobileharness.infra.lab.controller.MasterSyncerForJob;
import com.google.devtools.mobileharness.infra.lab.controller.handler.DeviceManagerDrainHandler;
import com.google.devtools.mobileharness.infra.lab.controller.handler.ExecTestServiceDrainHandler;
import com.google.devtools.mobileharness.infra.lab.controller.handler.JobManagerDrainHandler;
import com.google.devtools.mobileharness.infra.lab.controller.handler.MasterSyncerForDeviceDrainHandler;
import com.google.devtools.mobileharness.infra.lab.controller.handler.MasterSyncerForJobDrainHandler;
import com.google.devtools.mobileharness.infra.lab.controller.handler.TestManagerDrainHandler;
import com.google.devtools.mobileharness.infra.lab.rpc.service.ExecTestServiceImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.service.PrepareTestServiceImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.service.grpc.ExecTestGrpcImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.service.grpc.PrepareTestGrpcImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.service.grpc.StatGrpcImpl;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.helper.JobSyncHelper;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.helper.LabSyncHelper;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabSyncStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.JobSyncGrpcStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabSyncGrpcStub;
import com.google.devtools.mobileharness.shared.constant.hostmanagement.HostPropertyConstants.HostPropertyKey;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.LocalLabInfoProvider;
import com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.service.CloudFileTransferServiceGrpcImpl;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.service.CloudFileTransferServiceImpl;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.TaggedFileHandler;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.TaggedFileMetadataProto.TaggedFileMetadata;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemInfoPrinter;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.VersionUtil;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto;
import com.google.devtools.mobileharness.shared.version.rpc.service.VersionServiceImpl;
import com.google.devtools.mobileharness.shared.version.rpc.service.grpc.VersionGrpcImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import io.grpc.BindableService;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  private final SystemInfoPrinter systemInfoPrinter;
  private final NetUtil netUtil;
  private final LocalDeviceManager deviceManager;
  private final PrepareTestServiceImpl prepareTestService;
  private final ExecTestServiceImpl execTestService;
  private final MessagingService messagingService;
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
      SystemInfoPrinter systemInfoPrinter,
      NetUtil netUtil,
      LocalDeviceManager deviceManager,
      PrepareTestServiceImpl prepareTestService,
      ExecTestServiceImpl execTestService,
      MessagingService messagingService,
      ExternalDeviceManager externalDeviceManager,
      @GlobalEventBus EventBus globalInternalBus,
      ListeningExecutorService mainThreadPool,
      @DebugThreadPool ListeningScheduledExecutorService debugExecutor,
      @ServViaStubby boolean enableStubbyRpcServer,
      @RpcPort int rpcPort) {
    this.testManager = testManager;
    this.jobManager = jobManager;
    this.systemUtil = systemUtil;
    this.systemInfoPrinter = systemInfoPrinter;
    this.netUtil = netUtil;
    this.deviceManager = deviceManager;
    this.prepareTestService = prepareTestService;
    this.execTestService = execTestService;
    this.messagingService = messagingService;
    this.externalDeviceManager = externalDeviceManager;
    this.globalInternalBus = globalInternalBus;
    this.mainThreadPool = mainThreadPool;
    this.debugExecutor = debugExecutor;
    this.enableStubbyRpcServer = enableStubbyRpcServer;
    this.rpcPort = rpcPort;
  }

  /** Initializes and runs lab server, and blocks until shutdown. */
  public void run() throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Add changes to trigger copybara.");
    systemInfoPrinter.printSystemInfo(DEBUG);
    try {

      // Sets public general file directory to BaseDevice.
      BaseDevice.setGenFileDirRoot(DirUtil.getPublicGenDir());

      HostProperties nonConfigurableHostProperties =
          initHostLevelDeviceDimensionsAndHostProperties();

      // TODO: Create FileTransferSocketService.

      ApiConfig apiConfig = ApiConfig.getInstance();
      String hostName = netUtil.getLocalHostName();
      DeviceIdManager deviceIdManager = DeviceIdManager.getInstance();
      DeviceConfigManager deviceConfigManager = null;
      if (Flags.instance().enableDeviceConfigManager.getNonNull()) {
        if (!Flags.instance().apiConfigFile.getNonNull().isEmpty()
            || !Flags.instance().labDeviceConfigFile.getNonNull().isEmpty()) {
          deviceConfigManager =
              new LocalFileBasedDeviceConfigManager(
                  deviceManager, deviceIdManager, apiConfig, new ApiConfigFileProcessor());
        }
      }
      apiConfig.initialize(
          /* isDefaultPublic= */ true,
          /* isDefaultSynced= */ deviceConfigManager == null,
          hostName);

      // Initializes the master service stub. If master host is set to empty, the lab server
      // will run independently without the master server for debugging.
      MasterSyncerForDevice masterSyncerForDevice = null;
      MasterSyncerForJob masterSyncerForJob = null;

      LabSyncHelper labSyncHelper = null;

      LabSyncStub labSyncStub = null;
      JobSyncStub jobSyncStub = null;

      if (Flags.instance().enableMasterSyncer.getNonNull()) {
        MasterGrpcStubHelper helper =
            new MasterGrpcStubHelper(
                ChannelFactory.createChannel(
                    Flags.instance().masterGrpcTarget.getNonNull(), mainThreadPool));
        labSyncStub = new LabSyncGrpcStub(helper);
        jobSyncStub = new JobSyncGrpcStub(helper);
      }

      if (jobSyncStub != null) {
        JobSyncHelper jobSyncHelper = new JobSyncHelper(jobSyncStub);
        masterSyncerForJob = new MasterSyncerForJob(jobManager, jobSyncHelper, deviceManager);
        globalInternalBus.register(masterSyncerForJob);
      }

      if (labSyncStub != null) {
        labSyncHelper =
            new LabSyncHelper(
                labSyncStub,
                rpcPort,
                Flags.instance().socketPort.getNonNull(),
                Flags.instance().grpcPort.getNonNull(),
                nonConfigurableHostProperties);
        masterSyncerForDevice = new MasterSyncerForDevice(deviceManager, labSyncHelper);
        globalInternalBus.register(masterSyncerForDevice);
        apiConfig.addListener(masterSyncerForDevice);
      }

      VersionServiceImpl versionService =
          new VersionServiceImpl(
              VersionProto.Version.newBuilder()
                  .setVersion(Version.LAB_VERSION.toString())
                  .setType("LAB_VERSION")
                  .build());

      Injector healthInjector = Guice.createInjector(new HealthStatusManagerModule());
      HealthStatusManager healthStatusManager =
          healthInjector.getInstance(HealthStatusManager.class);
      healthStatusManager.addDrainHandler(new DeviceManagerDrainHandler(deviceManager));
      healthStatusManager.addDrainHandler(new JobManagerDrainHandler(jobManager));
      healthStatusManager.addDrainHandler(new TestManagerDrainHandler(testManager));
      healthStatusManager.addDrainHandler(new ExecTestServiceDrainHandler(execTestService));

      if (masterSyncerForDevice != null) {
        healthStatusManager.addDrainHandler(
            new MasterSyncerForDeviceDrainHandler(masterSyncerForDevice));
      }
      if (masterSyncerForJob != null) {
        healthStatusManager.addDrainHandler(new MasterSyncerForJobDrainHandler(masterSyncerForJob));
      }

      logger.atInfo().log("Lab server %s starts.", Version.LAB_VERSION);
      logger.atInfo().log(
          "Lab server starts with hostname: %s, version: %s, build version: [%s]",
          hostName,
          Version.LAB_VERSION,
          VersionUtil.getBuildVersion().map(ProtoTextFormat::shortDebugString).orElse("n/a"));

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
      if (deviceConfigManager != null) {
        logFailure(
            mainThreadPool.submit(deviceConfigManager),
            Level.SEVERE,
            "Device config manager fatal error");
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
      if (Flags.instance().enableCloudFileTransfer.getNonNull()) {
        CloudFileTransferServiceImpl cloudFileTransferServiceImpl =
            new CloudFileTransferServiceImpl(
                Path.of(DirUtil.getCloudReceivedDir()), Path.of(DirCommon.getPublicDirRoot()));
        BindableService cloudFileTransferService =
            new CloudFileTransferServiceGrpcImpl(cloudFileTransferServiceImpl)
                .addHandler(
                    TaggedFileMetadata.class,
                    new TaggedFileHandler(new FileClassifier(jobManager)));
        localGrpcServices.add(cloudFileTransferService);
      }

      localGrpcServices.add(healthInjector.getInstance(HealthGrpc.HealthImplBase.class));

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

      if (Flags.instance().enableMessagingService.getNonNull()) {
        localGrpcServices.add(messagingService);
      }

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
    } catch (MobileHarnessException | IOException | RuntimeException | Error e) {
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

  /**
   * Initializes logger.
   *
   * @apiNote this method should be called after flags are parsed
   */
  public static void initLogger() throws MobileHarnessException {
    LocalFileUtil localFileUtil = new LocalFileUtil();
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
  }

  /**
   * Initializes system properties.
   *
   * @apiNote this method should be called after flags are parsed
   */
  public static void initSystemProperties() {
    SystemUtil systemUtil = new SystemUtil();
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

  private HostProperties initHostLevelDeviceDimensionsAndHostProperties()
      throws MobileHarnessException, InterruptedException {

    HostProperties.Builder hostProperties = HostProperties.newBuilder();

    if (Flags.instance().addRequiredDimensionForPartnerSharedPool.getNonNull()) {
      LabDimensionManager.getInstance()
          .getRequiredLocalDimensions()
          .add(Name.POOL, Value.POOL_PARTNER_SHARED);
    }

    if (Flags.instance().addSupportedDimensionForOmniModeUsage.get() != null) {
      LabDimensionManager.getInstance()
          .getSupportedLocalDimensions()
          .add(
              Name.OMNI_MODE_USAGE,
              Ascii.toLowerCase(Flags.instance().addSupportedDimensionForOmniModeUsage.get()));
    }

    // Supported dimensions

    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.HOST_NAME.name()), netUtil.getLocalHostName());

    // Host IP
    Optional<String> hostIp = netUtil.getUniqueHostIpOrEmpty();
    if (!hostIp.isEmpty()) {
      LabDimensionManager.getInstance()
          .getSupportedLocalDimensions()
          .add(Ascii.toLowerCase(Name.HOST_IP.name()), hostIp.get());
      hostProperties.addHostProperty(
          HostProperty.newBuilder()
              .setKey(Ascii.toLowerCase(HostPropertyKey.HOST_IP.name()))
              .setValue(hostIp.get())
              .build());
    }

    // Host OS
    String osName = systemUtil.getOsName();
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.HOST_OS.name()), systemUtil.getOsName());
    hostProperties.addHostProperty(
        HostProperty.newBuilder()
            .setKey(Ascii.toLowerCase(HostPropertyKey.HOST_OS.name()))
            .setValue(osName)
            .build());

    // Host OS version. For Linux, we return ubuntu version.
    String osVersion =
        systemUtil.isOnLinux()
            ? systemUtil.getUbuntuVersion().orElse(systemUtil.getOsVersion())
            : systemUtil.getOsVersion();
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.HOST_OS_VERSION.name()), osVersion);
    hostProperties.addHostProperty(
        HostProperty.newBuilder()
            .setKey(Ascii.toLowerCase(HostPropertyKey.HOST_OS_VERSION.name()))
            .setValue(osVersion)
            .build());

    // Lab host version
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.HOST_VERSION.name()), Version.LAB_VERSION.toString());
    hostProperties.addHostProperty(
        HostProperty.newBuilder()
            .setKey(Ascii.toLowerCase(HostPropertyKey.HOST_VERSION.name()))
            .setValue(Version.LAB_VERSION.toString()));

    // Host location
    Optional<String> labLocation = netUtil.getLocalHostLocation();
    if (!labLocation.isEmpty()) {
      LabDimensionManager.getInstance()
          .getSupportedLocalDimensions()
          .add(Ascii.toLowerCase(Name.LAB_LOCATION.name()), labLocation.get());
      hostProperties.addHostProperty(
          HostProperty.newBuilder()
              .setKey(Ascii.toLowerCase(HostPropertyKey.LAB_LOCATION.name()))
              .setValue(labLocation.get())
              .build());
    }

    // Host location type
    String locationType = netUtil.getLocalHostLocationType().name();
    LabDimensionManager.getInstance()
        .getSupportedLocalDimensions()
        .add(Ascii.toLowerCase(Name.LOCATION_TYPE.name()), Ascii.toLowerCase(locationType));
    hostProperties.addHostProperty(
        HostProperty.newBuilder()
            .setKey(Ascii.toLowerCase(HostPropertyKey.LOCATION_TYPE.name()))
            .setValue(Ascii.toLowerCase(locationType))
            .build());

    // Host total memory
    hostProperties.addHostProperty(
        HostProperty.newBuilder()
            .setKey(Ascii.toLowerCase(HostPropertyKey.TOTAL_MEM.name()))
            .setValue(StrUtil.getHumanReadableSize(systemUtil.getTotalMemory()))
            .build());

    // Add ATS GitHub version to host properties. Only ATS labs have this property.
    VersionUtil.getBuildVersion()
        .ifPresent(
            version -> {
              if (version.hasGithubVersion()) {
                hostProperties.addHostProperty(
                    HostProperty.newBuilder()
                        .setKey(Ascii.toLowerCase(HostPropertyKey.GITHUB_VERSION.name()))
                        .setValue(version.getGithubVersion())
                        .build());
              }
            });

    return hostProperties.build();
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
