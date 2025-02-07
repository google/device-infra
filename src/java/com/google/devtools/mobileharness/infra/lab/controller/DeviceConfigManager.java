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

package com.google.devtools.mobileharness.infra.lab.controller;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocatorConfigPair;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * The lab/device config manager which loads configs from config server and change local configs.
 */
public abstract class DeviceConfigManager implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration CHECK_DEVICE_CONFIG_SHORT_INTERVAL = Duration.ofSeconds(2);
  private static final Duration CHECK_DEVICE_CONFIG_LONG_INTERVAL = Duration.ofSeconds(30);
  private static final Duration SHORT_INTERVAL_EFFECTIVE_DURATION = Duration.ofSeconds(30);

  private final ApiConfig apiConfig;
  private final String hostName;
  private final LocalDeviceManager localDeviceManager;
  private final DeviceIdManager deviceIdManager;
  private final Sleeper sleeper;
  private final Clock clock;

  public DeviceConfigManager(
      LocalDeviceManager localDeviceManager,
      DeviceIdManager deviceIdManager,
      ApiConfig apiConfig,
      String hostName) {
    this.localDeviceManager = localDeviceManager;
    this.deviceIdManager = deviceIdManager;
    this.apiConfig = apiConfig;
    this.hostName = hostName;
    this.sleeper = Sleeper.defaultSleeper();
    this.clock = Clock.systemUTC();
  }

  @Override
  public void run() {
    Instant shortIntervalEndTime = clock.instant().plus(SHORT_INTERVAL_EFFECTIVE_DURATION);
    while (!Thread.interrupted()) {
      // Only refresh configs when ApiConfigV5 is enabled.
      if (apiConfig != null) {
        try {
          refreshLabConfig();
        } catch (MobileHarnessException e) {
          logger.atSevere().withCause(e).log("Failed to refresh lab config.");
        } catch (Throwable e) {
          logger.atSevere().withCause(e).log("FATAL ERROR");
        }

        try {
          refreshDeviceConfigs();
        } catch (MobileHarnessException e) {
          logger.atSevere().withCause(e).log("Failed to refresh device configs.");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (Throwable e) {
          logger.atSevere().withCause(e).log("FATAL ERROR");
        }
      }

      try {
        sleeper.sleep(
            clock.instant().isAfter(shortIntervalEndTime)
                ? CHECK_DEVICE_CONFIG_LONG_INTERVAL
                : CHECK_DEVICE_CONFIG_SHORT_INTERVAL);
      } catch (InterruptedException e) {
        logger.atSevere().log("Interrupted: %s", e.getMessage());
        Thread.currentThread().interrupt();
      }
    }
  }

  @VisibleForTesting
  void refreshDeviceConfigs() throws MobileHarnessException, InterruptedException {
    logger.atFine().log("Start to load device configs from config service.");
    // Get the active device UUID.
    ImmutableSet<String> activeDeviceUuids =
        localDeviceManager.getAllDeviceStatus(false).keySet().stream()
            .map(Device::getDeviceUuid)
            .collect(toImmutableSet());
    logger.atFine().log("There are %s devices in device id map", activeDeviceUuids.size());

    // Map from UUID to Control ID for all local devices with UUID.
    Map<String, String> deviceUuidToControlDeviceIdMap =
        deviceIdManager.getUuidToDeviceIdMap().entrySet().stream()
            .filter(uuid -> activeDeviceUuids.contains(uuid.getKey()))
            .collect(toMap(Entry::getKey, e -> e.getValue().controlId()));
    apiConfig.getTestbedUuidList().stream()
        .filter(uuid -> !deviceUuidToControlDeviceIdMap.containsKey(uuid))
        .forEach(uuid -> deviceUuidToControlDeviceIdMap.put(uuid, uuid));
    logger.atFine().log(
        "Try to load %d devices config after filtering", deviceUuidToControlDeviceIdMap.size());

    ImmutableList<DeviceLocator> deviceLocators =
        deviceUuidToControlDeviceIdMap.keySet().stream()
            .map(
                uuid -> DeviceLocator.newBuilder().setDeviceUuid(uuid).setLabHost(hostName).build())
            .collect(toImmutableList());
    if (deviceLocators.isEmpty()) {
      return;
    }
    try {
      List<DeviceConfig> deviceConfigs = loadDeviceConfigs(deviceLocators);
      // Map from UUID to device config. If one device with UUID doesn't have config in device
      // config server, it will not be in this map.
      ImmutableMap<String, DeviceConfig> remoteDeviceUuidToConfigMap =
          deviceConfigs.stream().collect(toImmutableMap(DeviceConfig::getUuid, identity()));

      // The first element in Pair is Control ID.
      Map<String, DeviceConfig> deviceConfigsNeedToUpdateToLocal = new HashMap<>();
      List<DeviceLocatorConfigPair> deviceConfigsNeedToStore = new ArrayList<>();
      for (Map.Entry<String, String> device : deviceUuidToControlDeviceIdMap.entrySet()) {
        String deviceUuid = device.getKey();
        String deviceControlId = device.getValue();

        // If a device config exists in server, prepare to update local device config from
        // the config in server; If not exists, prepare to update server from local device config.
        DeviceConfig remoteDeviceConfig = remoteDeviceUuidToConfigMap.get(deviceUuid);
        if (remoteDeviceConfig != null) {
          deviceConfigsNeedToUpdateToLocal.put(deviceControlId, remoteDeviceConfig);
        } else {
          Optional<DeviceConfig> localDeviceConfig =
              apiConfig.getDeviceConfigToStore(deviceControlId);
          if (localDeviceConfig.isPresent() && activeDeviceUuids.contains(deviceUuid)) {
            deviceConfigsNeedToStore.add(
                DeviceLocatorConfigPair.newBuilder()
                    .setDeviceLocator(DeviceLocator.newBuilder().setDeviceUuid(deviceUuid).build())
                    .setDeviceConfig(localDeviceConfig.get().toBuilder().setUuid(deviceUuid))
                    .build());
          }
        }
      }
      if (!deviceConfigsNeedToUpdateToLocal.isEmpty()) {
        logger.atInfo().log(
            "Update local device configs: %s.",
            deviceConfigsNeedToUpdateToLocal.entrySet().stream()
                .map(entry -> String.format("%s: %s", entry.getKey(), entry.getValue()))
                .collect(joining(", ")));
        apiConfig.setDeviceConfigs(deviceConfigsNeedToUpdateToLocal);
        onDeviceConfigUpdatedToLocal();
      }
      if (!deviceConfigsNeedToStore.isEmpty()) {
        storeDeviceConfigs(deviceConfigsNeedToStore);
      }
    } finally {
      for (Map.Entry<String, String> device : deviceUuidToControlDeviceIdMap.entrySet()) {
        apiConfig.setDeviceConfigSynced(device.getValue());
      }
    }
  }

  /**
   * Updates local config with remote if remote config exists. Otherwise, update remote with local.
   *
   * <p>If the remote config contains testbed UUIDs, this function will retrieve the device configs
   * for these UUIDs and use {@link #storeLabConfig} to update the testbed content from the device
   * configs.
   *
   * @throws MobileHarnessException if RPC call to device config service fails or file operations
   *     failed while updating remote testbed directory
   */
  @VisibleForTesting
  void refreshLabConfig() throws MobileHarnessException {
    Optional<LabConfig> remoteLabConfig = loadLabConfig(hostName);
    LabConfig localLabConfig = apiConfig.getLabConfigToStore();
    // If lab config exists in device config server, update local ApiConfig with it;
    // If not, use local ApiConfig to update to device config server
    if (remoteLabConfig.isEmpty()) {
      storeLabConfig(localLabConfig);
      return;
    }
    logger.atFine().log("Update local lab config to %s.", remoteLabConfig);
    apiConfig.setLabConfig(remoteLabConfig.get());
  }

  /** Loads the lab config from the config storage, like config service, local file, etc. */
  protected abstract Optional<LabConfig> loadLabConfig(String hostName)
      throws MobileHarnessException;

  /** Stores the lab config to the config storage, like config service, local file, etc. */
  protected abstract void storeLabConfig(LabConfig labConfig) throws MobileHarnessException;

  /** Loads the device configs from the config storage, like config service, local file, etc. */
  protected abstract List<DeviceConfig> loadDeviceConfigs(List<DeviceLocator> deviceLocators)
      throws MobileHarnessException;

  /** Stores the device configs to the config storage, like config service, local file, etc. */
  protected abstract void storeDeviceConfigs(List<DeviceLocatorConfigPair> deviceConfigs)
      throws MobileHarnessException;

  /** Do extra work after device config is updated to local. */
  protected abstract void onDeviceConfigUpdatedToLocal()
      throws MobileHarnessException, InterruptedException;
}
