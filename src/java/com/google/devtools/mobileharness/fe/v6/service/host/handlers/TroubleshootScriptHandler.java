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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.FilterProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.fe.v6.service.device.provider.DeviceOpsStubProvider;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.host.handlers.TroubleshootScriptRegistry.ScriptMetadata;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.ListTroubleshootScriptsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.ListTroubleshootScriptsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RunTroubleshootScriptRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RunTroubleshootScriptResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.TroubleshootScriptAction;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.DeviceOpsStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.RunTroubleshootScriptRequest.TroubleshootScript;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for troubleshoot script operations (run and list). */
@Singleton
public class TroubleshootScriptHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabInfoProvider labInfoProvider;
  private final DeviceOpsStubProvider deviceOpsStubProvider;

  @Inject
  TroubleshootScriptHandler(
      LabInfoProvider labInfoProvider, DeviceOpsStubProvider deviceOpsStubProvider) {
    this.labInfoProvider = labInfoProvider;
    this.deviceOpsStubProvider = deviceOpsStubProvider;
  }

  /** Runs a troubleshoot script on a host after verifying no devices are BUSY. */
  public ListenableFuture<RunTroubleshootScriptResponse> runTroubleshootScript(
      RunTroubleshootScriptRequest request, UniverseScope universe) {
    if (!(universe instanceof UniverseScope.SelfUniverse)) {
      return immediateFailedFuture(
          FeServiceException.unimplemented(
              "Troubleshoot scripts are only supported in the self universe."));
    }
    String hostName = request.getHostName();

    return FluentFuture.from(
            labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(hostName), universe))
        .transformAsync(
            labInfoResponse -> {
              DeviceOpsServ.RunTroubleshootScriptRequest.TroubleshootScript labScript =
                  switch (request.getScript()) {
                    case RESET_USB_HUB ->
                        DeviceOpsServ.RunTroubleshootScriptRequest.TroubleshootScript.RESET_USB_HUB;
                    default ->
                        throw FeServiceException.invalidArgument(
                            "Unsupported troubleshoot script: " + request.getScript());
                  };

              DeviceOpsServ.RunTroubleshootScriptRequest labRequest =
                  DeviceOpsServ.RunTroubleshootScriptRequest.newBuilder()
                      .setScript(labScript)
                      .build();

              DeviceOpsStub stub = deviceOpsStubProvider.createStub(hostName, universe);
              return Futures.transform(
                  stub.runTroubleshootScriptAsync(labRequest),
                  labResponse ->
                      RunTroubleshootScriptResponse.newBuilder()
                          .setExitCode(labResponse.getExitCode())
                          .setStdout(labResponse.getStdout())
                          .setStderr(labResponse.getStderr())
                          .build(),
                  directExecutor());
            },
            directExecutor())
        .catching(
            Exception.class,
            e -> {
              throw toFeServiceException(hostName, e);
            },
            directExecutor());
  }

  /** Lists available troubleshoot scripts, disabling them if any device is BUSY. */
  public ListenableFuture<ListTroubleshootScriptsResponse> listTroubleshootScripts(
      ListTroubleshootScriptsRequest request, UniverseScope universe) {
    if (!(universe instanceof UniverseScope.SelfUniverse)) {
      return immediateFailedFuture(
          FeServiceException.unimplemented(
              "Troubleshoot scripts are only supported in the self universe."));
    }
    String hostName = request.getHostName();

    return Futures.transform(
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(hostName), universe),
        labInfoResponse -> {
          ListTroubleshootScriptsResponse.Builder response =
              ListTroubleshootScriptsResponse.newBuilder();

          for (TroubleshootScript script : TroubleshootScriptRegistry.getRegisteredScripts()) {
            ScriptMetadata metadata = TroubleshootScriptRegistry.getMetadata(script);

            // Each script has its own precondition check and disabled reason.
            record Precondition(boolean canRun, String disabledReason) {}
            Precondition precondition =
                switch (script) {
                  case RESET_USB_HUB ->
                      new Precondition(
                          canRunResetUsbHub(labInfoResponse),
                          "Disabled: some devices are BUSY (tests in progress).");
                  default -> new Precondition(false, "This script is not available.");
                };

            String constraintTooltip =
                precondition.canRun() ? metadata.description() : precondition.disabledReason();

            com.google.devtools.mobileharness.fe.v6.service.proto.host.RunTroubleshootScriptRequest
                    .TroubleshootScript
                feScript =
                    com.google.devtools.mobileharness.fe.v6.service.proto.host
                        .RunTroubleshootScriptRequest.TroubleshootScript.valueOf(script.name());

            response.addActions(
                TroubleshootScriptAction.newBuilder()
                    .setScript(feScript)
                    .setDisplayName(metadata.displayName())
                    .setDescription(metadata.description())
                    .setEnabled(precondition.canRun())
                    .setConstraintTooltip(constraintTooltip)
                    .build());
          }

          return response.build();
        },
        directExecutor());
  }

  /**
   * Converts downstream exceptions into {@link FeServiceException} so that the gRPC/AF boundary
   * (FeGrpcInvoker / SafeExceptionHandler) maps them to the correct HTTP status code.
   *
   * <p>If the exception is already a {@code FeServiceException} (e.g. from validation above), it is
   * re-thrown as-is. gRPC {@link StatusRuntimeException}s from the lab server RPC preserve their
   * original code. All other exceptions become {@code INTERNAL}.
   *
   * <p>The error message includes the full cause chain so the client can display detailed command
   * logs (exit code, stdout, stderr) for debugging troubleshoot script failures.
   */
  private static FeServiceException toFeServiceException(String hostName, Exception e) {
    if (e instanceof FeServiceException feEx) {
      return feEx;
    }
    String detail = collectCauseChainMessages(e);
    if (e instanceof StatusRuntimeException sre) {
      Status.Code code = sre.getStatus().getCode();
      String message =
          String.format("Troubleshoot script failed on host %s: %s — %s", hostName, code, detail);
      return new FeServiceException(code, message, sre);
    }
    logger.atWarning().withCause(e).log(
        "Unexpected error running troubleshoot script on host %s", hostName);
    return new FeServiceException(
        Status.Code.INTERNAL,
        String.format("Troubleshoot script failed on host %s: %s", hostName, detail),
        e);
  }

  /**
   * Walks the exception cause chain and collects all distinct messages, joined by {@code "; Caused
   * by: "}. This surfaces root-cause details (e.g. command exit codes, stderr output) that would
   * otherwise be hidden in nested exceptions.
   */
  private static String collectCauseChainMessages(Throwable t) {
    StringBuilder sb = new StringBuilder();
    String lastMessage = null;
    for (Throwable current = t; current != null; current = current.getCause()) {
      String msg = current.getMessage();
      if (isNullOrEmpty(msg)) {
        msg = current.getClass().getSimpleName();
      }
      // Skip duplicate messages (common when wrapping exceptions re-use the same message).
      if (!msg.equals(lastMessage)) {
        if (sb.length() > 0) {
          sb.append("; Caused by: ");
        }
        sb.append(msg);
        lastMessage = msg;
      }
    }
    return sb.length() > 0 ? sb.toString() : t.getClass().getSimpleName();
  }

  /** Returns true if no devices are BUSY on the host, meaning it is safe to run RESET_USB_HUB. */
  private static boolean canRunResetUsbHub(GetLabInfoResponse labInfoResponse) {
    return labInfoResponse.getLabQueryResult().getLabView().getLabDataList().stream()
        .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
        .noneMatch(d -> d.getDeviceStatus() == DeviceStatus.BUSY);
  }

  private static GetLabInfoRequest createGetLabInfoRequest(String hostName) {
    return GetLabInfoRequest.newBuilder()
        .setLabQuery(
            LabQuery.newBuilder()
                .setFilter(
                    LabQuery.Filter.newBuilder()
                        .setLabFilter(
                            FilterProto.LabFilter.newBuilder()
                                .addLabMatchCondition(
                                    FilterProto.LabFilter.LabMatchCondition.newBuilder()
                                        .setLabHostNameMatchCondition(
                                            FilterProto.LabFilter.LabMatchCondition
                                                .LabHostNameMatchCondition.newBuilder()
                                                .setCondition(
                                                    FilterProto.StringMatchCondition.newBuilder()
                                                        .setInclude(
                                                            FilterProto.StringMatchCondition.Include
                                                                .newBuilder()
                                                                .addExpected(hostName))))))))
        .build();
  }
}
