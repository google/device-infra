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

import static com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerLogs.SERVER_STARTED_SIGNAL;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.EnableDatabase;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.OlcDatabaseConnections;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerDirs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.ServiceProvider;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecords;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.ControlService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.SessionService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.VersionService;
import com.google.devtools.mobileharness.infra.monitoring.MonitorPipelineLauncher;
import com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat;
import com.google.devtools.mobileharness.shared.util.comm.relay.service.ServerUtils;
import com.google.devtools.mobileharness.shared.util.comm.server.ClientAddressServerInterceptor;
import com.google.devtools.mobileharness.shared.util.comm.server.LifecycleManager;
import com.google.devtools.mobileharness.shared.util.comm.server.LifecycleManager.LabeledServer;
import com.google.devtools.mobileharness.shared.util.comm.server.ServerBuilderFactory;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import com.google.devtools.mobileharness.shared.util.database.TablesLister;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.inject.CommonModule;
import com.google.devtools.mobileharness.shared.util.logging.flogger.FloggerFormatter;
import com.google.devtools.mobileharness.shared.util.signal.Signals;
import com.google.devtools.mobileharness.shared.util.system.SystemInfoPrinter;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.VersionUtil;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.constant.DirPreparer;
import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** OLC server. */
public class OlcServer {

  static {
    FloggerFormatter.initialize();
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration DUMP_MEMORY_INFO_INTERVAL = Duration.ofMinutes(5L);

  public static void main(String[] args) throws MobileHarnessException, InterruptedException {
    // Parses flags.
    Flags.parse(args);

    // Creates the server.
    Instant serverStartTime = Instant.now();
    boolean enableDatabase = validateDatabase();
    OlcServer server =
        Guice.createInjector(
                new ServerModule(
                    Flags.instance().enableAtsMode.getNonNull(),
                    serverStartTime,
                    Flags.instance().enableCloudPubsubMonitoring.getNonNull(),
                    enableDatabase,
                    Flags.instance().enableGrpcRelay.getNonNull()),
                new CommonModule())
            .getInstance(OlcServer.class);

    // Prepares dirs.
    DirPreparer.prepareDirRoots();

    // Initializes logger.
    MobileHarnessLogger.init(
        OlcServerDirs.getLogDir(),
        ImmutableList.of(server.logManager.getLogHandler()),
        /* disableConsoleHandler= */ false);

    // Initializes log recorder.
    LogRecorder.getInstance().initialize(server.logManager.getLogRecorderBackend());

    // Logs arguments and system info.
    if (args.length > 0) {
      logger.atInfo().log("Args: %s", args);
    }
    server.systemInfoPrinter.printSystemInfo(DEBUG);

    // Adds shutdown hook.
    Runtime.getRuntime().addShutdownHook(new Thread(server::onShutdown));

    // Monitors known signals.
    if (Flags.instance().monitorSignals.getNonNull()) {
      Signals.monitorKnownSignals();
    }

    // Runs the server in standalone mode.
    server.runBasic();
    server.runStandaloneMode();
  }

  private final SessionService sessionService;
  private final VersionService versionService;
  private final ControlService controlService;
  private final SessionManager sessionManager;
  @Nullable private final MonitorPipelineLauncher monitorPipelineLauncher;
  private final ListeningExecutorService threadPool;
  private final ListeningScheduledExecutorService scheduledThreadPool;
  private final ExecMode execMode;
  private final EventBus globalInternalEventBus;
  private final LogManager<LogRecords> logManager;
  private final ClientApi clientApi;
  private final SystemUtil systemUtil;
  private final SystemInfoPrinter systemInfoPrinter;
  private final boolean enableDatabase;
  private final DatabaseConnections olcDatabaseConnections;
  private final TablesLister tablesLister;
  private final ServerUtils serverUtils;

  @Inject
  OlcServer(
      SessionService sessionService,
      VersionService versionService,
      ControlService controlService,
      SessionManager sessionManager,
      @Nullable MonitorPipelineLauncher monitorPipelineLauncher,
      ListeningExecutorService threadPool,
      ListeningScheduledExecutorService scheduledThreadPool,
      ExecMode execMode,
      @GlobalInternalEventBus EventBus globalInternalEventBus,
      LogManager<LogRecords> logManager,
      ClientApi clientApi,
      SystemUtil systemUtil,
      SystemInfoPrinter systemInfoPrinter,
      @EnableDatabase boolean enableDatabase,
      @OlcDatabaseConnections DatabaseConnections olcDatabaseConnections,
      TablesLister tablesLister,
      ServerUtils serverUtils) {
    this.sessionService = sessionService;
    this.versionService = versionService;
    this.controlService = controlService;
    this.sessionManager = sessionManager;
    this.monitorPipelineLauncher = monitorPipelineLauncher;
    this.threadPool = threadPool;
    this.scheduledThreadPool = scheduledThreadPool;
    this.execMode = execMode;
    this.globalInternalEventBus = globalInternalEventBus;
    this.logManager = logManager;
    this.clientApi = clientApi;
    this.systemUtil = systemUtil;
    this.systemInfoPrinter = systemInfoPrinter;
    this.enableDatabase = enableDatabase;
    this.olcDatabaseConnections = olcDatabaseConnections;
    this.tablesLister = tablesLister;
    this.serverUtils = serverUtils;
  }

  /**
   * Runs basic logic for both standalone mode and embedded mode.
   *
   * <p>Major ones include:
   *
   * <ul>
   *   <li>Initializes OmniLab client API.
   *   <li>Initializes exec mode (e.g., local device manager).
   *   <li>Starts session manager.
   * </ul>
   */
  private void runBasic() {
    // Logs version.
    logger.atInfo().log(
        "OLC version: lab-%s%s",
        Version.LAB_VERSION,
        VersionUtil.getBuildVersion()
            .map(
                buildVersion ->
                    String.format(" %s", ProtoTextFormat.shortDebugString(buildVersion)))
            .orElse(""));

    // Initializes ClientApi.
    clientApi.initializeSingleton();

    // Starts exec mode.
    logger.atInfo().log("Starting %s exec mode", execMode.getClass().getSimpleName());
    logFailure(
        threadPool.submit(threadRenaming(new ExecModeInitializer(), () -> "exec-mode-initializer")),
        Level.SEVERE,
        "Fatal error while initializing %s exec mode",
        execMode.getClass().getSimpleName());

    // Starts session manager.
    sessionManager.start();

    // Periodically prints memory usage info.
    logFailure(
        scheduledThreadPool.scheduleWithFixedDelay(
            threadRenaming(
                () -> logger.atInfo().log("OLC server memory info: %s", systemUtil.getMemoryInfo()),
                () -> "memory-info-dumper"),
            DUMP_MEMORY_INFO_INTERVAL,
            DUMP_MEMORY_INFO_INTERVAL),
        Level.SEVERE,
        "Fatal error while dumping memory info.");
  }

  /**
   * Runs logic for standalone mode, in which the server is not embedded in another component.
   *
   * <p>Major ones include:
   *
   * <ul>
   *   <li>Starts streaming log manager.
   *   <li>Connects to database.
   *   <li>Starts gRPC servers.
   *   <li>Starts monitoring.
   *   <li>Resumes unfinished sessions.
   * </ul>
   */
  private void runStandaloneMode() throws MobileHarnessException, InterruptedException {
    // Starts log manager.
    logManager.start();

    // Connects to database.
    if (enableDatabase) {
      Properties properties = new Properties();
      Flags.instance().olcDatabaseJdbcProperty.getNonNull().forEach(properties::setProperty);
      olcDatabaseConnections.initialize(
          Flags.instance().olcDatabaseJdbcUrl.getNonNull(),
          properties,
          /* statementCacheSize= */ 100);

      // Prints table names.
      logger.atInfo().log(
          "OLC database tables: %s", tablesLister.listTables(olcDatabaseConnections));
    }

    // Starts RPC servers.
    LifecycleManager lifecycleManager = startRpcServers();

    // Starts monitoring.
    if (Flags.instance().enableCloudPubsubMonitoring.getNonNull()
        && monitorPipelineLauncher != null) {
      logger.atInfo().log("Starting monitoring service.");
      monitorPipelineLauncher.start();
    }

    // Resumes all the unfinished sessions.
    logger.atInfo().log("Resuming unfinished sessions.");
    logFailure(
        threadPool.submit(threadRenaming(sessionManager::resumeSessions, () -> "resume-sessions")),
        Level.SEVERE,
        "Fatal error while resuming unfinished sessions.");

    // Prints signal.
    logger.atInfo().log("Servers have started: %s", SERVER_STARTED_SIGNAL);
    logger.atInfo().log(
        "Process info: pid=%s, memory_info=[%s]",
        ProcessHandle.current().pid(), systemUtil.getMemoryInfo());

    // Waits for termination.
    lifecycleManager.awaitTermination();
    logger.atInfo().log("Exiting...");
    System.exit(0);
  }

  private LifecycleManager startRpcServers() {
    // Creates extra services.
    ImmutableList<BindableService> extraServicesForNonWorker;
    ImmutableList<BindableService> extraServicesForWorker;
    List<BindableService> dualModeServices = new ArrayList<>();
    if (execMode instanceof ServiceProvider serviceProvider) {
      extraServicesForNonWorker = serviceProvider.provideServices();
      extraServicesForWorker = serviceProvider.provideServicesForWorkers();
      dualModeServices.addAll(serviceProvider.provideDualModeServices());
    } else {
      extraServicesForNonWorker = ImmutableList.of();
      extraServicesForWorker = ImmutableList.of();
    }

    // Creates RPC server for non-worker.
    ImmutableList.Builder<LabeledServer> servers = ImmutableList.builder();
    ServerBuilder<?> serverBuilderForNonWorker =
        Flags.instance().useAlts.getNonNull()
            ? ServerBuilderFactory.createAltsServerBuilder(
                Flags.instance().olcServerPort.getNonNull(),
                ImmutableSet.copyOf(Flags.instance().restrictOlcServiceToUsers.getNonNull()))
            : ServerBuilderFactory.createNettyServerBuilder(
                Flags.instance().olcServerPort.getNonNull(), /* localhost= */ false);
    serverBuilderForNonWorker
        .executor(threadPool)
        .intercept(new ClientAddressServerInterceptor())
        .addService(controlService)
        .addService(sessionService);
    dualModeServices.add(versionService);
    extraServicesForNonWorker.forEach(serverBuilderForNonWorker::addService);
    if (Flags.instance().enableGrpcRelay.getNonNull()) {
      serverBuilderForNonWorker =
          serverUtils.enableGrpcRelay(serverBuilderForNonWorker, dualModeServices);
    } else {
      dualModeServices.forEach(serverBuilderForNonWorker::addService);
    }
    servers.add(LabeledServer.create(serverBuilderForNonWorker.build(), "for-non-worker"));

    // Creates RPC server for worker.
    if (!extraServicesForWorker.isEmpty()) {
      ServerBuilder<?> serverBuilderForWorker =
          ServerBuilderFactory.createNettyServerBuilder(
                  Flags.instance().atsWorkerGrpcPort.getNonNull(), /* localhost= */ false)
              .executor(threadPool)
              .intercept(new ClientAddressServerInterceptor());
      extraServicesForWorker.forEach(serverBuilderForWorker::addService);
      servers.add(LabeledServer.create(serverBuilderForWorker.build(), "for-worker"));
    }

    // Starts RPC servers.
    LifecycleManager lifecycleManager = new LifecycleManager(threadPool, servers.build());
    controlService.setLifecycleManager(lifecycleManager);
    lifecycleManager.start();

    return lifecycleManager;
  }

  private class ExecModeInitializer implements Callable<Void> {

    @Override
    public Void call() throws InterruptedException {
      execMode.initialize(globalInternalEventBus);
      return null;
    }
  }

  private void onShutdown() {
    logger.atInfo().log("OLC server shutting down...");
    if (monitorPipelineLauncher != null) {
      logger.atInfo().log("Stopping monitoring service.");
      monitorPipelineLauncher.stop();
    }
  }

  /** Validates whether the database can be enabled. */
  private static boolean validateDatabase() {
    String jdbcUrl = Flags.instance().olcDatabaseJdbcUrl.get();
    if (Strings.isNullOrEmpty(jdbcUrl)) {
      return false;
    }
    if (jdbcUrl.startsWith("jdbc:mysql:")) {
      if (!checkClassExist("com.mysql.jdbc.Driver")) {
        return false;
      }
    }

    String socketFactoryClassName =
        Flags.instance().olcDatabaseJdbcProperty.getNonNull().get("socketFactory");
    if (!Strings.isNullOrEmpty(socketFactoryClassName)) {
      if (!checkClassExist(socketFactoryClassName)) {
        return false;
      }
    }

    return true;
  }

  private static boolean checkClassExist(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException e) {
      logger.atWarning().withCause(e).log("Failed to load class %s.", className);
      return false;
    }
  }
}
