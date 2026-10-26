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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.Page;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsResponse.CandidateDimensionValue;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.protobuf.FieldMask;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetDimensionValueSuggestions RPC. */
@Singleton
public final class GetDimensionValueSuggestionsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final FieldMask SUPPORTED_DIM_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths("device_locator.id")
          .addPaths("device_feature.composite_dimension.supported_dimension")
          .build();

  private static final FieldMask REQUIRED_DIM_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths("device_locator.id")
          .addPaths("device_feature.composite_dimension.required_dimension")
          .build();

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final Environment environment;
  private final ListeningExecutorService executor;

  @Inject
  GetDimensionValueSuggestionsHandler(
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

  public ListenableFuture<GetDimensionValueSuggestionsResponse> getDimensionValueSuggestions(
      GetDimensionValueSuggestionsRequest request, UniverseScope universe) {
    logger.atInfo().log("GetDimensionValueSuggestionsRequest: %s", request);

    try {
      configServiceCapabilityFactory.create(universe).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(e);
    }

    String dimensionName = BatchDeviceConfigUtil.cleanDimensionName(request.getDimensionName());
    if (dimensionName.isEmpty()) {
      return immediateFuture(GetDimensionValueSuggestionsResponse.getDefaultInstance());
    }
    DimensionList dimensionList = request.getDimensionList();

    if (environment.isAts()) {
      return getSuggestionsFromDeviceConfigs(dimensionName, dimensionList, universe);
    }
    return getSuggestionsFromLabInfo(dimensionName, dimensionList, universe);
  }

  private ListenableFuture<GetDimensionValueSuggestionsResponse> getSuggestionsFromDeviceConfigs(
      String dimensionName, DimensionList dimensionList, UniverseScope universe) {
    return Futures.transformAsync(
        labInfoProvider.getLabInfoAsync(GetLabInfoRequest.getDefaultInstance(), universe),
        labInfoResponse -> {
          ImmutableList<String> deviceIds =
              BatchDeviceConfigUtil.extractFleetDeviceIds(labInfoResponse);
          if (deviceIds.isEmpty()) {
            return immediateFuture(GetDimensionValueSuggestionsResponse.getDefaultInstance());
          }
          return Futures.transform(
              configurationProvider.getDeviceConfigs(deviceIds, universe),
              configs -> {
                ImmutableMap<String, DeviceConfig> configByDeviceId =
                    configs.stream()
                        .filter(cfg -> !cfg.getUuid().isEmpty())
                        .collect(
                            toImmutableMap(
                                DeviceConfig::getUuid, cfg -> cfg, (first, second) -> second));
                ImmutableList<ImmutableList<String>> perDeviceValueSets =
                    deviceIds.stream()
                        .map(
                            id ->
                                configByDeviceId.containsKey(id)
                                    ? BatchDeviceConfigUtil.getDimensionValues(
                                        configByDeviceId.get(id).getBasicConfig(),
                                        dimensionName,
                                        dimensionList)
                                    : ImmutableList.<String>of())
                        .collect(toImmutableList());
                return aggregateValueSets(perDeviceValueSets);
              },
              executor);
        },
        executor);
  }

  private ListenableFuture<GetDimensionValueSuggestionsResponse> getSuggestionsFromLabInfo(
      String dimensionName, DimensionList dimensionList, UniverseScope universe) {
    boolean isRequired = BatchDeviceConfigUtil.isRequiredDimensionList(dimensionList);
    DeviceInfoMask.Builder deviceInfoMaskBuilder =
        DeviceInfoMask.newBuilder()
            .setFieldMask(isRequired ? REQUIRED_DIM_FIELD_MASK : SUPPORTED_DIM_FIELD_MASK);
    DimensionsMask dimensionsMask =
        DimensionsMask.newBuilder().addDimensionNames(dimensionName).build();
    if (isRequired) {
      deviceInfoMaskBuilder.setRequiredDimensionsMask(dimensionsMask);
    } else {
      deviceInfoMaskBuilder.setSupportedDimensionsMask(dimensionsMask);
    }

    GetLabInfoRequest labInfoRequest =
        GetLabInfoRequest.newBuilder()
            .setLabQuery(
                LabQuery.newBuilder()
                    .setLabViewRequest(LabQuery.LabViewRequest.getDefaultInstance())
                    .setMask(
                        LabQuery.Mask.newBuilder()
                            .setLabInfoMask(
                                LabInfoMask.newBuilder()
                                    .setFieldMask(FieldMask.getDefaultInstance()))
                            .setDeviceInfoMask(deviceInfoMaskBuilder)))
            .setPage(Page.newBuilder().setLimit(0))
            .setUseRealtimeData(false)
            .build();

    return Futures.transform(
        labInfoProvider.getLabInfoAsync(labInfoRequest, universe),
        response -> {
          if (!response.getLabQueryResult().hasLabView()) {
            return GetDimensionValueSuggestionsResponse.getDefaultInstance();
          }
          ImmutableList<ImmutableList<String>> perDeviceValueSets =
              response.getLabQueryResult().getLabView().getLabDataList().stream()
                  .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
                  .map(
                      deviceInfo ->
                          BatchDeviceConfigUtil.getDimensionValues(
                              deviceInfo.getDeviceFeature().getCompositeDimension(),
                              dimensionName,
                              dimensionList))
                  .collect(toImmutableList());
          return aggregateValueSets(perDeviceValueSets);
        },
        executor);
  }

  private static GetDimensionValueSuggestionsResponse aggregateValueSets(
      List<ImmutableList<String>> perDeviceValueSets) {
    Map<ImmutableList<String>, ImmutableList<String>> displayByCanon = new LinkedHashMap<>();
    Map<ImmutableList<String>, Integer> countsByCanon = new LinkedHashMap<>();

    for (ImmutableList<String> values : perDeviceValueSets) {
      ImmutableList<String> canon = BatchDeviceConfigUtil.canonicalizeValues(values);
      if (canon.isEmpty()) {
        // Devices without the dimension are not a suggestion; clearing is ClearDimensionValues.
        continue;
      }
      displayByCanon.putIfAbsent(canon, values);
      countsByCanon.merge(canon, 1, Integer::sum);
    }

    ImmutableList<CandidateDimensionValue> candidates =
        countsByCanon.entrySet().stream()
            .map(
                entry ->
                    CandidateDimensionValue.newBuilder()
                        .addAllValues(displayByCanon.get(entry.getKey()))
                        .setDeviceCount(entry.getValue())
                        .build())
            .sorted(comparingInt(CandidateDimensionValue::getDeviceCount).reversed())
            .collect(toImmutableList());

    return GetDimensionValueSuggestionsResponse.newBuilder()
        .addAllCandidateValues(candidates)
        .build();
  }
}
