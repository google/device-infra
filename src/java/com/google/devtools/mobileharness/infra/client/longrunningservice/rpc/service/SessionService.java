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

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionResponse;
import com.google.devtools.mobileharness.shared.util.message.FieldMaskUtils;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** Implementation of {@link SessionServiceGrpc}. */
public class SessionService extends SessionServiceGrpc.SessionServiceImplBase {

  private final SessionManager sessionManager;

  @Inject
  SessionService(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Override
  public void createSession(
      CreateSessionRequest request, StreamObserver<CreateSessionResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doCreateSession,
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getCreateSessionMethod());
  }

  @Override
  public void getSession(
      GetSessionRequest request, StreamObserver<GetSessionResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doGetSession,
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getGetSessionMethod());
  }

  private CreateSessionResponse doCreateSession(CreateSessionRequest request)
      throws MobileHarnessException {
    SessionDetail sessionDetail =
        sessionManager.addSession(request.getSessionConfig()).sessionDetail();
    return CreateSessionResponse.newBuilder().setSessionId(sessionDetail.getSessionId()).build();
  }

  private GetSessionResponse doGetSession(GetSessionRequest request) throws MobileHarnessException {
    // Calculates sub field mask for SessionDetail.
    FieldMask sessionDetailSubMask;
    if (request.hasFieldMask()) {
      sessionDetailSubMask =
          FieldMaskUtils.subFieldMask(
                  request.getFieldMask(),
                  GetSessionResponse.getDescriptor()
                      .findFieldByNumber(GetSessionResponse.SESSION_DETAIL_FIELD_NUMBER))
              .orElse(null);
    } else {
      sessionDetailSubMask = null;
    }

    SessionDetail sessionDetail =
        sessionManager.getSession(request.getSessionId().getId(), sessionDetailSubMask);
    GetSessionResponse result =
        GetSessionResponse.newBuilder().setSessionDetail(sessionDetail).build();

    // Applies the field mask if any.
    if (request.hasFieldMask()) {
      result = FieldMaskUtil.trim(request.getFieldMask(), result);
    }
    return result;
  }
}
