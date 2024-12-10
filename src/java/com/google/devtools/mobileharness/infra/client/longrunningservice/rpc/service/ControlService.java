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
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.common.base.Ascii;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogManager.LogRecordsConsumer;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.HeartbeatRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.HeartbeatResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.Failure;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.Success;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.SetLogLevelRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.SetLogLevelResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecords;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionQueryUtil;
import com.google.devtools.mobileharness.shared.util.comm.server.LifecycleManager;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Implementation of {@link ControlServiceGrpc}. */
public class ControlService extends ControlServiceGrpc.ControlServiceImplBase {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LogManager<LogRecords> logManager;
  private final SessionManager sessionManager;

  /** Value is unused. */
  private final Cache<String, Boolean> aliveClientIds =
      CacheBuilder.newBuilder()
          .expireAfterWrite(Duration.ofMinutes(1L))
          .removalListener(
              (RemovalListener<String, Boolean>)
                  notification -> {
                    if (notification.wasEvicted()) {
                      logger.atInfo().log("Client [%s] becomes not alive", notification.getKey());
                    }
                  })
          .build();

  /** Set in {@link #setLifecycleManager}. */
  private volatile LifecycleManager lifecycleManager;

  @Inject
  ControlService(LogManager<LogRecords> logManager, SessionManager sessionManager) {
    this.logManager = logManager;
    this.sessionManager = sessionManager;
  }

  public void setLifecycleManager(LifecycleManager lifecycleManager) {
    this.lifecycleManager = lifecycleManager;
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

  @Override
  public void heartbeat(
      HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doHeartbeat,
        ControlServiceGrpc.getServiceDescriptor(),
        ControlServiceGrpc.getHeartbeatMethod());
  }

  private KillServerResponse doKillServer(KillServerRequest request) {
    logger.atInfo().log("KillServerRequest: [%s]", shortDebugString(request));
    KillServerResponse.Builder responseBuilder =
        KillServerResponse.newBuilder().setServerPid(ProcessHandle.current().pid());

    String clientId = request.getClientId();
    ImmutableList<String> unfinishedSessionIdsFromClient =
        sessionManager
            .getAllSessions(
                SessionQueryUtil.SESSION_ID_FIELD_MASK,
                SessionQueryUtil.getAllAbortableSessionFromClientFilter(clientId))
            .stream()
            .map(SessionDetail::getSessionId)
            .map(SessionId::getId)
            .collect(toImmutableList());
    logger.atInfo().log(
        "Unfinished sessions from client [%s]: %s", clientId, unfinishedSessionIdsFromClient);
    sessionManager.abortSessions(unfinishedSessionIdsFromClient);

    ImmutableList<SessionDetail> unfinishedNotAbortedSessions =
        sessionManager.getAllSessions(
            SessionQueryUtil.SESSION_SUMMARY_FIELD_MASK,
            SessionQueryUtil.UNFINISHED_NOT_ABORTED_SESSION_FILTER);

    // Gets all clients that are still alive.
    aliveClientIds.invalidate(clientId);
    Set<String> currentAliveClientIds = aliveClientIds.asMap().keySet();

    if (unfinishedNotAbortedSessions.isEmpty() && currentAliveClientIds.isEmpty()) {
      logger.atInfo().log("Exiting by KillServerRequest");
      lifecycleManager.shutdown();
      return responseBuilder.setSuccess(Success.getDefaultInstance()).build();
    } else {
      KillServerResponse response =
          responseBuilder
              .setFailure(
                  Failure.newBuilder()
                      .addAllUnfinishedSessions(unfinishedNotAbortedSessions)
                      .addAllAliveClients(currentAliveClientIds))
              .build();
      logger.atInfo().log(
          "KillServerRequest is rejected due to unfinished (and not aborted) sessions or alive"
              + " clients, response=[%s]",
          shortDebugString(response));
      return response;
    }
  }

  private SetLogLevelResponse doSetLogLevel(SetLogLevelRequest request) {
    logManager.getLogHandler().setLevel(Level.parse(Ascii.toUpperCase(request.getLevel())));
    return SetLogLevelResponse.getDefaultInstance();
  }

  private HeartbeatResponse doHeartbeat(HeartbeatRequest request) {
    aliveClientIds.put(request.getClientId(), false);
    return HeartbeatResponse.getDefaultInstance();
  }

  private class GetLogRequestStreamObserver implements StreamObserver<GetLogRequest> {

    private final ForwardingLogRecordHandler forwardingLogRecordHandler;

    @Nullable private volatile String clientId;

    private GetLogRequestStreamObserver(StreamObserver<GetLogResponse> responseObserver) {
      this.forwardingLogRecordHandler = new ForwardingLogRecordHandler(responseObserver);
    }

    @Override
    public void onNext(GetLogRequest value) {
      if (value.getEnable()) {
        clientId = value.hasClientId() ? value.getClientId() : null;
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

    private class ForwardingLogRecordHandler implements LogRecordsConsumer<LogRecords> {

      private final StreamObserver<GetLogResponse> responseObserver;

      private ForwardingLogRecordHandler(StreamObserver<GetLogResponse> responseObserver) {
        this.responseObserver = responseObserver;
      }

      @Override
      public void consumeLogRecords(LogRecords logRecords) {
        // Filters log records.
        List<LogRecord> records = logRecords.getLogRecordList();
        List<LogRecord> filteredRecords = filterLogRecords(records);

        // Creates result proto.
        LogRecords filteredResult;
        if (records.size() == filteredRecords.size()) {
          filteredResult = logRecords;
        } else {
          filteredResult =
              logRecords.toBuilder().clearLogRecord().addAllLogRecord(filteredRecords).build();
        }

        responseObserver.onNext(GetLogResponse.newBuilder().setLogRecords(filteredResult).build());
      }

      private void onCompleted() {
        responseObserver.onCompleted();
      }

      @SuppressWarnings("MixedMutabilityReturnType")
      private List<LogRecord> filterLogRecords(List<LogRecord> logRecords) {
        String clientId = GetLogRequestStreamObserver.this.clientId;
        if (clientId == null) {
          return logRecords;
        }

        // Traverses twice to avoid new list construction.
        int numAccepted = 0;
        for (LogRecord logRecord : logRecords) {
          if (acceptLogRecord(clientId, logRecord)) {
            numAccepted++;
          }
        }
        if (numAccepted == logRecords.size()) {
          return logRecords;
        }
        if (numAccepted == 0) {
          return ImmutableList.of();
        }
        List<LogRecord> result = new ArrayList<>(numAccepted);
        for (LogRecord logRecord : logRecords) {
          if (acceptLogRecord(clientId, logRecord)) {
            result.add(logRecord);
          }
        }
        return result;
      }
    }
  }

  private static boolean acceptLogRecord(String clientId, LogRecord logRecord) {
    return !logRecord.hasClientId() || logRecord.getClientId().equals(clientId);
  }
}
