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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.device.provider.DeviceOpsStubProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CommandResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDebugInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDebugInfoResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.DeviceOpsStub;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ;
import java.time.InstantSource;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetHostDebugInfo RPC. */
@Singleton
public final class GetHostDebugInfoHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceOpsStubProvider deviceOpsStubProvider;
  private final InstantSource instantSource;

  @Inject
  GetHostDebugInfoHandler(
      DeviceOpsStubProvider deviceOpsStubProvider, InstantSource instantSource) {
    this.deviceOpsStubProvider = deviceOpsStubProvider;
    this.instantSource = instantSource;
  }

  public ListenableFuture<GetHostDebugInfoResponse> getHostDebugInfo(
      GetHostDebugInfoRequest request, UniverseScope universe) {
    String hostName = request.getHostName();

    // 1. Validate universe
    if (!(universe instanceof UniverseScope.SelfUniverse)) {
      return immediateFailedFuture(
          new IllegalArgumentException(
              "GetHostDebugInfo is only supported for Scenario 1 (google_1p). Current universe: "
                  + universe));
    }

    logger.atInfo().log("Getting host debug info for host %s in universe %s", hostName, universe);

    // 2. Create GetDeviceDebugInfoRequest for Lab Server
    DeviceOpsServ.GetDeviceDebugInfoRequest labRequest =
        DeviceOpsServ.GetDeviceDebugInfoRequest.newBuilder()
            .addCommand(DeviceOpsServ.GetDeviceDebugInfoRequest.GetDeviceDebugInfoCommand.ALL)
            .build();

    // 3. Call Lab Server
    DeviceOpsStub deviceOpsStub = deviceOpsStubProvider.createStub(hostName, universe);
    ListenableFuture<DeviceOpsServ.GetDeviceDebugInfoResponse> labResponseFuture =
        deviceOpsStub.getDeviceDebugInfoAsync(labRequest, /* useClientRpcAuthority= */ true);

    // 4. Map response
    return Futures.transform(
        labResponseFuture,
        labResponse ->
            GetHostDebugInfoResponse.newBuilder()
                .addAllResults(
                    labResponse.getDeviceDebugInfoList().stream()
                        .map(
                            result ->
                                CommandResult.newBuilder()
                                    .setCommand(result.getCommand())
                                    .setStdout(result.getStdout())
                                    .setStderr(result.getStderr())
                                    .build())
                        .collect(toImmutableList()))
                .setTimestamp(Timestamps.fromMillis(instantSource.millis()))
                .build(),
        directExecutor());
  }
}
