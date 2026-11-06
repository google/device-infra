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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.util.Comparator.comparingInt;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigPolicy;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysResponse.CandidateDimensionKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the BatchGetConfigurableDimensionKeys RPC. */
@Singleton
public final class BatchGetConfigurableDimensionKeysHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final BatchDeviceConfigPolicy policy;
  private final ListeningExecutorService executor;

  @Inject
  BatchGetConfigurableDimensionKeysHandler(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      BatchDeviceConfigPolicy policy,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.policy = policy;
    this.executor = executor;
  }

  public ListenableFuture<BatchGetConfigurableDimensionKeysResponse>
      batchGetConfigurableDimensionKeys(BatchGetConfigurableDimensionKeysRequest request) {
    logger.atInfo().log(
        "BatchGetConfigurableDimensionKeysRequest: %d devices, list=%s",
        request.getDevicesCount(), request.getDimensionList());

    try {
      configServiceCapabilityFactory.create(UniverseScope.SELF).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(FeServiceException.failedPrecondition(e.getMessage()));
    }

    DimensionList dimensionList = request.getDimensionList();
    if (dimensionList != DimensionList.DIMENSION_LIST_SUPPORTED
        && dimensionList != DimensionList.DIMENSION_LIST_REQUIRED) {
      return immediateFailedFuture(
          FeServiceException.invalidArgument(
              "Dimension list must be DIMENSION_LIST_SUPPORTED or DIMENSION_LIST_REQUIRED."));
    }
    Optional<String> invalidReason =
        BatchDeviceConfigHelper.checkDeviceTargetCount(request.getDevicesList());
    if (invalidReason.isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(invalidReason.get()));
    }

    ImmutableSet<String> selectedIds =
        ImmutableSet.copyOf(
            BatchDeviceConfigHelper.extractDistinctValidDeviceUuids(request.getDevicesList()));

    if (policy.discoverFleetDimensionsFromDeviceConfigs()) {
      return getKeysFromFleetDeviceConfigs(selectedIds, dimensionList);
    }
    return getKeysFromLabInfo(selectedIds, dimensionList);
  }

  private ListenableFuture<BatchGetConfigurableDimensionKeysResponse> getKeysFromFleetDeviceConfigs(
      ImmutableSet<String> selectedIds, DimensionList dimensionList) {
    ListenableFuture<GetLabInfoResponse> labInfoFuture =
        labInfoProvider.getLabInfoAsync(GetLabInfoRequest.getDefaultInstance(), UniverseScope.SELF);
    return Futures.transformAsync(
        labInfoFuture,
        labInfoResponse -> {
          ImmutableList<String> allDeviceIds =
              Stream.concat(
                      BatchDeviceConfigUtil.extractFleetDeviceIds(labInfoResponse).stream(),
                      selectedIds.stream())
                  .distinct()
                  .collect(toImmutableList());
          if (allDeviceIds.isEmpty()) {
            return immediateFuture(
                buildResponse(ImmutableMap.of(), ImmutableMap.of(), dimensionList));
          }
          return Futures.transform(
              configurationProvider.getDeviceConfigs(allDeviceIds, UniverseScope.SELF),
              configs -> {
                ImmutableMap<String, Integer> fleetCounts =
                    countDimensionKeysInConfigs(configs, dimensionList);
                ImmutableList<DeviceConfig> selectedConfigs =
                    configs.stream()
                        .filter(cfg -> selectedIds.contains(cfg.getUuid()))
                        .collect(toImmutableList());
                ImmutableMap<String, Integer> selectedCounts =
                    countDimensionKeysInConfigs(selectedConfigs, dimensionList);
                return buildResponse(selectedCounts, fleetCounts, dimensionList);
              },
              executor);
        },
        executor);
  }

  private ListenableFuture<BatchGetConfigurableDimensionKeysResponse> getKeysFromLabInfo(
      ImmutableSet<String> selectedIds, DimensionList dimensionList) {
    ListenableFuture<List<DeviceConfig>> selectedConfigsFuture =
        selectedIds.isEmpty()
            ? immediateFuture(ImmutableList.of())
            : configurationProvider.getDeviceConfigs(selectedIds.asList(), UniverseScope.SELF);

    ListenableFuture<ImmutableMap<String, Integer>> fleetCountsFuture =
        fetchDimensionFleetCounts(dimensionList);

    return Futures.whenAllSucceed(selectedConfigsFuture, fleetCountsFuture)
        .call(
            () -> {
              ImmutableMap<String, Integer> selectedCounts =
                  countDimensionKeysInConfigs(
                      Futures.getDone(selectedConfigsFuture), dimensionList);
              ImmutableMap<String, Integer> fleetCounts = Futures.getDone(fleetCountsFuture);
              return buildResponse(selectedCounts, fleetCounts, dimensionList);
            },
            executor);
  }

  /**
   * Queries {@code LabInfoService.GetAllDeviceDimensions} so fleet key discovery runs in O(distinct
   * keys) rather than scanning every device via {@code GetLabInfo}. Because {@code
   * GetAllDeviceDimensions} aggregates dimension names across both {@code supported_dimension} and
   * {@code required_dimension}, non-curated custom keys report combined fleet counts while curated
   * keys belonging only to the opposite list are excluded.
   */
  private ListenableFuture<ImmutableMap<String, Integer>> fetchDimensionFleetCounts(
      DimensionList dimensionList) {
    DimensionList oppositeList =
        BatchDeviceConfigUtil.isRequiredDimensionList(dimensionList)
            ? DimensionList.DIMENSION_LIST_SUPPORTED
            : DimensionList.DIMENSION_LIST_REQUIRED;
    Set<String> excludedCuratedKeys =
        Sets.difference(
            policy.getDefaultConfigurableKeys(oppositeList),
            policy.getDefaultConfigurableKeys(dimensionList));
    return Futures.transform(
        labInfoProvider.getAllDeviceDimensionsAsync(
            GetAllDeviceDimensionsRequest.getDefaultInstance(), UniverseScope.SELF),
        response ->
            response.getDimensionsList().stream()
                .filter(dim -> dim.getDeviceCount() > 0)
                .filter(
                    dim ->
                        BatchDeviceConfigUtil.isValidConfigurableDimensionKey(dim.getName(), policy)
                            && !excludedCuratedKeys.contains(dim.getName()))
                .collect(
                    toImmutableMap(
                        dim -> dim.getName(),
                        dim -> (int) Math.min(dim.getDeviceCount(), Integer.MAX_VALUE),
                        Math::max)),
        executor);
  }

  private ImmutableMap<String, Integer> countDimensionKeysInConfigs(
      List<DeviceConfig> configs, DimensionList dimensionList) {
    boolean isRequired = BatchDeviceConfigUtil.isRequiredDimensionList(dimensionList);
    Map<String, Long> counts =
        configs.stream()
            .flatMap(
                config -> {
                  DeviceCompositeDimension composite =
                      config.getBasicConfig().getCompositeDimension();
                  List<DeviceDimension> entries =
                      isRequired
                          ? composite.getRequiredDimensionList()
                          : composite.getSupportedDimensionList();
                  return extractValidKeys(entries);
                })
            .collect(groupingBy(identity(), counting()));
    return counts.entrySet().stream()
        .collect(
            toImmutableMap(
                Map.Entry::getKey, e -> (int) Math.min(e.getValue(), Integer.MAX_VALUE)));
  }

  private Stream<String> extractValidKeys(List<DeviceDimension> dimensions) {
    return dimensions.stream()
        .filter(d -> !d.getValue().isEmpty())
        .map(DeviceDimension::getName)
        .filter(name -> BatchDeviceConfigUtil.isValidConfigurableDimensionKey(name, policy))
        .distinct();
  }

  private BatchGetConfigurableDimensionKeysResponse buildResponse(
      ImmutableMap<String, Integer> selectedCounts,
      ImmutableMap<String, Integer> fleetCounts,
      DimensionList dimensionList) {
    ImmutableSet<String> defaultKeys = policy.getDefaultConfigurableKeys(dimensionList);
    Set<String> allKeys =
        policy.allowCustomDimensions()
            ? Sets.union(Sets.union(fleetCounts.keySet(), selectedCounts.keySet()), defaultKeys)
            : defaultKeys;

    Comparator<CandidateDimensionKey> comparator =
        comparingInt(CandidateDimensionKey::getSelectedDeviceCount)
            .reversed()
            .thenComparing(comparingInt(CandidateDimensionKey::getFleetDeviceCount).reversed())
            .thenComparing(CandidateDimensionKey::getDimensionName);

    ImmutableList<CandidateDimensionKey> candidateKeys =
        allKeys.stream()
            .map(
                key -> {
                  int selectedCount = selectedCounts.getOrDefault(key, 0);
                  int fleetCount = Math.max(fleetCounts.getOrDefault(key, 0), selectedCount);
                  return CandidateDimensionKey.newBuilder()
                      .setDimensionName(key)
                      .setSelectedDeviceCount(selectedCount)
                      .setFleetDeviceCount(fleetCount)
                      .build();
                })
            .sorted(comparator)
            .collect(toImmutableList());

    return BatchGetConfigurableDimensionKeysResponse.newBuilder()
        .addAllCandidateKeys(candidateKeys)
        .addAllSystemDimensionNames(policy.getSortedSystemDetectedDimensionNames())
        .build();
  }
}
