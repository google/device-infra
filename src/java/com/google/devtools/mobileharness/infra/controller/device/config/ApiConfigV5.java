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

package com.google.devtools.mobileharness.infra.controller.device.config;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.DetectorSpecs;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.OverSshDevice;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.protobuf.Any;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.concurrent.GuardedBy;

/** The class which contains the user configured DeviceConfig/LabConfig. */
public class ApiConfigV5 implements ApiConfig {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int DEFAULT_MAX_CONSECUTIVE_FAILS = 5;
  private static final int DEFAULT_MAX_CONSECUTIVE_TESTS = 10000;
  public static final String DEVICE_DEFAULT_OWNER = "mobileharness-device-default-owner";
  private static final String DEVICE_DEFAULT_EXECUTOR = "mobileharness-device-default-executor";

  // The Key of device config mode. The value of this param is "device" by default.
  private static final String DEVICE_CONFIG_MODE_KEY = "device_config_mode";

  private volatile boolean isInitialized;

  private final Object deviceConfigIsSyncedLock = new Object();

  @GuardedBy("deviceConfigIsSyncedLock")
  private final Set<String> deviceConfigIsSyncedDevices = new HashSet<>();

  /**
   * Singleton holder for lazy initialization.
   *
   * @see <a href="http://en.wikipedia.org/wiki/Double-checked_locking">Double-checked locking</a>
   */
  private static class SingletonHolder {

    /** The singleton instance of {@link ApiConfigV5}. */
    private static final ApiConfigV5 singleton;

    static {
      singleton =
          new ApiConfigV5(DeviceIdManager.getInstance(), new SystemUtil(), Clock.systemUTC());
    }
  }

  public static ApiConfigV5 getInstance() {
    return SingletonHolder.singleton;
  }

  private final DeviceIdManager deviceIdManager;
  private final SystemUtil systemUtil;
  private final Clock clock;
  private LabConfig labConfig = LabConfig.getDefaultInstance();

  /** <DeviceControlId, DeviceConfig> map. */
  private final Map<String, DeviceConfig> deviceConfigs = new ConcurrentHashMap<>();

  /** <DeviceControlId, DeviceConfig> map for tenant configs. */
  private final Map<String, DeviceConfig> tenantDeviceConfigs = new ConcurrentHashMap<>();

  private final Set<ApiConfigListener> listeners = ConcurrentHashMap.newKeySet();

  private boolean isDefaultPublic;

  private boolean isDefaultSynced = true;

  @VisibleForTesting
  ApiConfigV5(DeviceIdManager deviceIdManager, SystemUtil systemUtil, Clock clock) {
    this.deviceIdManager = deviceIdManager;
    this.systemUtil = systemUtil;
    this.clock = clock;
  }

  @Override
  public void initialize(boolean isDefaultPublic, boolean isDefaultSynced, String hostName) {
    if (!isInitialized) {
      synchronized (this) {
        if (!isInitialized) {
          logger.atInfo().log(
              "Initialize ApiConfig: isDefaultPublic=%s, isDefaultSynced=%s, hostName=%s",
              isDefaultPublic, isDefaultSynced, hostName);
          isInitialized = true;
          this.isDefaultPublic = isDefaultPublic;
          this.isDefaultSynced = isDefaultSynced;
          labConfig =
              hostName.isEmpty()
                  ? LabConfig.getDefaultInstance()
                  : LabConfig.newBuilder()
                      .setHostName(hostName)
                      .setDefaultDeviceConfig(
                          isDefaultPublic
                              ? getPublicBasicDeviceConfig()
                              : getBasicDeviceConfigWithDefaultOwner())
                      .build();
        }
      }
    }
  }

  @Override
  public int getMaxConsecutiveFail(String deviceControlId) {
    BasicDeviceConfig basicDeviceConfig = getBasicDeviceConfig(deviceControlId);
    return basicDeviceConfig.hasMaxConsecutiveFail()
        ? basicDeviceConfig.getMaxConsecutiveFail().getValue()
        : DEFAULT_MAX_CONSECUTIVE_FAILS;
  }

  @Override
  public int getMaxConsecutiveTest(String deviceControlId) {
    BasicDeviceConfig basicDeviceConfig = getBasicDeviceConfig(deviceControlId);
    return basicDeviceConfig.hasMaxConsecutiveTest()
        ? basicDeviceConfig.getMaxConsecutiveTest().getValue()
        : DEFAULT_MAX_CONSECUTIVE_TESTS;
  }

  @Override
  public List<String> getOwners(String deviceControlId) {
    Set<String> owners = new HashSet<>(getBasicDeviceConfig(deviceControlId).getOwnerList());
    DeviceConfig tenantConfig = tenantDeviceConfigs.get(deviceControlId);
    if (tenantConfig != null) {
      owners.addAll(tenantConfig.getBasicConfig().getOwnerList());
    }
    return new ArrayList<>(owners);
  }

  @Override
  public List<String> getExecutors(String deviceControlId) {
    Set<String> executors = new HashSet<>(getBasicDeviceConfig(deviceControlId).getExecutorList());
    DeviceConfig tenantConfig = tenantDeviceConfigs.get(deviceControlId);
    if (tenantConfig != null) {
      executors.addAll(tenantConfig.getBasicConfig().getExecutorList());
    }
    return new ArrayList<>(executors);
  }

  @Override
  public Optional<Any> getCustomizedConfig(String deviceControlId) {
    return Optional.ofNullable(deviceConfigs.get(deviceControlId))
        .map(DeviceConfig::getCustomizedConfig);
  }

  @Override
  public List<StrPair> getSupportedDimensions(String deviceControlId) {
    Optional<DeviceId> deviceId = deviceIdManager.getDeviceIdFromControlId(deviceControlId);
    List<StrPair> deviceExtraDimensions = new ArrayList<>();
    List<String> labels = Flags.instance().extraDeviceLabels.getNonNull();
    if (!labels.isEmpty()) {
      labels.forEach(
          label ->
              deviceExtraDimensions.add(
                  StrPair.newBuilder().setName("label").setValue(label).build()));
    }
    if (deviceId.isPresent() && getMonitoredDeviceUuids().contains(deviceId.get().uuid())) {
      deviceExtraDimensions.add(StrPair.newBuilder().setName("monitored").setValue("true").build());
    }

    ImmutableList<StrPair> dimensions =
        Stream.concat(
                StrPairUtil.convertFromDeviceDimension(
                    getBasicDeviceConfig(deviceControlId)
                        .getCompositeDimension()
                        .getSupportedDimensionList()
                        .stream()
                        .map(this::replaceEnvVariableInDimension)
                        .collect(toImmutableList()))
                    .stream(),
                deviceExtraDimensions.stream())
            .distinct()
            .collect(toImmutableList());
    logger.atFine().log("Get supported dimensions for %s: %s", deviceControlId, dimensions);
    return dimensions;
  }

  private DeviceDimension replaceEnvVariableInDimension(DeviceDimension dimension) {
    if (dimension.getValue().startsWith("$")) {
      String envName = dimension.getValue().substring(1);
      String envValue = systemUtil.getEnv(envName);
      if (envValue != null) {
        return DeviceDimension.newBuilder().setName(dimension.getName()).setValue(envValue).build();
      }
    }
    return dimension;
  }

  @Override
  public List<StrPair> getRequiredDimensions(String deviceControlId) {
    ImmutableList<StrPair> dimensions =
        StrPairUtil.convertFromDeviceDimension(
            getBasicDeviceConfig(deviceControlId)
                .getCompositeDimension()
                .getRequiredDimensionList()
                .stream()
                .map(this::replaceEnvVariableInDimension)
                .collect(toImmutableList()));
    logger.atFine().log("Get required dimensions for %s: %s", deviceControlId, dimensions);
    return dimensions;
  }

  @Override
  public Basic.WifiConfig getDefaultWifi(String deviceControlId) {
    return getBasicDeviceConfig(deviceControlId).getDefaultWifi();
  }

  @Override
  public List<String> getMonitoredDeviceUuids() {
    return labConfig.getMonitoredDeviceUuidList();
  }

  @Override
  public List<String> getOverTcpDeviceControlIds() {
    return labConfig.getOverTcpIpList();
  }

  @Override
  public Map<String, List<String>> getOverSshDevice() {
    Map<String, List<String>> sshDeviceInfo = new HashMap<>();
    List<OverSshDevice> sshDeviceList = labConfig.getOverSshList();
    for (OverSshDevice device : sshDeviceList) {
      sshDeviceInfo.putIfAbsent(
          device.getIpAddress(),
          ImmutableList.of(device.getUsername(), device.getPassword(), device.getSshDeviceType()));
    }
    return sshDeviceInfo;
  }

  /**
   * Sets the device configs. It updates the devices' configs whose control ID exists in the
   * parameters. For the existed device config whose ID is not set in the parameter, it will not be
   * updated or removed.
   *
   * @param deviceConfigList the list of <device control id, DeviceConfig> pair.
   */
  @Override
  public void setDeviceConfigs(Map<String, DeviceConfig> deviceConfigList) {
    ImmutableSet.Builder<String> changedControlIdsBuilder = ImmutableSet.builder();
    for (Entry<String, DeviceConfig> deviceConfig : deviceConfigList.entrySet()) {
      String deviceControlId = deviceConfig.getKey();
      DeviceConfig newDeviceConfig = deviceConfig.getValue();
      DeviceConfig existedDeviceConfig = deviceConfigs.get(deviceControlId);
      if (!newDeviceConfig.equals(existedDeviceConfig)) {
        logger.atInfo().log(
            "Set device %s's DeviceConfig to %s ", deviceControlId, newDeviceConfig);
        deviceConfigs.put(deviceControlId, newDeviceConfig);
        changedControlIdsBuilder.add(deviceControlId);
      }
    }
    ImmutableSet<String> changedControlIdsList = changedControlIdsBuilder.build();
    if (!changedControlIdsList.isEmpty()) {
      for (ApiConfigListener listener : listeners) {
        listener.onDeviceConfigChange(changedControlIdsList);
      }
    }
  }

  /** Sets the lab level config. */
  @Override
  public void setLabConfig(LabConfig labConfig) throws MobileHarnessException {
    if (this.labConfig.equals(labConfig)) {
      return;
    }
    logger.atInfo().log("Set LabConfig to %s", labConfig);
    this.labConfig = labConfig;
    for (ApiConfigListener listener : listeners) {
      listener.onLabConfigChange();
    }
  }

  @Override
  public void setTenantDeviceConfig(String deviceControlId, DeviceConfig tenantConfig) {
    DeviceConfig existingTenantConfig = tenantDeviceConfigs.get(deviceControlId);
    if (!tenantConfig.equals(existingTenantConfig)) {
      logger.atInfo().atMostEvery(2, MINUTES).log(
          "Set TenantDeviceConfig for device %s to %s ", deviceControlId, tenantConfig);
      tenantDeviceConfigs.put(deviceControlId, tenantConfig);
    }
  }

  @Override
  public Optional<DeviceConfig> getDeviceConfigToStore(String deviceControlId) {
    DeviceConfig deviceConfig = deviceConfigs.get(deviceControlId);
    if (deviceConfig != null) {
      return Optional.of(deviceConfig);
    }
    Optional<DeviceId> deviceId = deviceIdManager.getDeviceIdFromControlId(deviceControlId);
    // For the device with stable UUID, need to store default device config if it has not been
    // configured before.
    if (deviceId.isPresent() && !deviceId.get().isUuidVolatile()) {
      if (!labConfig.getDefaultDeviceConfig().equals(BasicDeviceConfig.getDefaultInstance())) {
        BasicDeviceConfig.Builder defaultDeviceConfig =
            labConfig.getDefaultDeviceConfig().toBuilder();
        if (!isDefaultPublic) {
          if (defaultDeviceConfig.getOwnerCount() == 0) {
            defaultDeviceConfig.addOwner(DEVICE_DEFAULT_OWNER);
          }
          if (defaultDeviceConfig.getExecutorCount() == 0) {
            defaultDeviceConfig.addExecutor(DEVICE_DEFAULT_EXECUTOR);
          }
        }
        return Optional.of(
            DeviceConfig.newBuilder()
                .setUuid(deviceId.get().uuid())
                .setBasicConfig(defaultDeviceConfig)
                .build());
      } else if (isDefaultPublic) {
        return Optional.of(
            DeviceConfig.newBuilder()
                .setUuid(deviceId.get().uuid())
                .setBasicConfig(getPublicBasicDeviceConfig())
                .build());
      } else {
        return Optional.of(
            DeviceConfig.newBuilder()
                .setUuid(deviceId.get().uuid())
                .setBasicConfig(getBasicDeviceConfigWithDefaultOwner())
                .build());
      }
    }
    return Optional.empty();
  }

  @Override
  public LabConfig getLabConfigToStore() {
    return labConfig;
  }

  @Override
  public List<String> getTestbedUuidList() {
    return labConfig.getTestbedUuidList();
  }

  @Override
  public List<String> getMiscDeviceUuids() {
    return labConfig.getMiscDeviceUuidList();
  }

  @Override
  public boolean isDeviceConfigSynced(String deviceControlId) {
    synchronized (deviceConfigIsSyncedLock) {
      return isDefaultSynced || deviceConfigIsSyncedDevices.contains(deviceControlId);
    }
  }

  @Override
  public void setDeviceConfigSynced(String deviceControlId) {
    synchronized (deviceConfigIsSyncedLock) {
      boolean isAdded = deviceConfigIsSyncedDevices.add(deviceControlId);
      if (isAdded) {
        logger.atInfo().log("Device %s is synced", deviceControlId);
        deviceConfigIsSyncedLock.notifyAll();
      }
    }
  }

  @Override
  public DetectorSpecs getDetectorSpecs() {
    return labConfig.getDetectorSpecs();
  }

  @Override
  public boolean waitUntilDeviceConfigSynced(String deviceControlId, Duration timeout)
      throws InterruptedException {
    Instant timeoutInstant = clock.instant().plus(timeout);
    synchronized (deviceConfigIsSyncedLock) {
      while (clock.instant().isBefore(timeoutInstant)) {
        if (isDeviceConfigSynced(deviceControlId)) {
          return true;
        }
        deviceConfigIsSyncedLock.wait(Duration.ofSeconds(1).toMillis());
      }
    }
    return false;
  }

  /**
   * Returns the host properties of the lab. Host properties include those configured by users in
   * the lab config and those auto-generated by the lab.
   */
  @Override
  public HostProperties getHostProperties() {
    return labConfig.getHostProperties();
  }

  private boolean isHostMode() {
    return getHostProperties().getHostPropertyList().stream()
        .filter(property -> property.getKey().equals(DEVICE_CONFIG_MODE_KEY))
        .map(property -> property.getValue().equals("host"))
        .findFirst()
        .orElse(false);
  }

  @Override
  public void addListener(ApiConfigListener listener) {
    listeners.add(listener);
  }

  private BasicDeviceConfig getBasicDeviceConfig(String deviceControlId) {
    DeviceConfig deviceConfig = deviceConfigs.get(deviceControlId);
    Optional<DeviceId> deviceId = deviceIdManager.getDeviceIdFromControlId(deviceControlId);

    if (isHostMode()) {
      return labConfig.getDefaultDeviceConfig();
    } else if (deviceConfig != null) {
      return deviceConfig.getBasicConfig();
    } else if (deviceId.isPresent() && deviceId.get().isUuidVolatile()) {
      return labConfig.getDefaultDeviceConfig();
    } else if (isDefaultPublic) {
      return getPublicBasicDeviceConfig();
    } else {
      return getBasicDeviceConfigWithDefaultOwner();
    }
  }

  private BasicDeviceConfig getBasicDeviceConfigWithDefaultOwner() {
    return BasicDeviceConfig.newBuilder()
        .addOwner(DEVICE_DEFAULT_OWNER)
        .addExecutor(DEVICE_DEFAULT_EXECUTOR)
        .build();
  }

  private BasicDeviceConfig getPublicBasicDeviceConfig() {
    return BasicDeviceConfig.getDefaultInstance();
  }
}
