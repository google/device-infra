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

import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.deviceinfra.infra.client.api.mode.local.LocalMode;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import com.google.devtools.deviceinfra.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.SessionService;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service.VersionService;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

/** OLC server. */
public class OlcServer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) throws IOException, InterruptedException {
    // Parses flags.
    Flags.parse(args);

    // Creates and runs the server.
    OlcServer server = Guice.createInjector(new ServerModule()).getInstance(OlcServer.class);
    server.run(Arrays.asList(args));
  }

  private final SessionService sessionService;
  private final VersionService versionService;
  private final LocalMode localMode;
  private final EventBus globalInternalEventBus;

  @Inject
  OlcServer(
      SessionService sessionService,
      VersionService versionService,
      LocalMode localMode,
      @GlobalInternalEventBus EventBus globalInternalEventBus) {
    this.sessionService = sessionService;
    this.versionService = versionService;
    this.localMode = localMode;
    this.globalInternalEventBus = globalInternalEventBus;
  }

  private void run(List<String> args) throws IOException, InterruptedException {
    // Initializes logger.
    MobileHarnessLogger.init(PathUtil.join(DirCommon.getPublicDirRoot(), "olc_server_log"));

    logger.atInfo().log("Arguments: %s", args);

    // Starts RPC server.
    int port = Flags.instance().olcServerPort.getNonNull();
    Server server =
        NettyServerBuilder.forPort(port)
            .addService(sessionService)
            .addService(versionService)
            .build();
    server.start();

    // Starts local device manager.
    logger.atInfo().log("Starting local device manager");
    localMode.initialize(globalInternalEventBus);

    logger.atInfo().log("OLC server started, port=%s", port);

    server.awaitTermination();
  }
}
