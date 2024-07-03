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
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.GrpcServer;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.WorkerGrpcServer;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerDirs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.ServiceProvider;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecords;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.ControlService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.SessionService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.VersionService;
import com.google.devtools.mobileharness.infra.monitoring.MonitorModule;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.devtools.mobileharness.shared.util.comm.server.ClientAddressServerInterceptor;
import com.google.devtools.mobileharness.shared.util.comm.server.LifecycleManager;
import com.google.devtools.mobileharness.shared.util.comm.server.LifecycleManager.LabeledServer;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.inject.Inject;

/** OLC server. */
public class OlcServer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) throws MobileHarnessException, InterruptedException {
    // Parses flags.
    Flags.parse(args);

    // Creates and runs the server.
    Instant serverStartTime = Instant.now();
    Injector injector =
        Guice.createInjector(
            new ServerModule(
                Flags.instance().enableAtsMode.getNonNull(),
                serverStartTime,
                Flags.instance().olcServerPort.getNonNull(),
                Flags.instance().atsWorkerGrpcPort.getNonNull(),
                Flags.instance().useAlts.getNonNull(),
                Flags.instance().restrictOlcServiceToUsers.getNonNull()));
    OlcServer server = injector.getInstance(OlcServer.class);

    if (Flags.instance().enableLabMonitoring.getNonNull()) {
      new MonitorModule(injector.getInstance(LabInfoProvider.class)).init();
    }

    server.run(Arrays.asList(args));
  }

  private final SessionService sessionService;
  private final VersionService versionService;
  private final ControlService controlService;
  private final ListeningExecutorService threadPool;
  private final ExecMode execMode;
  private final EventBus globalInternalEventBus;
  private final LogManager<LogRecords> logManager;
  private final ClientApi clientApi;
  private final ServerBuilder<?> serverBuilder;
  private final ServerBuilder<?> workerServerBuilder;
  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;

  @Inject
  OlcServer(
      SessionService sessionService,
      VersionService versionService,
      ControlService controlService,
      ListeningExecutorService threadPool,
      ExecMode execMode,
      @GlobalInternalEventBus EventBus globalInternalEventBus,
      LogManager<LogRecords> logManager,
      ClientApi clientApi,
      @GrpcServer ServerBuilder<?> serverBuilder,
      @WorkerGrpcServer ServerBuilder<?> workerServerBuilder,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil) {
    this.sessionService = sessionService;
    this.versionService = versionService;
    this.controlService = controlService;
    this.threadPool = threadPool;
    this.execMode = execMode;
    this.globalInternalEventBus = globalInternalEventBus;
    this.logManager = logManager;
    this.clientApi = clientApi;
    this.serverBuilder = serverBuilder;
    this.workerServerBuilder = workerServerBuilder;
    this.localFileUtil = localFileUtil;
    this.systemUtil = systemUtil;
  }

  private void run(List<String> args) throws MobileHarnessException, InterruptedException {
    // Prepares dirs.
    localFileUtil.prepareDir(DirCommon.getPublicDirRoot());
    localFileUtil.prepareDir(DirCommon.getTempDirRoot());
    localFileUtil.grantFileOrDirFullAccess(DirCommon.getPublicDirRoot());
    localFileUtil.grantFileOrDirFullAccess(DirCommon.getTempDirRoot());

    // Initializes logger.
    MobileHarnessLogger.init(
        OlcServerDirs.getLogDir(),
        ImmutableList.of(logManager.getLogHandler()),
        /* disableConsoleHandler= */ false);

    // Logs arguments.
    if (!args.isEmpty()) {
      logger.atInfo().log("arguments=%s", args);
    }

    // Initializes TestMessageManager.
    TestMessageManager.createInstance(clientApi::getTestMessagePoster);

    // Starts log manager.
    logManager.start();
    LogRecorder.getInstance().initialize(logManager);

    // Starts RPC servers.
    List<LabeledServer> managedServers = new ArrayList<>();
    ImmutableList<BindableService> extraServices =
        execMode instanceof ServiceProvider
            ? ((ServiceProvider) execMode).provideServices()
            : ImmutableList.of();
    serverBuilder
        .executor(threadPool)
        .intercept(new ClientAddressServerInterceptor())
        .addService(controlService)
        .addService(sessionService)
        .addService(versionService);
    extraServices.forEach(serverBuilder::addService);
    Server server = serverBuilder.build();
    managedServers.add(LabeledServer.create(server, "Non-worker"));
    if (execMode instanceof ServiceProvider) {
      workerServerBuilder.executor(threadPool).intercept(new ClientAddressServerInterceptor());
      ((ServiceProvider) execMode)
          .provideServicesForWorkers()
          .forEach(workerServerBuilder::addService);
      managedServers.add(LabeledServer.create(workerServerBuilder.build(), "Worker"));
    }
    LifecycleManager manager = new LifecycleManager(threadPool, managedServers);
    controlService.setLifecycleManager(manager);
    manager.start();

    logger.atInfo().log("Starting %s exec mode", execMode.getClass().getSimpleName());
    logFailure(
        threadPool.submit(threadRenaming(new ExecModeInitializer(), () -> "exec-mode-initializer")),
        Level.SEVERE,
        "Fatal error while initializing %s exec mode",
        execMode.getClass().getSimpleName());
    logger.atInfo().log("Servers have started: %s.", SERVER_STARTED_SIGNAL);
    logger.atInfo().log(
        "Process info: pid=%s, memory_info=[%s]",
        ProcessHandle.current().pid(), systemUtil.getMemoryInfo());

    manager.awaitTermination();
    logger.atInfo().log("Exiting...");
    System.exit(0);
  }

  private class ExecModeInitializer implements Callable<Void> {

    @Override
    public Void call() throws InterruptedException {
      execMode.initialize(globalInternalEventBus);
      return null;
    }
  }
}
