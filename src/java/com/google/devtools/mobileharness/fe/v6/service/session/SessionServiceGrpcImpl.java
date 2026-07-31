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

package com.google.devtools.mobileharness.fe.v6.service.session;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionFileRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionFileResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionLogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionLogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.SessionServiceGrpc;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** gRPC implementation of the SessionService. */
public final class SessionServiceGrpcImpl extends SessionServiceGrpc.SessionServiceImplBase {

  private final SessionServiceLogic logic;
  private final ListeningExecutorService executor;

  @Inject
  SessionServiceGrpcImpl(SessionServiceLogic logic, ListeningExecutorService executor) {
    this.logic = logic;
    this.executor = executor;
  }

  @Override
  public void getSession(
      GetSessionRequest request, StreamObserver<GetSessionResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getSession,
        executor,
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getGetSessionMethod());
  }

  @Override
  public void getSessionLog(
      GetSessionLogRequest request, StreamObserver<GetSessionLogResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getSessionLog,
        executor,
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getGetSessionLogMethod());
  }

  @Override
  public void getSessionFile(
      GetSessionFileRequest request, StreamObserver<GetSessionFileResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getSessionFile,
        executor,
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getGetSessionFileMethod());
  }
}
