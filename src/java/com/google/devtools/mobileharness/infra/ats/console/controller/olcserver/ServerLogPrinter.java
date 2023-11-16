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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import io.grpc.stub.StreamObserver;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Printer for printing OLC server streaming logs. */
@Singleton
public class ServerLogPrinter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConsoleUtil consoleUtil;
  private final ControlStub controlStub;
  private final ServerPreparer serverPreparer;

  private final GetLogResponseObserver responseObserver = new GetLogResponseObserver();

  private final Object lock = new Object();

  @GuardedBy("lock")
  private boolean enable;

  @GuardedBy("lock")
  private StreamObserver<GetLogRequest> requestObserver;

  @Inject
  ServerLogPrinter(
      ConsoleUtil consoleUtil,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) ControlStub controlStub,
      ServerPreparer serverPreparer) {
    this.consoleUtil = consoleUtil;
    this.controlStub = controlStub;
    this.serverPreparer = serverPreparer;
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
      consoleUtil.printlnStdout("%s server log.", enable ? "Printing" : "Stop printing");

      if (enable) {
        serverPreparer.prepareOlcServer();
        if (requestObserver == null) {
          requestObserver = controlStub.getLog(responseObserver);
        }
        requestObserver.onNext(GetLogRequest.newBuilder().setEnable(true).build());
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
        consoleUtil.printStderr(logRecord.getFormattedLogRecord());
      }
    }

    @Override
    public void onError(Throwable e) {
      logger.atWarning().withCause(e).log("Failed to get log from server");
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
