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

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceGrpc;
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
import io.grpc.stub.StreamObserver;

/** Stub of {@link SessionServiceGrpc}. */
public interface SessionStub {

  CreateSessionResponse createSession(CreateSessionRequest request) throws GrpcExceptionWithErrorId;

  RunSessionResponse runSession(RunSessionRequest request) throws GrpcExceptionWithErrorId;

  GetSessionResponse getSession(GetSessionRequest request) throws GrpcExceptionWithErrorId;

  GetAllSessionsResponse getAllSessions(GetAllSessionsRequest request)
      throws GrpcExceptionWithErrorId;

  StreamObserver<SubscribeSessionRequest> subscribeSession(
      StreamObserver<SubscribeSessionResponse> responseObserver);

  NotifySessionResponse notifySession(NotifySessionRequest request) throws GrpcExceptionWithErrorId;

  NotifyAllSessionsResponse notifyAllSessions(NotifyAllSessionsRequest request)
      throws GrpcExceptionWithErrorId;

  AbortSessionsResponse abortSessions(AbortSessionsRequest request) throws GrpcExceptionWithErrorId;

  GetSessionManagerStatusResponse getSessionManagerStatus(GetSessionManagerStatusRequest request)
      throws GrpcExceptionWithErrorId;
}
