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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.LabFilter;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition.Include;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.LabViewRequest;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostAuxiliaryInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostReleaseInfo;
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostVersionUtil;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Resolves the current lab server version for a host. */
@Singleton
public class LabServerVersionResolver {

  private final LabInfoProvider labInfoProvider;
  private final HostAuxiliaryInfoProvider hostAuxiliaryInfoProvider;
  private final ListeningExecutorService executor;

  @Inject
  LabServerVersionResolver(
      LabInfoProvider labInfoProvider,
      HostAuxiliaryInfoProvider hostAuxiliaryInfoProvider,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.hostAuxiliaryInfoProvider = hostAuxiliaryInfoProvider;
    this.executor = executor;
  }

  /**
   * Resolves the current version of the lab server running on the specified host.
   *
   * <p>TODO: Consider refactoring other version resolution places (e.g., host overview, canUpgrade,
   * release preflight) to share this resolver.
   */
  public ListenableFuture<String> resolveCurrentVersion(String hostName, UniverseScope universe) {
    ListenableFuture<GetLabInfoResponse> labInfoFuture =
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(hostName), universe);

    ListenableFuture<Optional<HostReleaseInfo>> hostReleaseInfoFuture =
        hostAuxiliaryInfoProvider.getHostReleaseInfo(hostName, universe);

    return Futures.whenAllSucceed(labInfoFuture, hostReleaseInfoFuture)
        .call(
            () -> {
              GetLabInfoResponse labInfoResponse = Futures.getDone(labInfoFuture);
              Optional<HostReleaseInfo> hostReleaseInfoOpt = Futures.getDone(hostReleaseInfoFuture);

              Optional<LabInfo> labInfoOpt =
                  labInfoResponse.getLabQueryResult().getLabView().getLabDataList().stream()
                      .map(LabData::getLabInfo)
                      .findFirst();

              Optional<String> currentVersionOpt =
                  HostVersionUtil.resolveCurrentVersion(labInfoOpt, hostReleaseInfoOpt);

              return currentVersionOpt.orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Failed to resolve current lab server version for host: " + hostName));
            },
            executor);
  }

  private GetLabInfoRequest createGetLabInfoRequest(String hostName) {
    return GetLabInfoRequest.newBuilder()
        .setLabQuery(
            LabQuery.newBuilder()
                .setFilter(
                    Filter.newBuilder()
                        .setLabFilter(
                            LabFilter.newBuilder()
                                .addLabMatchCondition(
                                    LabFilter.LabMatchCondition.newBuilder()
                                        .setLabHostNameMatchCondition(
                                            LabFilter.LabMatchCondition.LabHostNameMatchCondition
                                                .newBuilder()
                                                .setCondition(
                                                    StringMatchCondition.newBuilder()
                                                        .setInclude(
                                                            Include.newBuilder()
                                                                .addExpected(hostName)))))))
                .setLabViewRequest(LabViewRequest.getDefaultInstance()))
        .build();
  }
}
