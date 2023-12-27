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

package com.google.devtools.mobileharness.infra.ats.local;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.DeviceInfraServiceUtil;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.local.proto.AtsLocalSessionPluginProto.AtsLocalSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.local.proto.AtsLocalSessionPluginProto.AtsLocalSessionPluginOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfigs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.RunSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.RunSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import java.nio.file.Path;
import java.util.Arrays;
import javax.inject.Inject;

/** ATS local runner. */
public class AtsLocalRunner {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ATS_LOCAL_PLUGIN_CLASS =
      "com.google.devtools.mobileharness.infra.ats.local.sessionplugin.AtsLocalSessionPlugin";

  public static void main(String[] args) throws InterruptedException, MobileHarnessException {
    ImmutableList<String> deviceInfraServiceFlags =
        DeviceInfraServiceUtil.parseDeviceInfraServiceFlagsFromSystemProperty();
    // Parse the system property and args
    ImmutableList<String> allFlags =
        ImmutableList.<String>builder()
            .addAll(deviceInfraServiceFlags)
            .addAll(Arrays.asList(args))
            .build();
    DeviceInfraServiceUtil.parseFlags(allFlags);

    Injector injector =
        Guice.createInjector(
            new AtsLocalRunnerModule(AtsLocalRunner::getOlcServerBinary, deviceInfraServiceFlags));
    AtsLocalRunner atsLocalRunner = injector.getInstance(AtsLocalRunner.class);
    atsLocalRunner.run();
  }

  private final ServerPreparer serverPreparer;
  private final ControlStub controlStub;
  private final SessionStub sessionStub;

  @Inject
  AtsLocalRunner(
      ServerPreparer serverPreparer,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) ControlStub controlStub,
      @ServerStub(ServerStub.Type.SESSION_SERVICE) SessionStub sessionStub) {
    this.serverPreparer = serverPreparer;
    this.controlStub = controlStub;
    this.sessionStub = sessionStub;
  }

  public void run() throws InterruptedException, MobileHarnessException {
    serverPreparer.prepareOlcServer();
    enableServerLog();

    runSession();

    if (!serverPreparer.killExistingServer()) {
      logger.atWarning().log("Failed to kill OLC server.");
    }
  }

  private void enableServerLog() {
    StreamObserver<GetLogRequest> requestStreamObserver =
        controlStub.getLog(new ServerLogObserver());
    requestStreamObserver.onNext(GetLogRequest.newBuilder().setEnable(true).build());
  }

  private void runSession() {
    RunSessionRequest request =
        RunSessionRequest.newBuilder().setSessionConfig(buildSessionConfig()).build();
    try {
      RunSessionResponse response = sessionStub.runSession(request);
      printResult(response);
    } catch (GrpcExceptionWithErrorId exception) {
      logger.atSevere().withCause(exception).log("Failed to run session");
    }
  }

  private static SessionConfig buildSessionConfig() {
    return SessionConfig.newBuilder()
        .setSessionName("ats-local-session")
        .setSessionPluginConfigs(
            SessionPluginConfigs.newBuilder().addSessionPluginConfig(buildSessionPluginConfig()))
        .build();
  }

  private static SessionPluginConfig buildSessionPluginConfig() {
    return SessionPluginConfig.newBuilder()
        .setLoadingConfig(
            SessionPluginLoadingConfig.newBuilder().setPluginClassName(ATS_LOCAL_PLUGIN_CLASS))
        .setExecutionConfig(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        AtsLocalSessionPluginConfig.newBuilder()
                            .setTestConfig(Flags.instance().alrTestConfig.getNonNull())
                            .addAllArtifact(Flags.instance().alrArtifacts.getNonNull())
                            .addAllDeviceSerial(Flags.instance().alrSerials.getNonNull())
                            .build())))
        .build();
  }

  private void printResult(RunSessionResponse response) {
    if (response.getSessionDetail().hasSessionRunnerError()) {
      logger.atSevere()
          .withCause(
              ErrorModelConverter.toDeserializedException(
                  response.getSessionDetail().getSessionRunnerError()))
          .log("Session runner error");
    } else {
      SessionPluginOutput sessionPluginOutput =
          response
              .getSessionDetail()
              .getSessionOutput()
              .getSessionPluginOutputMap()
              .getOrDefault(ATS_LOCAL_PLUGIN_CLASS, SessionPluginOutput.getDefaultInstance());
      if (sessionPluginOutput.hasOutput()) {
        try {
          AtsLocalSessionPluginOutput output =
              sessionPluginOutput.getOutput().unpack(AtsLocalSessionPluginOutput.class);
          if (output.getResult().equals(TestResult.PASS)) {
            System.out.printf("\n\033[1;32mJob result: %s\033[0m\n", output.getResult()); // Green
          } else {
            System.out.printf("\n\033[1;31mJob result: %s\033[0m\n", output.getResult()); // Red
            System.out.println(output.getResultDetail());
          }
        } catch (InvalidProtocolBufferException e) {
          logger.atSevere().withCause(e).log("Failed to unpack AtsLocalSessionPluginOutput");
        }
      }
      Path logDir = Path.of(Flags.instance().publicDir.getNonNull()).toAbsolutePath();
      System.out.printf("\nATS logs have been saved to %s/olc_server_log\n", logDir);
      System.out.printf(
          "\nTo access logs, press \"ctrl\" and click on:\n"
              + "\033[1;35mfile://%s/olc_server_log/log0.txt\033[0m\n\n", // Magenta
          logDir);
    }
  }

  public static Path getOlcServerBinary() {
    return Path.of(Flags.instance().alrOlcServerPath.getNonNull());
  }

  private static class ServerLogObserver implements StreamObserver<GetLogResponse> {
    @Override
    public void onNext(GetLogResponse response) {
      for (LogRecord logRecord : response.getLogRecords().getLogRecordList()) {
        // Print server log without format
        System.out.print(logRecord.getFormattedLogRecord());
      }
    }

    @Override
    public void onError(Throwable e) {
      logger.atWarning().withCause(e).log("Failed to get log from OLC server");
    }

    @Override
    public void onCompleted() {}
  }
}