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
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.ControlService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.SessionService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.VersionService;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
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
        Guice.createInjector(new ServerModule(serverStartTime)).getInstance(OlcServer.class);
    server.run(Arrays.asList(args));
  }

  private final SessionService sessionService;
  private final VersionService versionService;
  private final ControlService controlService;
  private final ListeningExecutorService threadPool;
  private final LocalMode localMode;
  private final EventBus globalInternalEventBus;
  private final LogManager<GetLogResponse> logManager;
  private final ClientApi clientApi;

  @Inject
  OlcServer(
      SessionService sessionService,
      VersionService versionService,
      ControlService controlService,
      ListeningExecutorService threadPool,
      LocalMode localMode,
      @GlobalInternalEventBus EventBus globalInternalEventBus,
      LogManager<GetLogResponse> logManager,
      ClientApi clientApi) {
    this.sessionService = sessionService;
    this.versionService = versionService;
    this.controlService = controlService;
    this.threadPool = threadPool;
    this.localMode = localMode;
    this.globalInternalEventBus = globalInternalEventBus;
    this.logManager = logManager;
    this.clientApi = clientApi;
  }

  private void run(List<String> args) throws IOException, InterruptedException {
    // Initializes logger.
    MobileHarnessLogger.init(
        PathUtil.join(DirCommon.getPublicDirRoot(), "olc_server_log"),
        ImmutableList.of(logManager.getLogHandler()));

    logger.atInfo().log("Arguments: %s", args);

    // Initializes TestMessageManager.
    TestMessageManager.createInstance(clientApi::getTestMessagePoster);

    // Starts log manager.
    logManager.start();
    LogRecorder.getInstance().initialize(logManager);

    // Starts RPC server.
    int port = Flags.instance().olcServerPort.getNonNull();
    Server server =
        NettyServerBuilder.forPort(port)
            .addService(controlService)
            .addService(sessionService)
            .addService(versionService)
            .executor(threadPool)
            .build();
    controlService.setServer(server);
    server.start();

    // Starts local device manager.
    logger.atInfo().log("Starting local device manager");
    logFailure(
        threadPool.submit(
            threadRenaming(new LocalModeInitializer(), () -> "local-mode-initializer")),
        Level.SEVERE,
        "Fatal error while initializing local mode");

    logger.atInfo().log("OLC server started, port=%s", port);

    server.awaitTermination();
    logger.atInfo().log("Exiting...");
    System.exit(0);
  }

  private class LocalModeInitializer implements Callable<Void> {

    @Override
    public Void call() throws InterruptedException {
      localMode.initialize(globalInternalEventBus);
      return null;
    }
  }
}
