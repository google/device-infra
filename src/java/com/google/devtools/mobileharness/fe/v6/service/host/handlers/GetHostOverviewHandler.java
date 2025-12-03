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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
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
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostTypes;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DaemonServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetHostOverview RPC. */
@Singleton
public final class GetHostOverviewHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabInfoProvider labInfoProvider;
  private final HostAuxiliaryInfoProvider hostAuxiliaryInfoProvider;
  private final ListeningExecutorService executor;

  @Inject
  GetHostOverviewHandler(
      LabInfoProvider labInfoProvider,
      HostAuxiliaryInfoProvider hostAuxiliaryInfoProvider,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.hostAuxiliaryInfoProvider = hostAuxiliaryInfoProvider;
    this.executor = executor;
  }

  public ListenableFuture<HostOverview> getHostOverview(GetHostOverviewRequest request) {
    logger.atInfo().log("Getting host overview for %s", request.getHostName());
    String hostName = request.getHostName();

    ListenableFuture<GetLabInfoResponse> labInfoFuture =
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(hostName), "");

    ListenableFuture<Optional<HostReleaseInfo>> hostReleaseInfoFuture =
        hostAuxiliaryInfoProvider.getHostReleaseInfo(hostName);

    ListenableFuture<Optional<String>> passThroughFlagsFuture =
        hostAuxiliaryInfoProvider.getPassThroughFlags(hostName);

    return Futures.whenAllSucceed(labInfoFuture, hostReleaseInfoFuture, passThroughFlagsFuture)
        .call(
            () -> {
              GetLabInfoResponse labInfoResponse = Futures.getDone(labInfoFuture);
              Optional<HostReleaseInfo> hostReleaseInfoOpt = Futures.getDone(hostReleaseInfoFuture);
              Optional<String> passThroughFlagsOpt = Futures.getDone(passThroughFlagsFuture);

              Optional<LabInfo> labInfoOpt =
                  labInfoResponse.getLabQueryResult().getLabView().getLabDataList().stream()
                      .map(LabData::getLabInfo)
                      .findFirst();

              return buildHostOverview(
                  hostName, labInfoOpt, hostReleaseInfoOpt, passThroughFlagsOpt);
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

  private HostOverview buildHostOverview(
      String hostName,
      Optional<LabInfo> labInfoOpt,
      Optional<HostReleaseInfo> hostReleaseInfoOpt,
      Optional<String> passThroughFlagsOpt) {
    HostOverview.Builder builder = HostOverview.newBuilder().setHostName(hostName);

    ImmutableMap<String, String> properties =
        labInfoOpt
            .map(
                labInfo ->
                    labInfo.getLabServerFeature().getHostProperties().getHostPropertyList().stream()
                        .collect(
                            toImmutableMap(
                                HostProperty::getKey, HostProperty::getValue, (v1, v2) -> v1)))
            .orElse(ImmutableMap.of());
    builder.putAllProperties(properties);

    labInfoOpt.ifPresent(labInfo -> builder.setIp(labInfo.getLabLocator().getIp()));

    builder.setOs(properties.getOrDefault("host_os", "Unknown"));

    Optional<String> labTypeOpt = hostReleaseInfoOpt.flatMap(HostReleaseInfo::labType);
    return builder
        .addAllLabTypeDisplayNames(HostTypes.determineLabTypeDisplayNames(labInfoOpt, labTypeOpt))
        .setLabServer(buildLabServerInfo(labInfoOpt, hostReleaseInfoOpt, passThroughFlagsOpt))
        .setDaemonServer(buildDaemonServerInfo(hostReleaseInfoOpt))
        .build();
  }

  private LabServerInfo buildLabServerInfo(
      Optional<LabInfo> unusedLabInfoOpt,
      Optional<HostReleaseInfo> hostReleaseInfoOpt,
      Optional<String> passThroughFlagsOpt) {
    LabServerInfo.Builder builder = LabServerInfo.newBuilder();

    // TODO: Implement HostConnectivityStatus logic
    builder.setConnectivity(HostConnectivityStatus.getDefaultInstance());

    // TODO: Implement LabServerInfo.Activity logic
    builder.setActivity(LabServerInfo.Activity.getDefaultInstance());

    hostReleaseInfoOpt
        .flatMap(HostReleaseInfo::labServerReleaseInfo)
        .ifPresent(
            info -> {
              info.version().ifPresent(builder::setVersion);
              // TODO: Use info.status() to determine ActivityState
            });

    passThroughFlagsOpt.ifPresent(builder::setPassThroughFlags);

    return builder.build();
  }

  private DaemonServerInfo buildDaemonServerInfo(Optional<HostReleaseInfo> hostReleaseInfoOpt) {
    DaemonServerInfo.Builder builder = DaemonServerInfo.newBuilder();

    // TODO: Implement DaemonServerInfo.Status logic
    builder.setStatus(DaemonServerInfo.Status.getDefaultInstance());

    hostReleaseInfoOpt
        .flatMap(HostReleaseInfo::daemonServerReleaseInfo)
        .ifPresent(
            info -> {
              info.version().ifPresent(builder::setVersion);
              // TODO: Use info.status() to determine DaemonServerInfo.State
            });
    return builder.build();
  }
}
