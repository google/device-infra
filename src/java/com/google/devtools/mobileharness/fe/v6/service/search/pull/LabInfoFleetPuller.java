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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.Page;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.protobuf.FieldMask;
import java.util.Map;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Pulls the core fleet or on-demand single dimensions from {@code LabInfoService}.
 *
 * <p>Unlike the per-entity detail-page reads, this asks for the whole fleet in one call: no filter
 * (every lab and device) and no page limit, using {@code DeviceInfoMask} and {@code LabInfoMask}
 * derived from the schema registry. It accepts the master's cached data, which is enough for a
 * periodically refreshed index that does not need the per-query realtime path.
 *
 * <p>{@link #pull} is non-blocking: it returns the {@link ListenableFuture} from the async {@code
 * LabInfoService} call, transformed to its {@link LabQueryResult}. Timeouts and failure handling
 * are applied by the refresher around the returned future.
 */
public final class LabInfoFleetPuller {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final FieldMask SINGLE_DIM_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths("device_locator.id")
          .addPaths("device_feature.composite_dimension")
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
      String keyId, UniverseScope universeScope) {
    String dimName =
        keyId.startsWith(DeviceKeys.PREFIX_DIMENSION)
            ? keyId.substring(DeviceKeys.PREFIX_DIMENSION.length())
            : (keyId.startsWith("dim::") ? keyId.substring("dim::".length()) : keyId);

    GetLabInfoRequest request =
        GetLabInfoRequest.newBuilder()
            .setLabQuery(
                LabQuery.newBuilder()
                    .setLabViewRequest(LabQuery.LabViewRequest.getDefaultInstance())
                    .setMask(
                        LabQuery.Mask.newBuilder()
                            .setDeviceInfoMask(
                                LabQuery.Mask.DeviceInfoMask.newBuilder()
                                    .setFieldMask(SINGLE_DIM_FIELD_MASK)
                                    .setSupportedDimensionsMask(
                                        LabQuery.Mask.DeviceInfoMask.DimensionsMask.newBuilder()
                                            .addDimensionNames(dimName))
                                    .setRequiredDimensionsMask(
                                        LabQuery.Mask.DeviceInfoMask.DimensionsMask.newBuilder()
                                            .addDimensionNames(dimName)))))
            .setPage(Page.newBuilder().setLimit(0))
            .build();

    Stopwatch stopwatch = Stopwatch.createStarted();
    return Futures.transform(
        labInfoProvider.getLabInfoAsync(request, universeScope),
        response -> {
          ImmutableMap<String, ImmutableList<String>> uuidToValues =
              response.getLabQueryResult().getLabView().getLabDataList().stream()
                  .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
                  .filter(deviceInfo -> !deviceInfo.getDeviceLocator().getId().isEmpty())
                  .map(
                      deviceInfo ->
                          Map.entry(
                              deviceInfo.getDeviceLocator().getId(),
                              dimensionValues(deviceInfo, dimName)))
                  .filter(entry -> !entry.getValue().isEmpty())
                  .collect(
                      toImmutableMap(
                          Map.Entry::getKey, Map.Entry::getValue, (first, second) -> second));

          logger.atInfo().log(
              "GetLabInfo on-demand dim '%s' returned %d devices in %d ms.",
              keyId, uuidToValues.size(), stopwatch.elapsed().toMillis());
          return DimensionOverlayRaw.create(keyId, uuidToValues);
        },
        directExecutor());
  }

  /** Starts an on-demand single dimension pull from the self universe. */
  public ListenableFuture<DimensionOverlayRaw> pullDimension(String keyId) {
    return pullDimension(keyId, UniverseScope.SELF);
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
