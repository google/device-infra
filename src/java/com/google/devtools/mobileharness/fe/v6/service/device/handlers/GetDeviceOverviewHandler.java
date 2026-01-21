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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Enums;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.FilterProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.fe.v6.service.device.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.BasicDeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.BasicDeviceInfo.Form;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.CapabilitiesInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverviewPageData;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DimensionSourceGroup;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.Dimensions;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.NetworkInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.PermissionInfo;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetDeviceOverview RPC. */
@Singleton
public final class GetDeviceOverviewHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DIMENSION_SOURCE_DETECTED = "Detected by OmniLab";
  private static final String DIMENSION_SOURCE_DEVICE_CONFIG = "From Device Config";
  private static final String DIMENSION_SOURCE_HOST_CONFIG = "From Host Config";

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ListeningExecutorService executor;
  private final HealthAndActivityBuilder healthAndActivityBuilder;
  private final DeviceHeaderInfoBuilder deviceHeaderInfoBuilder;
  private final AsyncLoadingCache<String, DeviceOverviewPageData> overviewCache;

  @Inject
  GetDeviceOverviewHandler(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      ListeningExecutorService executor,
      HealthAndActivityBuilder healthAndActivityBuilder,
      DeviceHeaderInfoBuilder deviceHeaderInfoBuilder) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.deviceHeaderInfoBuilder = deviceHeaderInfoBuilder;
    this.executor = executor;
    this.healthAndActivityBuilder = healthAndActivityBuilder;
    this.overviewCache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1000)
            .executor(executor)
            .buildAsync(new DeviceOverviewLoader());
  }

  public ListenableFuture<DeviceOverviewPageData> getDeviceOverview(
      GetDeviceOverviewRequest request) {
    String universe = request.getUniverse().isEmpty() ? "google_1p" : request.getUniverse();
    String cacheKey = universe + ":" + request.getId();
    if (request.getForceRefresh()) {
      logger.atInfo().log("Force refreshing cache for %s", cacheKey);
      overviewCache.synchronous().invalidate(cacheKey);
    }
    return JdkFutureAdapters.listenInPoolThread(overviewCache.get(cacheKey), executor);
  }

  private class DeviceOverviewLoader implements AsyncCacheLoader<String, DeviceOverviewPageData> {
    @Override
    public CompletableFuture<DeviceOverviewPageData> asyncLoad(String key, Executor executor) {
      ListenableFuture<DeviceOverviewPageData> loadFuture = loadDeviceOverviewPageData(key);
      return toCompletableFuture(loadFuture);
    }
  }

  private <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> listenableFuture) {
    CompletableFuture<T> completableFuture = new CompletableFuture<>();
    Futures.addCallback(
        listenableFuture,
        new FutureCallback<T>() {
          @Override
          public void onSuccess(T result) {
            completableFuture.complete(result);
          }

          @Override
          public void onFailure(Throwable t) {
            completableFuture.completeExceptionally(t);
          }
        },
        executor);
    return completableFuture;
  }

  private ListenableFuture<DeviceOverviewPageData> loadDeviceOverviewPageData(String key) {
    List<String> parts = Splitter.on(':').limit(2).splitToList(key);
    String universe = parts.get(0);
    String deviceId = parts.get(1);
    logger.atInfo().log("Loading device overview for %s", key);

    // 1. Fetch DeviceInfo
    logger.atInfo().log("Fetching DeviceInfo for %s", key);
    ListenableFuture<GetLabInfoResponse> getLabInfoResponseFuture =
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(deviceId), universe);

    ListenableFuture<DeviceInfo> deviceInfoFuture =
        Futures.transform(
            getLabInfoResponseFuture,
            response -> {
              logger.atInfo().log("Received DeviceInfo response for %s", key);
              return response
                  .getLabQueryResult()
                  .getDeviceView()
                  .getGroupedDevices()
                  .getDeviceList()
                  .getDeviceInfoList()
                  .stream()
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new RuntimeException(
                              "Device not found: " + deviceId + " in universe: " + universe));
            },
            executor);

    // 2. Start ConfigProvider fetches early
    logger.atInfo().log("Fetching DeviceConfig for %s", key);
    ListenableFuture<Optional<DeviceConfig>> deviceConfigFuture =
        configurationProvider.getDeviceConfig(deviceId, universe);

    // 3. Fetch LabConfig, depends on DeviceInfo
    ListenableFuture<Optional<LabConfig>> labConfigFuture =
        Futures.transformAsync(
            deviceInfoFuture,
            deviceInfo -> {
              String hostName = deviceInfo.getDeviceLocator().getLabLocator().getHostName();
              logger.atInfo().log("Fetching LabConfig for host %s for key %s", hostName, key);
              return hostName.isEmpty()
                  ? immediateFuture(Optional.empty())
                  : configurationProvider.getLabConfig(hostName, universe);
            },
            executor);

    // 4. Combine and build
    return Futures.whenAllSucceed(deviceInfoFuture, labConfigFuture)
        .callAsync(
            () -> {
              logger.atInfo().log("All base futures succeeded for %s", key);
              DeviceInfo deviceInfo = Futures.getDone(deviceInfoFuture);
              Optional<LabConfig> labConfigOpt = Futures.getDone(labConfigFuture);

              final ListenableFuture<Optional<DeviceConfig>> finalDeviceConfigFuture;
              if (isHostConfigUsed(labConfigOpt)) {
                logger.atInfo().log("Using host config for %s", key);
                deviceConfigFuture.cancel(false);
                finalDeviceConfigFuture = immediateFuture(Optional.empty());
              } else {
                logger.atInfo().log("Using device config for %s", key);
                finalDeviceConfigFuture = deviceConfigFuture;
              }

              return Futures.transform(
                  finalDeviceConfigFuture,
                  deviceConfigOpt -> {
                    logger.atInfo().log("Building DeviceOverview for %s", key);
                    return buildDeviceOverviewPageData(
                        deviceId, deviceInfo, deviceConfigOpt, labConfigOpt);
                  },
                  executor);
            },
            executor);
  }

  private DeviceOverviewPageData buildDeviceOverviewPageData(
      String deviceId,
      DeviceInfo deviceInfo,
      Optional<DeviceConfig> deviceConfigOpt,
      Optional<LabConfig> labConfigOpt) {
    DeviceHeaderInfo headerInfo =
        deviceHeaderInfoBuilder.buildDeviceHeaderInfo(deviceInfo, deviceConfigOpt, labConfigOpt);
    DeviceOverview overview =
        buildDeviceOverview(deviceId, deviceInfo, deviceConfigOpt, labConfigOpt, headerInfo);
    return DeviceOverviewPageData.newBuilder()
        .setOverview(overview)
        .setHeaderInfo(headerInfo)
        .build();
  }

  private DeviceOverview buildDeviceOverview(
      String deviceId,
      DeviceInfo deviceInfo,
      Optional<DeviceConfig> deviceConfigOpt,
      Optional<LabConfig> labConfigOpt,
      DeviceHeaderInfo headerInfo) {
    DeviceOverview.Builder builder = DeviceOverview.newBuilder().setId(deviceId);

    // HostInfo
    builder.setHost(headerInfo.getHost());

    // BasicDeviceInfo
    ImmutableList<DeviceDimension> allDimensions =
        Stream.concat(
                deviceInfo
                    .getDeviceFeature()
                    .getCompositeDimension()
                    .getSupportedDimensionList()
                    .stream(),
                deviceInfo
                    .getDeviceFeature()
                    .getCompositeDimension()
                    .getRequiredDimensionList()
                    .stream())
            .collect(toImmutableList());
    builder.setBasicInfo(buildBasicDeviceInfo(allDimensions));

    // Permissions
    builder.setPermissions(
        PermissionInfo.newBuilder()
            .addAllOwners(deviceInfo.getDeviceFeature().getOwnerList())
            .addAllExecutors(deviceInfo.getDeviceFeature().getExecutorList()));

    // Capabilities
    builder.setCapabilities(
        CapabilitiesInfo.newBuilder()
            .addAllSupportedDrivers(deviceInfo.getDeviceFeature().getDriverList())
            .addAllSupportedDecorators(deviceInfo.getDeviceFeature().getDecoratorList()));

    // Properties
    builder.putAllProperties(
        deviceInfo.getDeviceFeature().getProperties().getPropertyList().stream()
            .collect(toImmutableMap(p -> p.getName(), p -> p.getValue())));

    // Dimensions
    builder.setDimensions(buildDimensions(deviceInfo, deviceConfigOpt, labConfigOpt));

    // HealthAndActivityInfo
    builder.setHealthAndActivity(healthAndActivityBuilder.buildHealthAndActivityInfo(deviceInfo));

    return builder.build();
  }

  private boolean isHostConfigUsed(Optional<LabConfig> labConfigOpt) {
    return labConfigOpt
        .map(
            labConfig ->
                labConfig.getHostProperties().getHostPropertyList().stream()
                    .anyMatch(
                        p ->
                            p.getKey().equals("device_config_mode") && p.getValue().equals("host")))
        .orElse(false);
  }

  private BasicDeviceInfo buildBasicDeviceInfo(List<DeviceDimension> dimensions) {
    BasicDeviceInfo.Builder basicInfo = BasicDeviceInfo.newBuilder();
    ImmutableMap<String, String> dimMap =
        dimensions.stream()
            .collect(toImmutableMap(d -> d.getName(), d -> d.getValue(), (v1, unused) -> v1));

    basicInfo.setModel(dimMap.getOrDefault("model", ""));

    String formStr = dimMap.getOrDefault("device_form", "FORM_UNSPECIFIED");
    Form form =
        Enums.getIfPresent(Form.class, formStr.toUpperCase(Locale.ROOT)).or(Form.FORM_UNSPECIFIED);
    basicInfo
        .setForm(form)
        .setOs(dimMap.getOrDefault("os", ""))
        .setHardware(dimMap.getOrDefault("hardware", ""))
        .setBuild(dimMap.getOrDefault("build", ""));

    String os = dimMap.getOrDefault("os", "");
    if (os.equals("Android")) {
      basicInfo.setVersion(dimMap.getOrDefault("sdk_version", ""));
    } else {
      basicInfo.setVersion(
          dimMap.getOrDefault("software_version", dimMap.getOrDefault("sdk_version", "")));
    }

    try {
      basicInfo.setBatteryLevel(Integer.parseInt(dimMap.getOrDefault("battery_level", "-1")));
    } catch (NumberFormatException e) {
      basicInfo.setBatteryLevel(-1); // Default if not a number
    }

    NetworkInfo.Builder network = NetworkInfo.newBuilder();
    try {
      network.setWifiRssi(Integer.parseInt(dimMap.getOrDefault("wifi_rssi", "0")));
    } catch (NumberFormatException e) {
      network.setWifiRssi(0);
    }
    network.setHasInternet(Boolean.parseBoolean(dimMap.getOrDefault("internet", "false")));
    return basicInfo.setNetwork(network).build();
  }

  private Dimensions buildDimensions(
      DeviceInfo deviceInfo,
      Optional<DeviceConfig> deviceConfigOpt,
      Optional<LabConfig> labConfigOpt) {
    ImmutableMap.Builder<String, DimensionSourceGroup> supportedMap = ImmutableMap.builder();
    ImmutableMap.Builder<String, DimensionSourceGroup> requiredMap = ImmutableMap.builder();

    List<DeviceDimension> configSupported = ImmutableList.of();
    List<DeviceDimension> configRequired = ImmutableList.of();
    String configSourceKey = "";

    if (isHostConfigUsed(labConfigOpt)) {
      configSourceKey = DIMENSION_SOURCE_HOST_CONFIG;
      if (labConfigOpt.get().hasDefaultDeviceConfig()) {
        configSupported =
            labConfigOpt
                .get()
                .getDefaultDeviceConfig()
                .getCompositeDimension()
                .getSupportedDimensionList();
        configRequired =
            labConfigOpt
                .get()
                .getDefaultDeviceConfig()
                .getCompositeDimension()
                .getRequiredDimensionList();
      }
    } else if (deviceConfigOpt.isPresent()) {
      configSourceKey = DIMENSION_SOURCE_DEVICE_CONFIG;
      if (deviceConfigOpt.get().hasBasicConfig()) {
        configSupported =
            deviceConfigOpt
                .get()
                .getBasicConfig()
                .getCompositeDimension()
                .getSupportedDimensionList();
        configRequired =
            deviceConfigOpt
                .get()
                .getBasicConfig()
                .getCompositeDimension()
                .getRequiredDimensionList();
      }
    }

    if (!configSourceKey.isEmpty()) {
      supportedMap.put(
          configSourceKey,
          DimensionSourceGroup.newBuilder()
              .addAllDimensions(convertToFeDimensions(configSupported))
              .build());
      requiredMap.put(
          configSourceKey,
          DimensionSourceGroup.newBuilder()
              .addAllDimensions(convertToFeDimensions(configRequired))
              .build());
    }

    ImmutableSet<DeviceDimension> configDims =
        Stream.concat(configSupported.stream(), configRequired.stream()).collect(toImmutableSet());

    List<DeviceDimension> allSupported =
        deviceInfo.getDeviceFeature().getCompositeDimension().getSupportedDimensionList();
    List<DeviceDimension> allRequired =
        deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimensionList();

    ImmutableList<com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension>
        detectedSupported =
            allSupported.stream()
                .filter(d -> !configDims.contains(d))
                .map(this::convertToFeDimension)
                .collect(toImmutableList());
    ImmutableList<com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension>
        detectedRequired =
            allRequired.stream()
                .filter(d -> !configDims.contains(d))
                .map(this::convertToFeDimension)
                .collect(toImmutableList());

    if (!detectedSupported.isEmpty()) {
      supportedMap.put(
          DIMENSION_SOURCE_DETECTED,
          DimensionSourceGroup.newBuilder().addAllDimensions(detectedSupported).build());
    }
    if (!detectedRequired.isEmpty()) {
      requiredMap.put(
          DIMENSION_SOURCE_DETECTED,
          DimensionSourceGroup.newBuilder().addAllDimensions(detectedRequired).build());
    }

    return Dimensions.newBuilder()
        .putAllSupported(supportedMap.buildKeepingLast())
        .putAllRequired(requiredMap.buildKeepingLast())
        .build();
  }

  private ImmutableList<
          com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension>
      convertToFeDimensions(List<DeviceDimension> dimensions) {
    return dimensions.stream().map(this::convertToFeDimension).collect(toImmutableList());
  }

  private com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
      convertToFeDimension(DeviceDimension dimension) {
    return com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension.newBuilder()
        .setName(dimension.getName())
        .setValue(dimension.getValue())
        .build();
  }

  private GetLabInfoRequest createGetLabInfoRequest(String deviceId) {
    return GetLabInfoRequest.newBuilder()
        .setLabQuery(
            LabQuery.newBuilder()
                .setFilter(
                    Filter.newBuilder()
                        .setDeviceFilter(
                            FilterProto.DeviceFilter.newBuilder()
                                .addDeviceMatchCondition(
                                    FilterProto.DeviceFilter.DeviceMatchCondition.newBuilder()
                                        .setDeviceUuidMatchCondition(
                                            FilterProto.DeviceFilter.DeviceMatchCondition
                                                .DeviceUuidMatchCondition.newBuilder()
                                                .setCondition(
                                                    FilterProto.StringMatchCondition.newBuilder()
                                                        .setInclude(
                                                            FilterProto.StringMatchCondition.Include
                                                                .newBuilder()
                                                                .addExpected(deviceId)))))))
                .setDeviceViewRequest(LabQuery.DeviceViewRequest.getDefaultInstance()))
        .build();
  }
}
