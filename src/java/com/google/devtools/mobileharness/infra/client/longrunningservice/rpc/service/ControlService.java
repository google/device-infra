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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** Implementation of {@link ControlServiceGrpc}. */
public class ControlService extends ControlServiceGrpc.ControlServiceImplBase {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SessionManager sessionManager;

  /** Set in {@link #setServer}. */
  private volatile Server server;

  @Inject
  ControlService(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  public void setServer(Server server) {
    this.server = server;
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
      logger.atInfo().log("Exiting by KillServerRequest");
      server.shutdown();
      return KillServerResponse.newBuilder().setSuccessful(true).build();
    }
  }
}
