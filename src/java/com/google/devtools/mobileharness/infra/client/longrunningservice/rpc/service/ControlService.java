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

package com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service;

import static com.google.devtools.deviceinfra.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.logging.Level;
import javax.inject.Inject;

/** Implementation of {@link ControlServiceGrpc}. */
public class ControlService extends ControlServiceGrpc.ControlServiceImplBase {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SessionManager sessionManager;
  private final ListeningScheduledExecutorService threadPool;

  @Inject
  ControlService(SessionManager sessionManager, ListeningScheduledExecutorService threadPool) {
    this.sessionManager = sessionManager;
    this.threadPool = threadPool;
  }

  @Override
  public void killServer(
      KillServerRequest request, StreamObserver<KillServerResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::killServer,
        ControlServiceGrpc.getServiceDescriptor(),
        ControlServiceGrpc.getKillServerMethod());
  }

  private KillServerResponse killServer(KillServerRequest request) {
    if (sessionManager.hasUnarchivedSessions()) {
      return KillServerResponse.newBuilder().setSuccessful(false).build();
    } else {
      logFailure(
          threadPool.schedule(
              threadRenaming(new ExitTask(), () -> "exit-task"), Duration.ofSeconds(1L)),
          Level.SEVERE,
          "Fatal error while exiting");
      return KillServerResponse.newBuilder().setSuccessful(true).build();
    }
  }

  private static class ExitTask implements Runnable {

    @SuppressWarnings("SystemExitOutsideMain")
    @Override
    public void run() {
      logger.atInfo().log("Exiting by KillServerRequest");
      System.exit(0);
    }
  }
}
