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

package com.google.devtools.mobileharness.infra.ats.console.controller.olcserver;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientId;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.util.log.LogRecordPrinter;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/** Printer for printing OLC server streaming logs. */
@Singleton
public class ServerLogPrinter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<Code> NORMAL_CODES =
      ImmutableSet.of(Code.UNAVAILABLE, Code.CANCELLED);

  private final LogRecordPrinter logRecordPrinter;
  private final Provider<ControlStub> controlStubProvider;
  private final ServerPreparer serverPreparer;
  private final String clientId;

  private final GetLogResponseObserver responseObserver = new GetLogResponseObserver();

  private final Object lock = new Object();

  @GuardedBy("lock")
  private boolean enable;

  @GuardedBy("lock")
  private StreamObserver<GetLogRequest> requestObserver;

  @Inject
  ServerLogPrinter(
      LogRecordPrinter logRecordPrinter,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) Provider<ControlStub> controlStubProvider,
      ServerPreparer serverPreparer,
      @ClientId String clientId) {
    this.logRecordPrinter = logRecordPrinter;
    this.controlStubProvider = controlStubProvider;
    this.serverPreparer = serverPreparer;
    this.clientId = clientId;
  }

  /** Enables/disables the printer. */
  public void enable() throws MobileHarnessException, InterruptedException {
    synchronized (lock) {
      enable(!enable);
    }
  }

  /** Enables/disables the printer. */
  public void enable(boolean enable) throws MobileHarnessException, InterruptedException {
    synchronized (lock) {
      this.enable = enable;
      logger
          .atInfo()
          .with(IMPORTANCE, DEBUG)
          .log("%s server log.", enable ? "Printing" : "Stop printing");

      if (enable) {
        serverPreparer.prepareOlcServer();
        if (requestObserver == null) {
          requestObserver = requireNonNull(controlStubProvider.get()).getLog(responseObserver);
        }
        requestObserver.onNext(
            GetLogRequest.newBuilder().setEnable(true).setClientId(clientId).build());
      } else {
        if (requestObserver != null) {
          requestObserver.onCompleted();
          requestObserver = null;
        }
      }
    }
  }

  private class GetLogResponseObserver implements StreamObserver<GetLogResponse> {

    @Override
    public void onNext(GetLogResponse response) {
      for (LogRecord logRecord : response.getLogRecords().getLogRecordList()) {
        logRecordPrinter.printLogRecord(logRecord);
      }
    }

    @Override
    public void onError(Throwable e) {
      if (e instanceof StatusRuntimeException
          && NORMAL_CODES.contains(((StatusRuntimeException) e).getStatus().getCode())) {
        logger.atInfo().with(IMPORTANCE, IMPORTANT).log("Stop getting from server since it stops");
      } else {
        logger
            .atWarning()
            .with(IMPORTANCE, IMPORTANT)
            .withCause(e)
            .log("Failed to get log from server");
      }
      doOnCompleted();
    }

    @Override
    public void onCompleted() {
      doOnCompleted();
    }

    private void doOnCompleted() {
      synchronized (lock) {
        requestObserver = null;
      }
    }
  }
}
