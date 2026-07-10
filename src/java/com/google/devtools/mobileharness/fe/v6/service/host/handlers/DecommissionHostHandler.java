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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionHostRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionHostResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingHostRequest;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabSyncStub;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the DecommissionHost RPC. */
@Singleton
public final class DecommissionHostHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabSyncStub labSyncStub;
  private final Environment environment;

  @Inject
  DecommissionHostHandler(LabSyncStub labSyncStub, Environment environment) {
    this.labSyncStub = labSyncStub;
    this.environment = environment;
  }

  public ListenableFuture<DecommissionHostResponse> decommissionHost(
      DecommissionHostRequest request, UniverseScope universe) {
    if (environment.isAts()) {
      return immediateFailedFuture(
          new UnsupportedOperationException(
              "Decommissioning host is not supported in the ATS environment."));
    }

    logger.atInfo().log("Decommissioning host %s in universe %s", request.getHostName(), universe);

    if (!(universe instanceof UniverseScope.SelfUniverse)) {
      return immediateFailedFuture(
          new IllegalArgumentException(
              "Decommissioning host is only supported for Google Internal Labs. Current"
                  + " universe: "
                  + universe));
    }

    RemoveMissingHostRequest masterRequest =
        RemoveMissingHostRequest.newBuilder().setLabHostName(request.getHostName()).build();

    return Futures.transform(
        labSyncStub.removeMissingHost(masterRequest, /* useClientRpcAuthority= */ true),
        unused -> DecommissionHostResponse.getDefaultInstance(),
        directExecutor());
  }
}
