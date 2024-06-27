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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.AbortSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.AbortSessionsResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetAllSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetAllSessionsResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifyAllSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifyAllSessionsResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifySessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifySessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.RunSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.RunSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SessionFilter;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SubscribeSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SubscribeSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionQueryUtil;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
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
  public void runSession(
      RunSessionRequest request, StreamObserver<RunSessionResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        this::doRunSession,
        directExecutor(),
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getRunSessionMethod());
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

  @Override
  public void getAllSessions(
      GetAllSessionsRequest request, StreamObserver<GetAllSessionsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doGetAllSessions,
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getGetAllSessionsMethod());
  }

  @Override
  public StreamObserver<SubscribeSessionRequest> subscribeSession(
      StreamObserver<SubscribeSessionResponse> responseObserver) {
    return sessionManager.subscribeSession(responseObserver);
  }

  @Override
  public void notifySession(
      NotifySessionRequest request, StreamObserver<NotifySessionResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doNotifySession,
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getNotifySessionMethod());
  }

  @Override
  public void notifyAllSessions(
      NotifyAllSessionsRequest request,
      StreamObserver<NotifyAllSessionsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doNotifyAllSessions,
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getNotifyAllSessionsMethod());
  }

  @Override
  public void abortSessions(
      AbortSessionsRequest request, StreamObserver<AbortSessionsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doAbortSessions,
        SessionServiceGrpc.getServiceDescriptor(),
        SessionServiceGrpc.getAbortSessionsMethod());
  }

  CreateSessionResponse doCreateSession(CreateSessionRequest request)
      throws MobileHarnessException {
    SessionDetail sessionDetail =
        sessionManager.addSession(request.getSessionConfig()).sessionDetail();
    return CreateSessionResponse.newBuilder().setSessionId(sessionDetail.getSessionId()).build();
  }

  private ListenableFuture<RunSessionResponse> doRunSession(RunSessionRequest request)
      throws MobileHarnessException {
    ListenableFuture<SessionDetail> finalResultFuture =
        sessionManager.addSession(request.getSessionConfig()).finalResultFuture();
    return transform(finalResultFuture, SessionService::createRunSessionResponse, directExecutor());
  }

  private GetSessionResponse doGetSession(GetSessionRequest request) throws MobileHarnessException {
    // Calculates sub field mask for SessionDetail.
    FieldMask sessionDetailFieldMask =
        SessionManager.getSessionDetailFieldMask(request).orElse(null);

    // Gets the session.
    SessionDetail sessionDetail =
        sessionManager.getSession(request.getSessionId().getId(), sessionDetailFieldMask);
    GetSessionResponse result =
        GetSessionResponse.newBuilder().setSessionDetail(sessionDetail).build();

    // Applies the field mask if any.
    if (request.hasFieldMask()) {
      result = FieldMaskUtil.trim(request.getFieldMask(), result);
    }
    return result;
  }

  private GetAllSessionsResponse doGetAllSessions(GetAllSessionsRequest request) {
    // Gets all sessions.
    ImmutableList<SessionDetail> sessions =
        sessionManager.getAllSessions(
            request.hasSessionDetailFieldMask() ? request.getSessionDetailFieldMask() : null,
            request.hasSessionFilter() ? request.getSessionFilter() : null);

    // Applies the field mask if any.
    if (request.hasSessionDetailFieldMask()) {
      FieldMask sessionDetailFieldMask = request.getSessionDetailFieldMask();
      sessions =
          sessions.stream()
              .map(session -> FieldMaskUtil.trim(sessionDetailFieldMask, session))
              .collect(toImmutableList());
    }
    return GetAllSessionsResponse.newBuilder().addAllSessionDetail(sessions).build();
  }

  private NotifySessionResponse doNotifySession(NotifySessionRequest request) {
    String sessionId = request.getSessionId().getId();
    ImmutableList<String> notifiedSessionIds =
        sessionManager.notifySessions(
            ImmutableList.of(sessionId), request.getSessionNotification());

    return NotifySessionResponse.newBuilder()
        .setSuccessful(notifiedSessionIds.contains(sessionId))
        .build();
  }

  @VisibleForTesting
  NotifyAllSessionsResponse doNotifyAllSessions(NotifyAllSessionsRequest request) {
    ImmutableList<String> sessionIds =
        getSessionIds(request.getSessionIdList(), request.getSessionFilter());
    if (sessionIds.isEmpty()) {
      return NotifyAllSessionsResponse.getDefaultInstance();
    }

    ImmutableList<String> notifiedSessionIds =
        sessionManager.notifySessions(sessionIds, request.getSessionNotification());
    return NotifyAllSessionsResponse.newBuilder()
        .addAllSessionId(
            notifiedSessionIds.stream()
                .map(sessionId -> SessionId.newBuilder().setId(sessionId).build())
                .collect(toImmutableList()))
        .build();
  }

  @VisibleForTesting
  AbortSessionsResponse doAbortSessions(AbortSessionsRequest request) {
    ImmutableList<String> sessionIds =
        getSessionIds(request.getSessionIdList(), request.getSessionFilter());
    if (sessionIds.isEmpty()) {
      return AbortSessionsResponse.getDefaultInstance();
    }

    sessionManager.abortSessions(sessionIds);
    return AbortSessionsResponse.newBuilder()
        .addAllSessionId(
            sessionIds.stream()
                .map(sessionId -> SessionId.newBuilder().setId(sessionId).build())
                .collect(toImmutableList()))
        .build();
  }

  /**
   * Gets all session ids that match the given session ids and session filter. If the given session
   * ids is empty, other filters will filter all sessions instead of no sessions.
   */
  @VisibleForTesting
  ImmutableList<String> getSessionIds(List<SessionId> sessionIds, SessionFilter sessionFilter) {
    if (sessionIds.isEmpty() && sessionFilter.equals(SessionFilter.getDefaultInstance())) {
      return ImmutableList.of();
    }

    ArrayList<String> allSessionIds = new ArrayList<>();
    allSessionIds.addAll(
        sessionManager.getAllSessions(SessionQueryUtil.SESSION_ID_FIELD_MASK, null).stream()
            .map(sessionDetail -> sessionDetail.getSessionId().getId())
            .collect(toImmutableList()));
    ImmutableList<String> givenSessionIds =
        sessionIds.stream().map(SessionId::getId).collect(toImmutableList());
    if (!givenSessionIds.isEmpty()) {
      allSessionIds.retainAll(givenSessionIds);
    }
    if (!sessionFilter.equals(SessionFilter.getDefaultInstance())) {
      ImmutableList<String> filteredSessionIds =
          filteredSessionIds =
              sessionManager
                  .getAllSessions(SessionQueryUtil.SESSION_ID_FIELD_MASK, sessionFilter)
                  .stream()
                  .map(sessionDetail -> sessionDetail.getSessionId().getId())
                  .collect(toImmutableList());
      allSessionIds.retainAll(filteredSessionIds);
    }
    return ImmutableList.copyOf(allSessionIds);
  }

  private static RunSessionResponse createRunSessionResponse(SessionDetail finalResult) {
    return RunSessionResponse.newBuilder().setSessionDetail(finalResult).build();
  }
}
