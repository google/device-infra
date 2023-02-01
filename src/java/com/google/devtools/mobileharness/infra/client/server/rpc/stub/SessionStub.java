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

package com.google.devtools.mobileharness.infra.client.server.rpc.stub;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionServiceGrpc;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionServiceGrpc.SessionServiceBlockingStub;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionServiceProto.CreateSessionResponse;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionServiceProto.GetSessionResponse;
import io.grpc.Channel;

/** Stub of {@link SessionServiceGrpc}. */
public class SessionStub {

  private final SessionServiceBlockingStub sessionServiceStub;

  public SessionStub(Channel channel) {
    this.sessionServiceStub = SessionServiceGrpc.newBlockingStub(channel);
  }

  public CreateSessionResponse createSession(CreateSessionRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        sessionServiceStub::createSession,
        request,
        InfraErrorId.OLCS_STUB_CREATE_SESSION_ERROR,
        "Failed to create session");
  }

  public GetSessionResponse getSession(GetSessionRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        sessionServiceStub::getSession,
        request,
        InfraErrorId.OLCS_STUB_GET_SESSION_ERROR,
        "Failed to get session");
  }
}
