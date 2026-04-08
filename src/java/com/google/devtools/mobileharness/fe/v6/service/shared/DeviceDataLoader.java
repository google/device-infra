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

package com.google.devtools.mobileharness.fe.v6.service.shared;

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.query.proto.FilterProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Loader for fetching device metadata and configurations from multiple backends. */
@Singleton
public class DeviceDataLoader {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ListeningExecutorService executor;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final UniverseFactory universeFactory;

  @Inject
  DeviceDataLoader(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      ListeningExecutorService executor,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      UniverseFactory universeFactory) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.executor = executor;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.universeFactory = universeFactory;
  }

  /** Represents how the device's configuration is managed and where it originates. */
  public enum ManagementMode {
    /** Managed individually via a specific DeviceConfig. */
    PER_DEVICE,
    /** Managed at the host level via LabConfig's default_device_config. */
    HOST_MANAGED,
    /** Configuration service is unavailable, unsupported, or inaccessible. */
    NOT_SUPPORTED
  }

  /** Consolidated data for a device, including its metadata and effective configuration. */
  @AutoValue
  public abstract static class DeviceData {
    public abstract DeviceInfo deviceInfo();

    /** The configuration currently in effect for the device. */
    public abstract DeviceConfig effectiveDeviceConfig();

    /** The management policy applied to this device. */
    public abstract ManagementMode managementMode();

    /** The raw LabConfig if successfully fetched. */
    public abstract Optional<LabConfig> rawLabConfig();

    /** The raw individual DeviceConfig if successfully fetched and relevant. */
    public abstract Optional<DeviceConfig> rawIndividualConfig();

    public boolean isHostManaged() {
      return managementMode() == ManagementMode.HOST_MANAGED;
    }

    public boolean isConfigSupported() {
      return managementMode() != ManagementMode.NOT_SUPPORTED;
    }

    public static DeviceData create(
        DeviceInfo deviceInfo,
        DeviceConfig effectiveDeviceConfig,
        ManagementMode managementMode,
        Optional<LabConfig> rawLabConfig,
        Optional<DeviceConfig> rawIndividualConfig) {
      return new AutoValue_DeviceDataLoader_DeviceData(
          deviceInfo, effectiveDeviceConfig, managementMode, rawLabConfig, rawIndividualConfig);
    }
  }

  /**
   * Loads device data asynchronously.
   *
   * @param deviceId the unique ID of the device
   * @param universe the universe string
   * @deprecated Use {@link #loadDeviceData(String, UniverseScope)} instead.
   */
  @Deprecated
  public ListenableFuture<DeviceData> loadDeviceData(String deviceId, String universe) {
    return loadDeviceData(deviceId, universeFactory.create(universe));
  }

  /**
   * Loads device data asynchronously.
   *
   * @param deviceId the unique ID of the device
   * @param universe the universe the device belongs to
   */
  public ListenableFuture<DeviceData> loadDeviceData(String deviceId, UniverseScope universe) {
    logger.atInfo().log("Loading device data for %s (universe: %s)", deviceId, universe);

    // Parallel fetch start: DeviceInfo (Required) and Individual Config (Speculative)
    ListenableFuture<DeviceInfo> deviceInfoFuture = getDeviceInfoAsync(deviceId, universe);
    ListenableFuture<Optional<DeviceConfig>> individualConfigFuture =
        getSafeDeviceConfigAsync(deviceId, universe);

    // Sequential chain for LabConfig (depends on host_name)
    return Futures.transformAsync(
        deviceInfoFuture,
        deviceInfo -> {
          String hostName = deviceInfo.getDeviceLocator().getLabLocator().getHostName();
          ListenableFuture<Optional<LabConfig>> labConfigFuture =
              getSafeLabConfigAsync(hostName, universe);

          return Futures.whenAllSucceed(labConfigFuture, individualConfigFuture)
              .call(
                  () -> {
                    Optional<LabConfig> labConfigOpt = Futures.getDone(labConfigFuture);
                    Optional<DeviceConfig> individualConfigOpt =
                        Futures.getDone(individualConfigFuture);

                    return resolveDeviceData(
                        deviceId, universe, deviceInfo, labConfigOpt, individualConfigOpt);
                  },
                  executor);
        },
        executor);
  }

  private DeviceData resolveDeviceData(
      String deviceId,
      UniverseScope universe,
      DeviceInfo deviceInfo,
      Optional<LabConfig> labConfigOpt,
      Optional<DeviceConfig> individualConfigOpt) {

    // If config service is not supported for the universe, or we couldn't even get a LabConfig,
    // we assume Config Service is not supported/accessible.
    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()
        || labConfigOpt.isEmpty()) {
      return DeviceData.create(
          deviceInfo,
          DeviceConfig.getDefaultInstance(),
          ManagementMode.NOT_SUPPORTED,
          Optional.empty(),
          Optional.empty());
    }

    LabConfig labConfig = labConfigOpt.get();
    if (isHostManaged(labConfig)) {
      DeviceConfig effectiveConfig =
          DeviceConfig.newBuilder()
              .setUuid(deviceId)
              .setBasicConfig(labConfig.getDefaultDeviceConfig())
              .build();
      return DeviceData.create(
          deviceInfo,
          effectiveConfig,
          ManagementMode.HOST_MANAGED,
          Optional.of(labConfig),
          Optional.empty());
    }

    // Individual management mode
    DeviceConfig effectiveConfig = individualConfigOpt.orElse(DeviceConfig.getDefaultInstance());
    return DeviceData.create(
        deviceInfo,
        effectiveConfig,
        ManagementMode.PER_DEVICE,
        Optional.of(labConfig),
        individualConfigOpt);
  }

  private ListenableFuture<Optional<DeviceConfig>> getSafeDeviceConfigAsync(
      String deviceId, UniverseScope universe) {
    return Futures.catching(
        configurationProvider.getDeviceConfig(deviceId, universe),
        Throwable.class,
        t -> {
          logger.atWarning().withCause(t).log("Failed to fetch DeviceConfig for %s", deviceId);
          return Optional.empty();
        },
        executor);
  }

  private ListenableFuture<Optional<LabConfig>> getSafeLabConfigAsync(
      String hostName, UniverseScope universe) {
    return Futures.catching(
        configurationProvider.getLabConfig(hostName, universe),
        Throwable.class,
        t -> {
          logger.atWarning().withCause(t).log("Failed to fetch LabConfig for %s", hostName);
          return Optional.empty();
        },
        executor);
  }

  private ListenableFuture<DeviceInfo> getDeviceInfoAsync(String deviceId, UniverseScope universe) {
    return Futures.transform(
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(deviceId), universe),
        response ->
            response
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
                            "Device not found: " + deviceId + " in universe: " + universe)),
        executor);
  }

  private static boolean isHostManaged(LabConfig labConfig) {
    return labConfig.getHostProperties().getHostPropertyList().stream()
        .anyMatch(p -> p.getKey().equals("device_config_mode") && p.getValue().equals("host"));
  }

  private static GetLabInfoRequest createGetLabInfoRequest(String deviceId) {
    return GetLabInfoRequest.newBuilder()
        .setLabQuery(
            LabQuery.newBuilder()
                .setFilter(
                    LabQuery.Filter.newBuilder()
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
