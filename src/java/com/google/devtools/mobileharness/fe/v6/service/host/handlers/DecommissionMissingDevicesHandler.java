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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionMissingDevicesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionMissingDevicesResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDeviceRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDevicesRequest;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabSyncStub;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the DecommissionMissingDevices RPC. */
@Singleton
public final class DecommissionMissingDevicesHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabSyncStub labSyncStub;
  private final Environment environment;

  @Inject
  DecommissionMissingDevicesHandler(LabSyncStub labSyncStub, Environment environment) {
    this.labSyncStub = labSyncStub;
    this.environment = environment;
  }

  public ListenableFuture<DecommissionMissingDevicesResponse> decommissionMissingDevices(
      DecommissionMissingDevicesRequest request, UniverseScope universe) {
    if (environment.isAts()) {
      throw new UnsupportedOperationException(
          "Decommissioning missing devices is not supported in the ATS environment.");
    }

    logger.atInfo().log(
        "Decommissioning missing devices for host %s in universe %s: %s",
        request.getHostName(), universe, request.getDeviceIdsList());

    if (!(universe instanceof UniverseScope.SelfUniverse)) {
      logger.atWarning().log(
          "Decommissioning missing devices is only supported for Scenario 1 (google_1p). "
              + "Current universe: %s",
          universe);
      return immediateFuture(DecommissionMissingDevicesResponse.getDefaultInstance());
    }

    RemoveMissingDevicesRequest masterRequest =
        RemoveMissingDevicesRequest.newBuilder()
            .addAllRemoveMissingDeviceRequest(
                request.getDeviceIdsList().stream()
                    .map(
                        deviceId ->
                            RemoveMissingDeviceRequest.newBuilder()
                                .setDeviceUuid(deviceId)
                                .setLabHostName(request.getHostName())
                                .build())
                    .collect(toImmutableList()))
            .build();

    return Futures.transform(
        labSyncStub.removeMissingDevices(masterRequest, /* useClientRpcAuthority= */ true),
        unused -> DecommissionMissingDevicesResponse.getDefaultInstance(),
        directExecutor());
  }
}
