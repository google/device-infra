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

import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerDirs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.ServiceProvider;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.ControlService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.SessionService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.VersionService;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.inject.Inject;

/** OLC server. */
public class OlcServer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) throws IOException, InterruptedException {
    // Parses flags.
    Flags.parse(args);

    // Creates and runs the server.
    Instant serverStartTime = Instant.now();
    OlcServer server =
        Guice.createInjector(
                new ServerModule(Flags.instance().enableAtsMode.getNonNull(), serverStartTime))
            .getInstance(OlcServer.class);
    server.run(Arrays.asList(args));
  }

  private final SessionService sessionService;
  private final VersionService versionService;
  private final ControlService controlService;
  private final ListeningExecutorService threadPool;
  private final ExecMode execMode;
  private final EventBus globalInternalEventBus;
  private final LogManager<GetLogResponse> logManager;
  private final ClientApi clientApi;

  @Inject
  OlcServer(
      SessionService sessionService,
      VersionService versionService,
      ControlService controlService,
      ListeningExecutorService threadPool,
      ExecMode execMode,
      @GlobalInternalEventBus EventBus globalInternalEventBus,
      LogManager<GetLogResponse> logManager,
      ClientApi clientApi) {
    this.sessionService = sessionService;
    this.versionService = versionService;
    this.controlService = controlService;
    this.threadPool = threadPool;
    this.execMode = execMode;
    this.globalInternalEventBus = globalInternalEventBus;
    this.logManager = logManager;
    this.clientApi = clientApi;
  }

  private void run(List<String> args) throws IOException, InterruptedException {
    // Initializes logger.
    MobileHarnessLogger.init(
        OlcServerDirs.getLogDir(), ImmutableList.of(logManager.getLogHandler()));

    logger.atInfo().log("Arguments: %s", args);

    // Initializes TestMessageManager.
    TestMessageManager.createInstance(clientApi::getTestMessagePoster);

    // Starts log manager.
    logManager.start();
    LogRecorder.getInstance().initialize(logManager);

    // Starts RPC server.
    int port = Flags.instance().olcServerPort.getNonNull();
    ImmutableList<BindableService> extraServices =
        execMode instanceof ServiceProvider
            ? ((ServiceProvider) execMode).provideServices()
            : ImmutableList.of();
    NettyServerBuilder serverBuilder =
        NettyServerBuilder.forPort(port)
            .executor(threadPool)
            .addService(controlService)
            .addService(sessionService)
            .addService(versionService);
    extraServices.forEach(serverBuilder::addService);
    Server server = serverBuilder.build();
    controlService.setServer(server);
    server.start();

    logger.atInfo().log("Starting %s exec mode", execMode.getClass().getSimpleName());
    logFailure(
        threadPool.submit(threadRenaming(new ExecModeInitializer(), () -> "exec-mode-initializer")),
        Level.SEVERE,
        "Fatal error while initializing %s exec mode",
        execMode.getClass().getSimpleName());

    logger.atInfo().log("OLC server started, port=%s", port);

    server.awaitTermination();
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
