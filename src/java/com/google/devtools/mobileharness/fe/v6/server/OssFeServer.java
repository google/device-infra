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

package com.google.devtools.mobileharness.fe.v6.server;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.fe.v6.service.device.DeviceServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.device.DeviceServiceModule;
import com.google.devtools.mobileharness.fe.v6.shared.util.concurrent.OssExecutorModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.IOException;

/** Main class for the open-source FE gRPC server. */
public final class OssFeServer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final int port;
  private final Server server;

  public OssFeServer(int port) {
    this.port = port;
    Injector injector = Guice.createInjector(new OssExecutorModule(), new DeviceServiceModule());
    this.server =
        ServerBuilder.forPort(port)
            .addService(injector.getInstance(DeviceServiceGrpcImpl.class))
            .addService(ProtoReflectionService.newInstance())
            .build();
  }

  /** Starts the server. */
  public void start() throws IOException {
    server.start();
    logger.atInfo().log("OSS FE Server started on port %d", port);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.atWarning().log(
                      "*** shutting down gRPC server since JVM is shutting down");
                  OssFeServer.this.stop();
                  logger.atWarning().log("*** server shut down");
                }));
  }

  /** Stops the server. */
  private void stop() {
    if (server != null) {
      server.shutdown();
    }
  }

  /** Await termination on the main thread since the grpc library uses daemon threads. */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    OssFeServer server = new OssFeServer(8080);
    server.start();
    server.blockUntilShutdown();
  }
}
