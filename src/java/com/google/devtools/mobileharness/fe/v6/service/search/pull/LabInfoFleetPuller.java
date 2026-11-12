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

package com.google.devtools.mobileharness.fe.v6.service.search.pull;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.DimensionMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch.GreaterThanOrEqual;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMultimapMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.Page;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw.DeviceKey;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.DeviceDimensionSummary;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.protobuf.FieldMask;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Pulls the core fleet, or the values of one or more dimensions on demand, from {@code
 * LabInfoService}. {@link #pullDimensions} fetches several dimensions in a single {@code
 * GetLabInfo} call.
 *
 * <p>Unlike the per-entity detail-page reads, {@link #pull} asks for the whole fleet in one call:
 * no filter (every lab and device) and no page limit, using {@code DeviceInfoMask} and {@code
 * LabInfoMask} derived from the schema registry. Single-dimension on-demand pulls ({@link
 * #pullDimension} and single-key {@link #pullDimensions}) pre-filter devices with a {@link
 * DeviceFilter} matching devices that carry at least one value for the dimension. It accepts the
 * master's cached data, which is enough for a periodically refreshed index that does not need the
 * per-query realtime path.
 *
 * <p>{@link #pull} is non-blocking: it returns the {@link ListenableFuture} from the async {@code
 * LabInfoService} call, transformed to its {@link LabQueryResult}. Timeouts and failure handling
 * are applied by the refresher around the returned future.
 */
public final class LabInfoFleetPuller {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> EXCLUDED_DIMENSIONS =
      ImmutableSet.of("subdevice_dimensions");

  private static final FieldMask SINGLE_DIM_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths("device_locator.id")
          .addPaths("device_feature.composite_dimension")
          .build();

  private static final LabInfoMask LAB_HOST_NAME_MASK =
      LabInfoMask.newBuilder()
          .setFieldMask(FieldMask.newBuilder().addPaths("lab_locator.host_name").build())
          .build();

  private final LabInfoProvider labInfoProvider;

  @Inject
  LabInfoFleetPuller(LabInfoProvider labInfoProvider) {
    this.labInfoProvider = labInfoProvider;
  }

  /**
   * Starts a core fleet pull for the given masks from the local (self) universe; the future
   * completes with its {@link LabQueryResult}.
   */
  public ListenableFuture<LabQueryResult> pull(
      DeviceInfoMask deviceInfoMask, LabInfoMask labInfoMask) {
    return pull(deviceInfoMask, labInfoMask, UniverseScope.SELF);
  }

  /** Starts a core fleet pull for the given masks and universe. */
  public ListenableFuture<LabQueryResult> pull(
      DeviceInfoMask deviceInfoMask, LabInfoMask labInfoMask, UniverseScope universeScope) {
    logger.atInfo().log(
        "Issuing GetLabInfo to the master (%s universe): full fleet, no page limit, cached"
            + " data.",
        universeScope);
    GetLabInfoRequest request =
        GetLabInfoRequest.newBuilder()
            .setLabQuery(
                LabQuery.newBuilder()
                    .setLabViewRequest(LabQuery.LabViewRequest.getDefaultInstance())
                    .setMask(
                        LabQuery.Mask.newBuilder()
                            .setDeviceInfoMask(deviceInfoMask)
                            .setLabInfoMask(labInfoMask)))
            .setPage(Page.newBuilder().setLimit(0))
            .setUseRealtimeData(false)
            .build();
    Stopwatch stopwatch = Stopwatch.createStarted();
    return Futures.transform(
        labInfoProvider.getLabInfoAsync(request, universeScope),
        response -> {
          LabQueryResult result = response.getLabQueryResult();
          logger.atInfo().log(
              "GetLabInfo returned %d labs in %d ms.",
              result.getLabView().getLabDataCount(), stopwatch.elapsed().toMillis());
          return result;
        },
        directExecutor());
  }

  /** Starts an on-demand single dimension pull from the specified universe. */
  public ListenableFuture<DimensionOverlayRaw> pullDimension(
      DeviceKeyDescriptor dimensionKey, UniverseScope universeScope) {
    return Futures.transform(
        pullDimensions(ImmutableSet.of(dimensionKey), universeScope),
        overlays -> overlays.get(dimensionKey.id()),
        directExecutor());
  }

  /** Starts an on-demand single dimension pull from the self universe. */
  public ListenableFuture<DimensionOverlayRaw> pullDimension(DeviceKeyDescriptor dimensionKey) {
    return pullDimension(dimensionKey, UniverseScope.SELF);
  }

  /**
   * Starts a batched on-demand pull of multiple dimensions from the specified universe in a single
   * {@code GetLabInfo} call, returning a map keyed by {@link DeviceKeyDescriptor#id()}.
   */
  public ListenableFuture<ImmutableMap<String, DimensionOverlayRaw>> pullDimensions(
      Set<DeviceKeyDescriptor> dimensionKeys, UniverseScope universeScope) {
    if (dimensionKeys.isEmpty()) {
      return immediateFuture(ImmutableMap.of());
    }
    dimensionKeys.forEach(
        key -> checkArgument(key.isDimension(), "Key must be a dimension: %s", key.id()));
    ImmutableSet<String> dimNames =
        dimensionKeys.stream().map(DeviceKeyDescriptor::bareName).collect(toImmutableSet());

    LabQuery.Builder labQuery =
        LabQuery.newBuilder()
            .setLabViewRequest(LabQuery.LabViewRequest.getDefaultInstance())
            .setMask(
                LabQuery.Mask.newBuilder()
                    .setLabInfoMask(LAB_HOST_NAME_MASK)
                    .setDeviceInfoMask(
                        LabQuery.Mask.DeviceInfoMask.newBuilder()
                            .setFieldMask(SINGLE_DIM_FIELD_MASK)
                            .setSupportedDimensionsMask(
                                LabQuery.Mask.DeviceInfoMask.DimensionsMask.newBuilder()
                                    .addAllDimensionNames(dimNames))
                            .setRequiredDimensionsMask(
                                LabQuery.Mask.DeviceInfoMask.DimensionsMask.newBuilder()
                                    .addAllDimensionNames(dimNames))));
    // DeviceFilter combines multiple DeviceMatchConditions with AND, so pre-filtering by dimension
    // key presence applies only when pulling a single dimension name. Interactive UI edits add one
    // cold key at a time (hitting this filtered path), whereas cold multi-key deep links stay a
    // single unfiltered masked GetLabInfo RPC rather than fanning out N concurrent RPCs to Master.
    if (dimNames.size() == 1) {
      labQuery.setFilter(singleDimensionFilter(Iterables.getOnlyElement(dimNames)));
    }
    GetLabInfoRequest request =
        GetLabInfoRequest.newBuilder()
            .setLabQuery(labQuery)
            .setPage(Page.newBuilder().setLimit(0))
            .build();

    Stopwatch stopwatch = Stopwatch.createStarted();
    return Futures.transform(
        labInfoProvider.getLabInfoAsync(request, universeScope),
        response -> {
          ImmutableList<ScopedDevice> devices =
              response.getLabQueryResult().getLabView().getLabDataList().stream()
                  .flatMap(labData -> {
                    String hostName = labData.getLabInfo().getLabLocator().getHostName();
                    return labData.getDeviceList().getDeviceInfoList().stream()
                        .filter(deviceInfo -> !deviceInfo.getDeviceLocator().getId().isEmpty())
                        .map(deviceInfo ->
                            new ScopedDevice(
                                new DeviceKey(
                                    universeScope, hostName, deviceInfo.getDeviceLocator().getId()),
                                deviceInfo));
                  })
                  .collect(toImmutableList());
          // Keyed by each requested key id, so every requested key gets an entry.
          ImmutableMap<String, DimensionOverlayRaw> overlays =
              dimensionKeys.stream()
                  .collect(
                      toImmutableMap(
                          DeviceKeyDescriptor::id,
                          key ->
                              DimensionOverlayRaw.createForDevices(
                                  key.id(), extractDimensionValues(devices, key.bareName())),
                          (first, second) -> first));

          logger.atInfo().log(
              "GetLabInfo on-demand dims %s (%s universe) received %d devices (filtered=%s) in"
                  + " %d ms.",
              overlays.keySet(),
              universeScope,
              devices.size(),
              dimNames.size() == 1,
              stopwatch.elapsed().toMillis());
          return overlays;
        },
        directExecutor());
  }

  private record ScopedDevice(DeviceKey key, DeviceInfo deviceInfo) {}

  /** Starts a batched on-demand pull of multiple dimensions from the self universe. */
  public ListenableFuture<ImmutableMap<String, DimensionOverlayRaw>> pullDimensions(
      Set<DeviceKeyDescriptor> dimensionKeys) {
    return pullDimensions(dimensionKeys, UniverseScope.SELF);
  }

  private static LabQuery.Filter singleDimensionFilter(String dimName) {
    return LabQuery.Filter.newBuilder()
        .setDeviceFilter(
            DeviceFilter.newBuilder()
                .addDeviceMatchCondition(
                    DeviceMatchCondition.newBuilder()
                        .setDimensionMatchCondition(
                            DimensionMatchCondition.newBuilder()
                                .setCondition(
                                    StringMultimapMatchCondition.newBuilder()
                                        .setKey(dimName)
                                        .setValueCondition(
                                            StringListMatchCondition.newBuilder()
                                                .setLengthMatch(
                                                    IntegerMatch.newBuilder()
                                                        .setGreaterThanOrEqual(
                                                            GreaterThanOrEqual.newBuilder()
                                                                .setMinValue(1))))))))
        .build();
  }

  private static ImmutableMap<DeviceKey, ImmutableList<String>> extractDimensionValues(
      ImmutableList<ScopedDevice> devices, String dimName) {
    return devices.stream()
        .map(device -> Map.entry(device.key(), dimensionValues(device.deviceInfo(), dimName)))
        .filter(entry -> !entry.getValue().isEmpty())
        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> second));
  }

  /**
   * Discovers all device dimension names and their device counts present across labs in the
   * specified universe by calling {@code LabInfoService.GetAllDeviceDimensions}.
   */
  public ListenableFuture<ImmutableMap<String, Integer>> pullDimensionCounts(
      UniverseScope universeScope) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    return Futures.transform(
        labInfoProvider.getAllDeviceDimensionsAsync(
            GetAllDeviceDimensionsRequest.getDefaultInstance(), universeScope),
        response -> {
          ImmutableMap<String, Integer> result =
              response.getDimensionsList().stream()
                  .filter(dim ->
                      !dim.getName().isEmpty()
                          && dim.getDeviceCount() > 0
                          && !EXCLUDED_DIMENSIONS.contains(dim.getName()))
                  .collect(
                      toImmutableMap(
                          DeviceDimensionSummary::getName,
                          dim -> (int) Math.min(dim.getDeviceCount(), Integer.MAX_VALUE),
                          Integer::sum));
          logger.atInfo().log(
              "GetAllDeviceDimensions (%s universe) discovered %d dimensions in %d ms.",
              universeScope, result.size(), stopwatch.elapsed().toMillis());
          return result;
        },
        directExecutor());
  }

  /** Discovers all device dimension names and their device counts in the self universe. */
  public ListenableFuture<ImmutableMap<String, Integer>> pullDimensionCounts() {
    return pullDimensionCounts(UniverseScope.SELF);
  }

  /**
   * Collects the distinct non-empty values of {@code dimName} carried by a device, across both its
   * supported and required composite dimensions.
   */
  private static ImmutableList<String> dimensionValues(DeviceInfo deviceInfo, String dimName) {
    DeviceCompositeDimension composite = deviceInfo.getDeviceFeature().getCompositeDimension();
    return Stream.concat(
            composite.getSupportedDimensionList().stream(),
            composite.getRequiredDimensionList().stream())
        .filter(dim -> dim.getName().equals(dimName) && !dim.getValue().isEmpty())
        .map(DeviceDimension::getValue)
        .distinct()
        .collect(toImmutableList());
  }
}
