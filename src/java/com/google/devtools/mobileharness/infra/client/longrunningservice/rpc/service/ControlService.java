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

import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static com.google.devtools.mobileharness.shared.util.message.FieldMaskUtils.createFieldMaskPath;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager.LogRecordsConsumer;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.Failure;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.Success;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.SetLogLevelRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.SetLogLevelResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SessionFilter;
import com.google.protobuf.FieldMask;
import io.grpc.Server;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.logging.Level;
import javax.inject.Inject;

/** Implementation of {@link ControlServiceGrpc}. */
public class ControlService extends ControlServiceGrpc.ControlServiceImplBase {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final FieldMask SESSION_SUMMARY_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_ID_FIELD_NUMBER)))
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_STATUS_FIELD_NUMBER)))
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_CONFIG_FIELD_NUMBER),
                  SessionConfig.getDescriptor()
                      .findFieldByNumber(SessionConfig.SESSION_NAME_FIELD_NUMBER)))
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_OUTPUT_FIELD_NUMBER),
                  SessionOutput.getDescriptor()
                      .findFieldByNumber(SessionOutput.SESSION_TIMING_INFO_FIELD_NUMBER)))
          .build();
  private static final SessionFilter UNFINISHED_SESSION_FILTER =
      SessionFilter.newBuilder()
          .setSessionStatusNameRegex(
              ImmutableList.of(SessionStatus.SESSION_SUBMITTED, SessionStatus.SESSION_RUNNING)
                  .stream()
                  .map(SessionStatus::name)
                  .collect(joining("|")))
          .build();

  private final LogManager<GetLogResponse> logManager;
  private final SessionManager sessionManager;
  private final ListeningScheduledExecutorService threadPool;

  /** Set in {@link #setServer}. */
  private volatile Server server;

  @Inject
  ControlService(
      LogManager<GetLogResponse> logManager,
      SessionManager sessionManager,
      ListeningScheduledExecutorService threadPool) {
    this.logManager = logManager;
    this.sessionManager = sessionManager;
    this.threadPool = threadPool;
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
        this::doKillServer,
        ControlServiceGrpc.getServiceDescriptor(),
        ControlServiceGrpc.getKillServerMethod());
  }

  @Override
  public StreamObserver<GetLogRequest> getLog(StreamObserver<GetLogResponse> responseObserver) {
    return new GetLogRequestStreamObserver(responseObserver);
  }

  @Override
  public void setLogLevel(
      SetLogLevelRequest request, StreamObserver<SetLogLevelResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doSetLogLevel,
        ControlServiceGrpc.getServiceDescriptor(),
        ControlServiceGrpc.getSetLogLevelMethod());
  }

  private KillServerResponse doKillServer(KillServerRequest request) {
    KillServerResponse.Builder responseBuilder = KillServerResponse.newBuilder();
    responseBuilder.setServerPid(ProcessHandle.current().pid());

    ImmutableList<SessionDetail> unfinishedSessions =
        sessionManager.getAllSessions(SESSION_SUMMARY_FIELD_MASK, UNFINISHED_SESSION_FILTER);

    if (unfinishedSessions.isEmpty()) {
      logger.atInfo().log("Exiting by KillServerRequest");
      server.shutdown();
      logFailure(
          threadPool.schedule(
              threadRenaming(server::shutdownNow, () -> "server-shutdown"), Duration.ofSeconds(3L)),
          Level.SEVERE,
          "Fatal error while shutting down server");
      return responseBuilder.setSuccess(Success.getDefaultInstance()).build();
    } else {
      KillServerResponse response =
          responseBuilder
              .setFailure(Failure.newBuilder().addAllUnfinishedSessions(unfinishedSessions))
              .build();
      logger.atInfo().log(
          "KillServerRequest is rejected due to unfinished sessions, response=[%s]",
          shortDebugString(response));
      return response;
    }
  }

  private SetLogLevelResponse doSetLogLevel(SetLogLevelRequest request) {
    logManager.getLogHandler().setLevel(Level.parse(Ascii.toUpperCase(request.getLevel())));
    return SetLogLevelResponse.getDefaultInstance();
  }

  private class GetLogRequestStreamObserver implements StreamObserver<GetLogRequest> {

    private final ForwardingLogRecordHandler forwardingLogRecordHandler;

    private GetLogRequestStreamObserver(StreamObserver<GetLogResponse> responseObserver) {
      this.forwardingLogRecordHandler = new ForwardingLogRecordHandler(responseObserver);
    }

    @Override
    public void onNext(GetLogRequest value) {
      if (value.getEnable()) {
        logManager.addConsumer(forwardingLogRecordHandler);
      } else {
        logManager.removeConsumer(forwardingLogRecordHandler);
      }
    }

    @Override
    public void onError(Throwable error) {
      doOnCompleted();
      if (error instanceof StatusRuntimeException) {
        if (((StatusRuntimeException) error).getStatus().getCode().equals(Code.CANCELLED)) {
          return;
        }
      }
      logger.atWarning().withCause(error).log("GetLog RPC error");
    }

    @Override
    public void onCompleted() {
      doOnCompleted();
    }

    private void doOnCompleted() {
      logManager.removeConsumer(forwardingLogRecordHandler);
      forwardingLogRecordHandler.onCompleted();
    }
  }

  private static class ForwardingLogRecordHandler implements LogRecordsConsumer<GetLogResponse> {

    private final StreamObserver<GetLogResponse> responseObserver;

    private ForwardingLogRecordHandler(StreamObserver<GetLogResponse> responseObserver) {
      this.responseObserver = responseObserver;
    }

    @Override
    public void consumeLogRecords(GetLogResponse getLogResponse) {
      responseObserver.onNext(getLogResponse);
    }

    private void onCompleted() {
      responseObserver.onCompleted();
    }
  }
}
