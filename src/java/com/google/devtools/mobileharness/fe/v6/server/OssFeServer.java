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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.fe.v6.server.Annotations.ServerPort;
import com.google.devtools.mobileharness.fe.v6.service.admin.AdminServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.admin.AdminServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.config.ConfigServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.config.ConfigServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.device.DeviceServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.device.DeviceServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.host.HostServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.host.HostServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.job.JobServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.job.OssJobServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.search.SearchServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.search.SearchServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.CoreFleetDataRefresher;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogRefresher;
import com.google.devtools.mobileharness.fe.v6.service.session.OssSessionServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.session.SessionServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.service.shared.OssStubsModule;
import com.google.devtools.mobileharness.fe.v6.service.test.OssTestServiceModule;
import com.google.devtools.mobileharness.fe.v6.service.test.TestServiceGrpcImpl;
import com.google.devtools.mobileharness.fe.v6.shared.util.concurrent.OssExecutorModule;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.flags.core.FlagsManager;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.IOException;
import java.time.Duration;
import java.time.InstantSource;
import javax.inject.Inject;

/** Main class for the open-source FE gRPC server. */
public final class OssFeServer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(2);

  private final int port;
  private final DeviceServiceGrpcImpl deviceService;
  private final HostServiceGrpcImpl hostService;
  private final ConfigServiceGrpcImpl configService;
  private final AdminServiceGrpcImpl adminService;
  private final TestServiceGrpcImpl testService;
  private final JobServiceGrpcImpl jobService;
  private final SessionServiceGrpcImpl sessionService;
  private final SearchServiceGrpcImpl searchService;
  private final CoreFleetDataRefresher refresher;
  private final DimensionCatalogRefresher dimensionCatalogRefresher;
  private volatile Server grpcServer;

  @Inject
  OssFeServer(
      DeviceServiceGrpcImpl deviceService,
      HostServiceGrpcImpl hostService,
      ConfigServiceGrpcImpl configService,
      AdminServiceGrpcImpl adminService,
      TestServiceGrpcImpl testService,
      JobServiceGrpcImpl jobService,
      SessionServiceGrpcImpl sessionService,
      SearchServiceGrpcImpl searchService,
      CoreFleetDataRefresher refresher,
      DimensionCatalogRefresher dimensionCatalogRefresher,
      @ServerPort int port) {
    this.deviceService = deviceService;
    this.hostService = hostService;
    this.configService = configService;
    this.adminService = adminService;
    this.testService = testService;
    this.jobService = jobService;
    this.sessionService = sessionService;
    this.searchService = searchService;
    this.refresher = refresher;
    this.dimensionCatalogRefresher = dimensionCatalogRefresher;
    this.port = port;
  }

  /** Starts the server. */
  public void start() throws IOException {
    this.grpcServer =
        ServerBuilder.forPort(port)
            .addService(deviceService)
            .addService(hostService)
            .addService(configService)
            .addService(adminService)
            .addService(testService)
            .addService(jobService)
            .addService(sessionService)
            .addService(searchService)
            .addService(ProtoReflectionService.newInstance())
            .build();
    dimensionCatalogRefresher.start();
    refresher.buildInitialIndexWithRetry();
    refresher.start(REFRESH_INTERVAL);
    grpcServer.start();
    logger.atInfo().log("FE Server started on port %d", port);
    Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));
  }

  /** Stops the server. */
  @VisibleForTesting
  void stopServer() {
    refresher.stop();
    dimensionCatalogRefresher.stop();
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
    FlagsManager.parse(args);
    Injector injector =
        Guice.createInjector(
            new OssExecutorModule(),
            new DeviceServiceModule(),
            new HostServiceModule(),
            new ConfigServiceModule(),
            new AdminServiceModule(),
            new OssTestServiceModule(),
            new OssJobServiceModule(),
            new OssSessionServiceModule(),
            new SearchServiceModule(),
            new OssStubsModule(),
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Integer.class)
                    .annotatedWith(ServerPort.class)
                    .toInstance(Flags.feGrpcPort.getNonNull());
                bind(InstantSource.class).toInstance(InstantSource.system());
              }
            });
    OssFeServer server = injector.getInstance(OssFeServer.class);
    server.start();
    server.blockUntilShutdown();
  }
}
