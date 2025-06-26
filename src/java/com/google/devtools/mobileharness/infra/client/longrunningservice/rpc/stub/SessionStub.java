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

package com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub;

import static com.google.devtools.mobileharness.shared.util.comm.stub.Stubs.withDeadline;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceGrpc.SessionServiceBlockingStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceGrpc.SessionServiceStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.AbortSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.AbortSessionsResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetAllSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetAllSessionsResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionManagerStatusRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionManagerStatusResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifyAllSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifyAllSessionsResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifySessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifySessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.RunSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.RunSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SubscribeSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SubscribeSessionResponse;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.time.Duration;

/** Stub of {@link SessionServiceGrpc}. */
public class SessionStub {

  private final SessionServiceBlockingStub sessionServiceBlockingStub;
  private final SessionServiceStub sessionServiceStub;

  public SessionStub(Channel channel) {
    this.sessionServiceBlockingStub = SessionServiceGrpc.newBlockingStub(channel);
    this.sessionServiceStub = SessionServiceGrpc.newStub(channel);
  }

  public CreateSessionResponse createSession(CreateSessionRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(sessionServiceBlockingStub, Duration.ofMinutes(2L))::createSession,
        request,
        InfraErrorId.OLCS_STUB_CREATE_SESSION_ERROR,
        "Failed to create session");
  }

  public RunSessionResponse runSession(RunSessionRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(sessionServiceBlockingStub, Duration.ofMinutes(2L))::runSession,
        request,
        InfraErrorId.OLCS_STUB_RUN_SESSION_ERROR,
        "Failed to run session");
  }

  public GetSessionResponse getSession(GetSessionRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(sessionServiceBlockingStub, Duration.ofSeconds(40L))::getSession,
        request,
        InfraErrorId.OLCS_STUB_GET_SESSION_ERROR,
        "Failed to get session");
  }

  public GetAllSessionsResponse getAllSessions(GetAllSessionsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(sessionServiceBlockingStub, Duration.ofSeconds(40L))::getAllSessions,
        request,
        InfraErrorId.OLCS_STUB_GET_ALL_SESSIONS_ERROR,
        "Failed to get all sessions");
  }

  public StreamObserver<SubscribeSessionRequest> subscribeSession(
      StreamObserver<SubscribeSessionResponse> responseObserver) {
    return sessionServiceStub.subscribeSession(responseObserver);
  }

  public NotifySessionResponse notifySession(NotifySessionRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(sessionServiceBlockingStub, Duration.ofSeconds(40L))::notifySession,
        request,
        InfraErrorId.OLCS_STUB_NOTIFY_SESSION_ERROR,
        "Failed to notify a specific session");
  }

  public NotifyAllSessionsResponse notifyAllSessions(NotifyAllSessionsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(sessionServiceBlockingStub, Duration.ofSeconds(40L))::notifyAllSessions,
        request,
        InfraErrorId.OLCS_STUB_NOTIFY_SESSION_ERROR,
        "Failed to notify all sessions ");
  }

  public AbortSessionsResponse abortSessions(AbortSessionsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(sessionServiceBlockingStub, Duration.ofSeconds(40L))::abortSessions,
        request,
        InfraErrorId.OLCS_STUB_ABORT_SESSIONS_ERROR,
        "Failed to abort sessions");
  }

  public GetSessionManagerStatusResponse getSessionManagerStatus(
      GetSessionManagerStatusRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(sessionServiceBlockingStub, Duration.ofSeconds(40L))::getSessionManagerStatus,
        request,
        InfraErrorId.OLCS_STUB_GET_SESSION_MANAGER_STATUS_ERROR,
        "Failed to get session manager status");
  }
}
