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
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.Page;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysResponse.CandidateDimensionKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.protobuf.FieldMask;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the BatchGetConfigurableDimensionKeys RPC. */
@Singleton
public final class BatchGetConfigurableDimensionKeysHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> DEFAULT_REQUIRED_KEYS = ImmutableSet.of("pool");
  private static final ImmutableSet<String> DEFAULT_SUPPORTED_KEYS =
      ImmutableSet.of("label", "cluster");

  private static final FieldMask SUPPORTED_DIM_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths("device_feature.composite_dimension.supported_dimension")
          .build();

  private static final FieldMask REQUIRED_DIM_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths("device_feature.composite_dimension.required_dimension")
          .build();

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final Environment environment;
  private final ListeningExecutorService executor;

  @Inject
  BatchGetConfigurableDimensionKeysHandler(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      Environment environment,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.environment = environment;
    this.executor = executor;
  }

  public ListenableFuture<BatchGetConfigurableDimensionKeysResponse>
      batchGetConfigurableDimensionKeys(
          BatchGetConfigurableDimensionKeysRequest request, UniverseScope universe) {
    logger.atInfo().log("BatchGetConfigurableDimensionKeysRequest: %s", request);

    try {
      configServiceCapabilityFactory.create(universe).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(e);
    }

    DimensionList dimensionList = request.getDimensionList();
    ImmutableSet<String> selectedIds = ImmutableSet.copyOf(request.getDeviceIdsList());

    if (environment.isAts()) {
      return getKeysFromFleetDeviceConfigs(selectedIds, dimensionList, universe);
    }
    return getKeysForInternalFleet(selectedIds, dimensionList, universe);
  }

  private ListenableFuture<BatchGetConfigurableDimensionKeysResponse> getKeysFromFleetDeviceConfigs(
      ImmutableSet<String> selectedIds, DimensionList dimensionList, UniverseScope universe) {
    ListenableFuture<GetLabInfoResponse> labInfoFuture =
        labInfoProvider.getLabInfoAsync(GetLabInfoRequest.getDefaultInstance(), universe);
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
              configurationProvider.getDeviceConfigs(allDeviceIds, universe),
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

  private ListenableFuture<BatchGetConfigurableDimensionKeysResponse> getKeysForInternalFleet(
      ImmutableSet<String> selectedIds, DimensionList dimensionList, UniverseScope universe) {
    ListenableFuture<List<DeviceConfig>> selectedConfigsFuture =
        selectedIds.isEmpty()
            ? immediateFuture(ImmutableList.of())
            : configurationProvider.getDeviceConfigs(selectedIds.asList(), universe);

    ListenableFuture<ImmutableMap<String, Integer>> fleetCountsFuture =
        fetchDimensionFleetCounts(dimensionList, universe);

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

  private ListenableFuture<ImmutableMap<String, Integer>> fetchDimensionFleetCounts(
      DimensionList dimensionList, UniverseScope universe) {
    boolean isRequired = BatchDeviceConfigUtil.isRequiredDimensionList(dimensionList);
    GetLabInfoRequest request =
        GetLabInfoRequest.newBuilder()
            .setLabQuery(
                LabQuery.newBuilder()
                    .setLabViewRequest(LabQuery.LabViewRequest.getDefaultInstance())
                    .setMask(
                        LabQuery.Mask.newBuilder()
                            .setLabInfoMask(
                                LabInfoMask.newBuilder()
                                    .setFieldMask(FieldMask.getDefaultInstance()))
                            .setDeviceInfoMask(
                                DeviceInfoMask.newBuilder()
                                    .setFieldMask(
                                        isRequired
                                            ? REQUIRED_DIM_FIELD_MASK
                                            : SUPPORTED_DIM_FIELD_MASK))))
            .setPage(Page.newBuilder().setLimit(0))
            .setUseRealtimeData(false)
            .build();
    return Futures.transform(
        labInfoProvider.getLabInfoAsync(request, universe),
        response -> {
          if (!response.getLabQueryResult().hasLabView()) {
            return ImmutableMap.of();
          }
          Map<String, Long> counts =
              response.getLabQueryResult().getLabView().getLabDataList().stream()
                  .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
                  .flatMap(
                      deviceInfo -> {
                        DeviceCompositeDimension composite =
                            deviceInfo.getDeviceFeature().getCompositeDimension();
                        return extractValidKeys(
                            isRequired
                                ? composite.getRequiredDimensionList()
                                : composite.getSupportedDimensionList());
                      })
                  .collect(groupingBy(identity(), counting()));
          return counts.entrySet().stream()
              .collect(
                  toImmutableMap(
                      Map.Entry::getKey, e -> (int) Math.min(e.getValue(), Integer.MAX_VALUE)));
        },
        executor);
  }

  private static ImmutableMap<String, Integer> countDimensionKeysInConfigs(
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

  private static Stream<String> extractValidKeys(List<DeviceDimension> dimensions) {
    return dimensions.stream()
        .filter(d -> !d.getValue().isEmpty())
        .map(d -> BatchDeviceConfigUtil.cleanDimensionName(d.getName()))
        .filter(BatchDeviceConfigUtil::isValidConfigurableDimensionKey)
        .distinct();
  }

  private static BatchGetConfigurableDimensionKeysResponse buildResponse(
      ImmutableMap<String, Integer> selectedCounts,
      ImmutableMap<String, Integer> fleetCounts,
      DimensionList dimensionList) {
    ImmutableSet<String> defaultKeys =
        BatchDeviceConfigUtil.isRequiredDimensionList(dimensionList)
            ? DEFAULT_REQUIRED_KEYS
            : DEFAULT_SUPPORTED_KEYS;
    Set<String> allKeys =
        Sets.union(Sets.union(fleetCounts.keySet(), selectedCounts.keySet()), defaultKeys);

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
        .addAllSystemDimensionNames(BatchDeviceConfigUtil.SORTED_SYSTEM_DETECTED_DIMENSION_NAMES)
        .build();
  }
}
