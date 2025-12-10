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

package com.google.devtools.mobileharness.service.deviceconfig;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.service.grpc.DeviceConfigGrpcImpl;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Guice;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.IOException;
import javax.inject.Inject;

/** The starter of the DeviceConfigServer. */
final class DeviceConfigServer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final int port;
  private final DeviceConfigGrpcImpl deviceConfigService;
  private volatile Server grpcServer;

  @Inject
  DeviceConfigServer(DeviceConfigGrpcImpl deviceConfigService, @Annotations.ServerPort int port) {
    this.deviceConfigService = deviceConfigService;
    this.port = port;
  }

  /** Starts the device config server and blocks until it's terminated. */
  public void start() throws IOException {
    grpcServer =
        ServerBuilder.forPort(port)
            .addService(deviceConfigService)
            .addService(ProtoReflectionService.newInstance())
            .build();
    grpcServer.start();
    logger.atInfo().log("Device config server started on port %d", port);

    Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
  }

  private void stop() {
    if (grpcServer != null) {
      logger.atInfo().log("Shutting down device config server...");
      grpcServer.shutdownNow();
      logger.atInfo().log("Device config server shut down");
      grpcServer = null;
    }
  }

  private void blockUntilShutdown() throws InterruptedException {
    if (grpcServer != null) {
      grpcServer.awaitTermination();
    }
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    Flags.parse(args);

    DeviceConfigServer deviceConfigServer =
        Guice.createInjector(new DeviceConfigModule()).getInstance(DeviceConfigServer.class);
    deviceConfigServer.start();
    deviceConfigServer.blockUntilShutdown();
  }
}
