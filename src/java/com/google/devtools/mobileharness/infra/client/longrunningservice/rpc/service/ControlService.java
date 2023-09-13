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

import com.google.common.base.Ascii;
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
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.SetLogLevelRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.SetLogLevelResponse;
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
    if (sessionManager.hasUnarchivedSessions()) {
      return KillServerResponse.newBuilder().setSuccessful(false).build();
    } else {
      logger.atInfo().log("Exiting by KillServerRequest");
      server.shutdown();
      logFailure(
          threadPool.schedule(
              threadRenaming(server::shutdownNow, () -> "server-shutdown"), Duration.ofSeconds(3L)),
          Level.SEVERE,
          "Fatal error while shutting down server");
      return KillServerResponse.newBuilder().setSuccessful(true).build();
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
