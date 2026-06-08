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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
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
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostLatestVersionProvider;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostReleaseInfo;
import com.google.devtools.mobileharness.fe.v6.service.host.util.DaemonStatuses;
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostConnectivityStatuses;
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostTypes;
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostVersionUtil;
import com.google.devtools.mobileharness.fe.v6.service.host.util.LabActivities;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DaemonServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DiagnosticLink;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverviewPageData;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UiLabType;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.devtools.mobileharness.shared.version.Version;
import java.util.List;
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
  private final HostHeaderInfoBuilder hostHeaderInfoBuilder;
  private final LabServerActionsBuilder labServerActionsBuilder;
  private final HostLatestVersionProvider hostLatestVersionProvider;

  @Inject
  GetHostOverviewHandler(
      LabInfoProvider labInfoProvider,
      HostAuxiliaryInfoProvider hostAuxiliaryInfoProvider,
      ListeningExecutorService executor,
      HostHeaderInfoBuilder hostHeaderInfoBuilder,
      LabServerActionsBuilder labServerActionsBuilder,
      HostLatestVersionProvider hostLatestVersionProvider) {
    this.labInfoProvider = labInfoProvider;
    this.hostAuxiliaryInfoProvider = hostAuxiliaryInfoProvider;
    this.executor = executor;
    this.hostHeaderInfoBuilder = hostHeaderInfoBuilder;
    this.labServerActionsBuilder = labServerActionsBuilder;
    this.hostLatestVersionProvider = hostLatestVersionProvider;
  }

  public ListenableFuture<HostOverviewPageData> getHostOverview(
      GetHostOverviewRequest request, UniverseScope universe) {
    logger.atInfo().log("Getting host overview for %s", request.getHostName());
    String hostName = request.getHostName();

    ListenableFuture<GetLabInfoResponse> labInfoFuture =
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(hostName), universe);

    ListenableFuture<Optional<HostReleaseInfo>> hostReleaseInfoFuture =
        hostAuxiliaryInfoProvider.getHostReleaseInfo(hostName, universe);

    ListenableFuture<Optional<String>> passThroughFlagsFuture =
        hostAuxiliaryInfoProvider.getPassThroughFlags(hostName, universe);

    ListenableFuture<List<DiagnosticLink>> logLinksFuture =
        Futures.transformAsync(
            hostReleaseInfoFuture,
            hostReleaseInfoOpt ->
                hostAuxiliaryInfoProvider.getDiagnosticLinks(
                    hostName, hostReleaseInfoOpt.flatMap(HostReleaseInfo::labType), universe),
            executor);

    ListenableFuture<Optional<String>> latestVersionFuture =
        hostLatestVersionProvider.getLatestVersion(hostName, universe);

    return Futures.whenAllSucceed(
            labInfoFuture,
            hostReleaseInfoFuture,
            passThroughFlagsFuture,
            logLinksFuture,
            latestVersionFuture)
        .call(
            () -> {
              GetLabInfoResponse labInfoResponse = Futures.getDone(labInfoFuture);
              Optional<HostReleaseInfo> hostReleaseInfoOpt = Futures.getDone(hostReleaseInfoFuture);
              Optional<String> passThroughFlagsOpt = Futures.getDone(passThroughFlagsFuture);
              List<DiagnosticLink> diagnosticLinks = Futures.getDone(logLinksFuture);
              Optional<String> latestVersionOpt = Futures.getDone(latestVersionFuture);

              Optional<LabInfo> labInfoOpt =
                  labInfoResponse.getLabQueryResult().getLabView().getLabDataList().stream()
                      .map(LabData::getLabInfo)
                      .findFirst();

              Optional<String> currentVersionOpt =
                  HostVersionUtil.resolveCurrentVersion(labInfoOpt, hostReleaseInfoOpt);

              LabServerInfo labServerInfo =
                  buildLabServerInfo(
                      labInfoOpt,
                      hostReleaseInfoOpt,
                      passThroughFlagsOpt,
                      currentVersionOpt,
                      universe);

              boolean canUpgrade =
                  calculateCanUpgrade(currentVersionOpt, latestVersionOpt, labServerInfo);

              HostOverview overview =
                  buildHostOverview(
                      hostName,
                      labInfoOpt,
                      hostReleaseInfoOpt,
                      diagnosticLinks,
                      labServerInfo,
                      canUpgrade);

              Optional<String> labTypeOpt = hostReleaseInfoOpt.flatMap(HostReleaseInfo::labType);

              Optional<HostReleaseInfo.ComponentInfo> daemonReleaseOpt =
                  hostReleaseInfoOpt.flatMap(HostReleaseInfo::daemonServerReleaseInfo);
              DaemonServerInfo.Status daemonStatus = DaemonStatuses.create(daemonReleaseOpt);

              HostHeaderInfo headerInfo =
                  hostHeaderInfoBuilder.build(
                      hostName, universe, labInfoOpt, labTypeOpt, daemonStatus);

              return HostOverviewPageData.newBuilder()
                  .setHeaderInfo(headerInfo)
                  .setOverviewContent(overview)
                  .build();
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
      List<DiagnosticLink> diagnosticLinks,
      LabServerInfo labServerInfo,
      boolean canUpgrade) {
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

    labInfoOpt.ifPresent(
        labInfo -> {
          String ip = labInfo.getLabLocator().getIp();
          // If the IP is not set, fallback to use the master detected IP.
          if (ip.isEmpty() && labInfo.getLabLocator().hasMasterDetectedIp()) {
            ip = labInfo.getLabLocator().getMasterDetectedIp();
          }
          builder.setIp(ip);
        });

    builder.setOs(properties.getOrDefault("host_os", "Unknown"));

    builder.setCanUpgrade(canUpgrade);

    Optional<String> labTypeOpt = hostReleaseInfoOpt.flatMap(HostReleaseInfo::labType);
    ImmutableList<UiLabType> uiLabTypes = HostTypes.determineUiLabTypes(labInfoOpt, labTypeOpt);
    ImmutableList<String> labTypes = HostTypes.determineLabTypeDisplayNames(labInfoOpt, labTypeOpt);
    boolean isCoreOrFusion = HostTypes.isCoreOrFusionUiLabTypes(uiLabTypes);

    return builder
        .addAllLabTypeDisplayNames(labTypes) // Legacy field for backward compatibility
        .addAllUiLabTypes(uiLabTypes)
        .setShowPassThroughFlags(!isCoreOrFusion)
        .setLabServer(labServerInfo)
        .setDaemonServer(buildDaemonServerInfo(hostReleaseInfoOpt))
        .addAllDiagnosticLinks(diagnosticLinks)
        .build();
  }

  private boolean calculateCanUpgrade(
      Optional<String> currentVersionOpt,
      Optional<String> latestVersionOpt,
      LabServerInfo labServerInfo) {
    if (currentVersionOpt.isEmpty() || latestVersionOpt.isEmpty()) {
      return false;
    }
    try {
      Version currentVersion =
          new Version(HostVersionUtil.normalizeVersion(currentVersionOpt.get()));
      Version latestVersion = new Version(HostVersionUtil.normalizeVersion(latestVersionOpt.get()));
      return latestVersion.compareTo(currentVersion) > 0
          && labServerInfo.getActions().getRelease().getVisible();
    } catch (IllegalArgumentException | MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse versions for comparison: current=%s, latest=%s",
          currentVersionOpt.get(), latestVersionOpt.get());
      return false;
    }
  }

  private LabServerInfo buildLabServerInfo(
      Optional<LabInfo> labInfoOpt,
      Optional<HostReleaseInfo> hostReleaseInfoOpt,
      Optional<String> passThroughFlagsOpt,
      Optional<String> currentVersionOpt,
      UniverseScope universe) {
    LabServerInfo.Builder builder = LabServerInfo.newBuilder();

    HostConnectivityStatus connectivityStatus = HostConnectivityStatuses.create(labInfoOpt);
    builder.setConnectivity(connectivityStatus);

    currentVersionOpt.ifPresent(builder::setVersion);

    Optional<HostReleaseInfo.ComponentInfo> labReleaseOpt =
        hostReleaseInfoOpt.flatMap(HostReleaseInfo::labServerReleaseInfo);

    boolean isCoreLab =
        HostTypes.determineLabTypeDisplayNames(
                labInfoOpt, hostReleaseInfoOpt.flatMap(HostReleaseInfo::labType))
            .contains(HostTypes.LAB_TYPE_CORE);

    LabServerInfo.Activity activity =
        LabActivities.create(labReleaseOpt, connectivityStatus, isCoreLab);
    builder.setActivity(activity);

    passThroughFlagsOpt.ifPresent(builder::setPassThroughFlags);

    Optional<String> labTypeOpt = hostReleaseInfoOpt.flatMap(HostReleaseInfo::labType);

    Optional<HostReleaseInfo.ComponentInfo> daemonReleaseOpt =
        hostReleaseInfoOpt.flatMap(HostReleaseInfo::daemonServerReleaseInfo);
    DaemonServerInfo.Status daemonStatus = DaemonStatuses.create(daemonReleaseOpt);

    builder.setActions(
        labServerActionsBuilder.build(
            universe, labInfoOpt, labTypeOpt, activity, connectivityStatus, daemonStatus));

    return builder.build();
  }

  private DaemonServerInfo buildDaemonServerInfo(Optional<HostReleaseInfo> hostReleaseInfoOpt) {
    DaemonServerInfo.Builder builder = DaemonServerInfo.newBuilder();

    Optional<HostReleaseInfo.ComponentInfo> daemonReleaseOpt =
        hostReleaseInfoOpt.flatMap(HostReleaseInfo::daemonServerReleaseInfo);

    if (daemonReleaseOpt.isPresent()) {
      daemonReleaseOpt.get().version().ifPresent(builder::setVersion);
    }

    return builder.setStatus(DaemonStatuses.create(daemonReleaseOpt)).build();
  }
}
