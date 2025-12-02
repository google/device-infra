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
import com.google.devtools.mobileharness.fe.v6.server.Annotations.ServerPort;
import com.google.devtools.mobileharness.fe.v6.service.device.DeviceServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.device.DeviceServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.host.HostServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.host.HostServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.shared.OssStubsModule;
import com.google.devtools.mobileharness.fe.v6.shared.util.concurrent.OssExecutorModule;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.IOException;
import java.time.InstantSource;
import javax.inject.Inject;

/** Main class for the open-source FE gRPC server. */
public final class OssFeServer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final int port;
  private final DeviceServiceGrpcImpl deviceService;
  private final HostServiceGrpcImpl hostService;
  private volatile Server grpcServer;

  @Inject
  OssFeServer(
      DeviceServiceGrpcImpl deviceService, HostServiceGrpcImpl hostService, @ServerPort int port) {
    this.deviceService = deviceService;
    this.hostService = hostService;
    this.port = port;
  }

  /** Starts the server. */
  public void start() throws IOException {
    this.grpcServer =
        ServerBuilder.forPort(port)
            .addService(deviceService)
            .addService(hostService)
            .addService(ProtoReflectionService.newInstance())
            .build();
    grpcServer.start();
    logger.atInfo().log("FE Server started on port %d", port);
    Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));
  }

  /** Stops the server. */
  private void stopServer() {
    if (grpcServer != null) {
      logger.atWarning().log("*** shutting down gRPC server since JVM is shutting down");
      grpcServer.shutdown();
      logger.atWarning().log("*** server shut down");
    }
  }

  /** Await termination on the main thread since the grpc library uses daemon threads. */
  private void blockUntilShutdown() throws InterruptedException {
    if (grpcServer != null) {
      grpcServer.awaitTermination();
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    Flags.parse(args);
    Injector injector =
        Guice.createInjector(
            new OssExecutorModule(),
            new DeviceServiceModule(),
            new HostServiceModule(),
            new OssStubsModule(),
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Integer.class)
                    .annotatedWith(ServerPort.class)
                    .toInstance(Flags.instance().feGrpcPort.getNonNull());
                bind(InstantSource.class).toInstance(InstantSource.system());
              }
            });
    OssFeServer server = injector.getInstance(OssFeServer.class);
    server.start();
    server.blockUntilShutdown();
  }
}
