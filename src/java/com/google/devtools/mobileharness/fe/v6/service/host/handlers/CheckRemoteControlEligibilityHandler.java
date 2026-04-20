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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.query.proto.FilterProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.DeviceDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceType;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.SubDeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CheckRemoteControlEligibilityRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CheckRemoteControlEligibilityRequest.DeviceTarget;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CheckRemoteControlEligibilityResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceEligibilityResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceProxyType;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.EligibilityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.SessionOptions;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceInfoUtil;
import com.google.devtools.mobileharness.fe.v6.service.shared.SubDeviceInfoListFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityContext;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityResult;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Handler for the CheckRemoteControlEligibility RPC.
 *
 * <p>Retrieves lab info and checks remote control eligibility using {@link
 * RemoteControlEligibilityChecker}.
 */
@Singleton
public final class CheckRemoteControlEligibilityHandler {

  private final LabInfoProvider labInfoProvider;
  private final RemoteControlEligibilityChecker remoteControlEligibilityChecker;
  private final SubDeviceInfoListFactory subDeviceInfoListFactory;
  private final ListeningExecutorService executor;

  @Inject
  CheckRemoteControlEligibilityHandler(
      LabInfoProvider labInfoProvider,
      RemoteControlEligibilityChecker remoteControlEligibilityChecker,
      SubDeviceInfoListFactory subDeviceInfoListFactory,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.remoteControlEligibilityChecker = remoteControlEligibilityChecker;
    this.subDeviceInfoListFactory = subDeviceInfoListFactory;
    this.executor = executor;
  }

  public ListenableFuture<CheckRemoteControlEligibilityResponse> checkRemoteControlEligibility(
      CheckRemoteControlEligibilityRequest request,
      Optional<String> username,
      UniverseScope universe) {
    List<DeviceTarget> targets = request.getTargetsList();

    // If there are no targets, return an empty response.
    if (targets.isEmpty()) {
      return immediateFuture(CheckRemoteControlEligibilityResponse.getDefaultInstance());
    }

    // If the username is empty, return a permission denied response.
    if (username.isEmpty()) {
      return immediateFuture(
          CheckRemoteControlEligibilityResponse.newBuilder()
              .setStatus(EligibilityStatus.BLOCK_ALL_PERMISSION_DENIED)
              .build());
    }

    return Futures.transformAsync(
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(request.getHostName()), universe),
        response -> processLabInfoResponse(response, targets, username.get()),
        executor);
  }

  private ListenableFuture<CheckRemoteControlEligibilityResponse> processLabInfoResponse(
      GetLabInfoResponse response, List<DeviceTarget> targets, String username) {
    boolean isMultiple = targets.size() > 1;

    // Get all devices from the lab info response.
    ImmutableMap<String, DeviceInfo> devices =
        response.getLabQueryResult().getLabView().getLabDataList().stream()
            .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
            .collect(toImmutableMap(d -> d.getDeviceLocator().getId(), d -> d, (v1, v2) -> v1));

    // If any device is in a shared pool, set the max duration to 3 hours.
    // This matches the standard definition of shared/MnM devices where the "pool" dimension
    // contains "shared".
    int maxDurationHours =
        targets.stream()
                .map(target -> devices.get(target.getDeviceId()))
                .filter(Objects::nonNull)
                .map(DeviceInfoUtil::getDimensions)
                .anyMatch(dims -> dims.getOrDefault("pool", "").contains("shared"))
            ? 3
            : 12;

    // Resolve targets to device eligibility results.
    ImmutableList<ListenableFuture<DeviceEligibilityResult>> resultFutures =
        targets.stream()
            .map(target -> resolveTarget(target, devices, isMultiple, username))
            .collect(toImmutableList());

    return Futures.transform(
        Futures.allAsList(resultFutures),
        results -> aggregateResults(results, maxDurationHours),
        executor);
  }

  private ListenableFuture<DeviceEligibilityResult> resolveTarget(
      DeviceTarget target, Map<String, DeviceInfo> devices, boolean isMultiple, String username) {
    DeviceInfo device = devices.get(target.getDeviceId());
    if (device == null) {
      return immediateFuture(
          DeviceEligibilityResult.newBuilder()
              .setDeviceId(target.getDeviceId())
              .setIsEligible(false)
              .setIneligibilityReason(
                  DeviceEligibilityResult.IneligibilityReason.newBuilder()
                      .setCode(IneligibilityReasonCode.DEVICE_NOT_FOUND)
                      .setMessage(
                          String.format("Device %s not found in Lab Info.", target.getDeviceId())))
              .build());
    }

    ImmutableMap<String, String> dimensions = DeviceInfoUtil.getDimensions(device);

    if (!target.getSubDeviceId().isEmpty()) {
      return resolveSubDeviceTarget(
          target.getDeviceId(), target.getSubDeviceId(), device, dimensions, isMultiple, username);
    }

    return resolveDeviceTarget(device, dimensions, isMultiple, username);
  }

  private ListenableFuture<DeviceEligibilityResult> resolveSubDeviceTarget(
      String deviceId,
      String subDeviceId,
      DeviceInfo device,
      ImmutableMap<String, String> dimensions,
      boolean isMultiple,
      String username) {
    Optional<SubDeviceInfo> sub =
        subDeviceInfoListFactory.create(dimensions).stream()
            .filter(s -> s.getId().equals(subDeviceId))
            .findFirst();

    if (sub.isEmpty()) {
      return immediateFuture(
          DeviceEligibilityResult.newBuilder()
              .setDeviceId(deviceId)
              .setIsEligible(false)
              .setIneligibilityReason(
                  DeviceEligibilityResult.IneligibilityReason.newBuilder()
                      .setCode(IneligibilityReasonCode.DEVICE_NOT_FOUND)
                      .setMessage(
                          String.format(
                              "SubDevice %s of device %s not found.", subDeviceId, deviceId)))
              .build());
    }

    return Futures.transform(
        remoteControlEligibilityChecker.checkEligibility(
            buildSubDeviceContext(device, sub.get(), isMultiple, username)),
        result -> mapToDeviceProto(subDeviceId, result),
        executor);
  }

  private ListenableFuture<DeviceEligibilityResult> resolveDeviceTarget(
      DeviceInfo device,
      ImmutableMap<String, String> dimensions,
      boolean isMultiple,
      String username) {

    // Check if the testbed device has communication_type sub-device.
    boolean hasCommSub = false;
    if (device.getDeviceFeature().getTypeList().contains("TestbedDevice")) {
      hasCommSub =
          subDeviceInfoListFactory.create(dimensions).stream()
              .anyMatch(
                  sub ->
                      sub.getDimensionsList().stream()
                          .anyMatch(d -> d.getName().equals("communication_type")));
    }

    ImmutableList<String> ownersAndExecutors =
        ImmutableList.<String>builder()
            .addAll(device.getDeviceFeature().getOwnerList())
            .addAll(device.getDeviceFeature().getExecutorList())
            .build();

    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(isMultiple)
            .setIsSubDevice(false)
            .setHasCommSubDevice(hasCommSub)
            .setDeviceStatus(device.getDeviceStatus())
            .setDrivers(ImmutableSet.copyOf(device.getDeviceFeature().getDriverList()))
            .setTypes(ImmutableSet.copyOf(device.getDeviceFeature().getTypeList()))
            .setDimensions(dimensions)
            .setUsername(username)
            .setOwnersAndExecutors(ownersAndExecutors)
            .build();

    return Futures.transformAsync(
        remoteControlEligibilityChecker.checkEligibility(context),
        checkerResult -> {
          DeviceEligibilityResult.Builder resultBuilder =
              mapToDeviceProto(device.getDeviceLocator().getId(), checkerResult).toBuilder();

          // If it's a testbed device and eligible, enrich with sub-device results summary.
          if (checkerResult.isEligible()
              && device.getDeviceFeature().getTypeList().contains("TestbedDevice")) {
            ImmutableList<ListenableFuture<DeviceEligibilityResult.SubDeviceEligibilityResult>>
                subFutures =
                    subDeviceInfoListFactory.create(dimensions).stream()
                        .map(
                            sub ->
                                Futures.transform(
                                    remoteControlEligibilityChecker.checkEligibility(
                                        buildSubDeviceContext(device, sub, false, username)),
                                    res -> mapToSubDeviceProto(sub.getId(), res),
                                    executor))
                        .collect(toImmutableList());
            return Futures.transform(
                Futures.allAsList(subFutures),
                subResults -> resultBuilder.addAllSubDeviceResults(subResults).build(),
                executor);
          }
          return immediateFuture(resultBuilder.build());
        },
        executor);
  }

  private static DeviceEligibilityResult mapToDeviceProto(
      String deviceId, RemoteControlEligibilityResult result) {
    DeviceEligibilityResult.Builder builder =
        DeviceEligibilityResult.newBuilder()
            .setDeviceId(deviceId)
            .setIsEligible(result.isEligible())
            .addAllSupportedProxyTypes(result.supportedProxyTypes())
            .addAllRunAsCandidates(result.runAsCandidates());

    createIneligibilityReason(result).ifPresent(builder::setIneligibilityReason);

    return builder.build();
  }

  private static DeviceEligibilityResult.SubDeviceEligibilityResult mapToSubDeviceProto(
      String id, RemoteControlEligibilityResult result) {
    DeviceEligibilityResult.SubDeviceEligibilityResult.Builder builder =
        DeviceEligibilityResult.SubDeviceEligibilityResult.newBuilder()
            .setDeviceId(id)
            .setIsEligible(result.isEligible());

    createIneligibilityReason(result).ifPresent(builder::setIneligibilityReason);

    return builder.build();
  }

  private static Optional<DeviceEligibilityResult.IneligibilityReason> createIneligibilityReason(
      RemoteControlEligibilityResult result) {
    if (result.isEligible()) {
      return Optional.empty();
    }

    return Optional.of(
        DeviceEligibilityResult.IneligibilityReason.newBuilder()
            .setCode(
                result
                    .reasonCode()
                    .orElse(IneligibilityReasonCode.INELIGIBILITY_REASON_CODE_UNSPECIFIED))
            .setMessage(result.reasonMessage().orElse(""))
            .build());
  }

  private static RemoteControlEligibilityContext buildSubDeviceContext(
      DeviceInfo device, SubDeviceInfo sub, boolean isMultiple, String username) {
    ImmutableList<String> ownersAndExecutors =
        ImmutableList.<String>builder()
            .addAll(device.getDeviceFeature().getOwnerList())
            .addAll(device.getDeviceFeature().getExecutorList())
            .build();

    return RemoteControlEligibilityContext.builder()
        .setIsMultipleSelection(isMultiple)
        .setIsSubDevice(true)
        .setUsername(username)
        .setDeviceStatus(device.getDeviceStatus())
        .setDrivers(ImmutableSet.copyOf(device.getDeviceFeature().getDriverList()))
        .setTypes(sub.getTypesList().stream().map(DeviceType::getType).collect(toImmutableSet()))
        .setDimensions(flattenSubDimensions(sub))
        .setOwnersAndExecutors(ownersAndExecutors)
        .build();
  }

  private static ImmutableMap<String, String> flattenSubDimensions(SubDeviceInfo sub) {
    return sub.getDimensionsList().stream()
        .collect(
            toImmutableMap(DeviceDimension::getName, DeviceDimension::getValue, (v1, v2) -> v1));
  }

  private CheckRemoteControlEligibilityResponse aggregateResults(
      List<DeviceEligibilityResult> results, int maxDurationHours) {
    CheckRemoteControlEligibilityResponse.Builder responseBuilder =
        CheckRemoteControlEligibilityResponse.newBuilder().addAllResults(results);

    if (results.isEmpty()) {
      return responseBuilder.setStatus(EligibilityStatus.ELIGIBILITY_STATUS_UNSPECIFIED).build();
    }

    // If any device is ineligible, return an ineligible response.
    // if any device is ineligible with permission denied, still return a ready response.
    boolean anyIneligible =
        results.stream()
            .anyMatch(
                r ->
                    !r.getIsEligible()
                        && r.getIneligibilityReason().getCode()
                            != IneligibilityReasonCode.PERMISSION_DENIED);
    if (anyIneligible) {
      return responseBuilder.setStatus(EligibilityStatus.BLOCK_DEVICES_INELIGIBLE).build();
    }

    // If all devices are permission denied, return a permission denied response.
    boolean allPermissionDenied =
        results.stream()
            .allMatch(
                r ->
                    r.getIneligibilityReason().getCode()
                        == IneligibilityReasonCode.PERMISSION_DENIED);
    if (allPermissionDenied) {
      return responseBuilder.setStatus(EligibilityStatus.BLOCK_ALL_PERMISSION_DENIED).build();
    }

    // If all devices are eligible, calculate common proxies.
    Set<DeviceProxyType> commonProxies =
        results.stream()
            .map(r -> (Set<DeviceProxyType>) ImmutableSet.copyOf(r.getSupportedProxyTypesList()))
            .reduce(Sets::intersection)
            .orElse(ImmutableSet.of());

    if (commonProxies.isEmpty()) {
      return responseBuilder.setStatus(EligibilityStatus.BLOCK_NO_COMMON_PROXY).build();
    }

    // calculate common run as candidates.
    Set<String> commonRunAsCandidates =
        results.stream()
            .filter(
                r ->
                    r.getIneligibilityReason().getCode()
                        != IneligibilityReasonCode.PERMISSION_DENIED)
            .map(r -> (Set<String>) ImmutableSet.copyOf(r.getRunAsCandidatesList()))
            .reduce(Sets::intersection)
            .orElse(ImmutableSet.of());

    return responseBuilder
        .setStatus(EligibilityStatus.READY)
        .setSessionOptions(
            SessionOptions.newBuilder()
                .addAllCommonProxyTypes(commonProxies)
                .addAllCommonRunAsCandidates(commonRunAsCandidates)
                .setMaxDurationHours(maxDurationHours)
                .build())
        .build();
  }

  private static GetLabInfoRequest createGetLabInfoRequest(String hostName) {
    return GetLabInfoRequest.newBuilder()
        .setLabQuery(LabQuery.newBuilder().setFilter(createLabQueryFilter(hostName)))
        .build();
  }

  private static LabQuery.Filter createLabQueryFilter(String hostName) {
    return LabQuery.Filter.newBuilder()
        .setLabFilter(
            FilterProto.LabFilter.newBuilder()
                .addLabMatchCondition(
                    FilterProto.LabFilter.LabMatchCondition.newBuilder()
                        .setLabHostNameMatchCondition(
                            FilterProto.LabFilter.LabMatchCondition.LabHostNameMatchCondition
                                .newBuilder()
                                .setCondition(
                                    FilterProto.StringMatchCondition.newBuilder()
                                        .setInclude(
                                            FilterProto.StringMatchCondition.Include.newBuilder()
                                                .addExpected(hostName))))))
        .build();
  }
}
