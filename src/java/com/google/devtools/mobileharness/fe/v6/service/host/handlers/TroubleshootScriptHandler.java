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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.FilterProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.fe.v6.service.device.provider.DeviceOpsStubProvider;
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
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for troubleshoot script operations (run and list). */
@Singleton
public class TroubleshootScriptHandler {

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
    String hostName = request.getHostName();

    return Futures.transformAsync(
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(hostName), universe),
        labInfoResponse -> {
          if (hasAnyDeviceBusy(labInfoResponse)) {
            return immediateFailedFuture(
                new MobileHarnessException(
                    BasicErrorId.COMMAND_EXEC_FAIL,
                    "Cannot run troubleshoot script while devices are BUSY on host "
                        + hostName
                        + ". Please wait for running tests to complete."));
          }

          DeviceOpsServ.RunTroubleshootScriptRequest.TroubleshootScript labScript =
              switch (request.getScript()) {
                case RESET_USB_HUB ->
                    DeviceOpsServ.RunTroubleshootScriptRequest.TroubleshootScript.RESET_USB_HUB;
                default ->
                    throw new MobileHarnessException(
                        BasicErrorId.COMMAND_EXEC_FAIL,
                        "Unsupported troubleshoot script: " + request.getScript());
              };

          DeviceOpsServ.RunTroubleshootScriptRequest labRequest =
              DeviceOpsServ.RunTroubleshootScriptRequest.newBuilder()
                  .setScript(labScript)
                  .putAllArguments(request.getArgumentsMap())
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
        directExecutor());
  }

  /** Lists available troubleshoot scripts, disabling them if any device is BUSY. */
  public ListenableFuture<ListTroubleshootScriptsResponse> listTroubleshootScripts(
      ListTroubleshootScriptsRequest request, UniverseScope universe) {
    String hostName = request.getHostName();

    return Futures.transform(
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(hostName), universe),
        labInfoResponse -> {
          boolean hasDeviceBusy = hasAnyDeviceBusy(labInfoResponse);

          ListTroubleshootScriptsResponse.Builder response =
              ListTroubleshootScriptsResponse.newBuilder();

          for (TroubleshootScript script : TroubleshootScriptRegistry.getRegisteredScripts()) {
            ScriptMetadata metadata = TroubleshootScriptRegistry.getMetadata(script);

            String constraintTooltip =
                hasDeviceBusy
                    ? "Disabled: some devices are BUSY (tests in progress)."
                    : metadata.description();

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
                    .setEnabled(!hasDeviceBusy)
                    .setConstraintTooltip(constraintTooltip)
                    .build());
          }

          return response.build();
        },
        directExecutor());
  }

  private static boolean hasAnyDeviceBusy(GetLabInfoResponse labInfoResponse) {
    return labInfoResponse.getLabQueryResult().getLabView().getLabDataList().stream()
        .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
        .anyMatch(d -> d.getDeviceStatus() == DeviceStatus.BUSY);
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
