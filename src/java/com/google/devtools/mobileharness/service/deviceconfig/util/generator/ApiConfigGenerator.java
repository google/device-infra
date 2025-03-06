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

package com.google.devtools.mobileharness.service.deviceconfig.util.generator;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.shared.util.message.ProtoUtil;
import com.google.wireless.qa.mobileharness.shared.proto.Config;
import com.google.wireless.qa.mobileharness.shared.proto.Config.ApiConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The utility class to help generating {@link ApiConfig} and {@link Config.DeviceConfig} instances.
 */
public final class ApiConfigGenerator {

  private ApiConfigGenerator() {}

  /** Device control ID and device config. */
  @AutoValue
  public abstract static class DeviceControlIdAndConfig {
    public abstract String deviceControlId();

    public abstract DeviceConfig deviceConfig();

    public static DeviceControlIdAndConfig of(String deviceControlId, DeviceConfig deviceConfig) {
      return new AutoValue_ApiConfigGenerator_DeviceControlIdAndConfig(
          deviceControlId, deviceConfig);
    }
  }

  /**
   * Generates a {@link ApiConfig} from the given {@link LabConfig} and list of {@link
   * DeviceConfig}s which are connecting to the lab currently.
   */
  public static ApiConfig generateApiConfig(
      LabConfig labConfig, List<DeviceControlIdAndConfig> deviceConfigs) {
    BasicDeviceConfig defaultDeviceConfig = labConfig.getDefaultDeviceConfig();
    ApiConfig.Builder apiConfig = ApiConfig.newBuilder();
    if (defaultDeviceConfig.hasMaxConsecutiveTest()) {
      apiConfig.setMaxConsecutiveTest(defaultDeviceConfig.getMaxConsecutiveTest().getValue());
    }
    if (defaultDeviceConfig.hasMaxConsecutiveFail()) {
      apiConfig.setMaxConsecutiveFail(defaultDeviceConfig.getMaxConsecutiveFail().getValue());
    }
    apiConfig.addAllOwner(defaultDeviceConfig.getOwnerList());
    apiConfig.addAllExecutor(defaultDeviceConfig.getExecutorList());
    Set<String> overTcpIds = new HashSet<>(labConfig.getOverTcpIpList());
    apiConfig.addAllDeviceConfig(
        deviceConfigs.stream()
            .map(
                deviceConfig ->
                    generateDeviceConfig(
                        deviceConfig.deviceControlId(),
                        deviceConfig.deviceConfig(),
                        overTcpIds.contains(deviceConfig.deviceControlId())))
            .collect(Collectors.toList()));
    if (defaultDeviceConfig.hasDefaultWifi()) {
      Config.WifiConfig.Builder wifiConfig = Config.WifiConfig.newBuilder();
      ProtoUtil.convert(defaultDeviceConfig.getDefaultWifi(), wifiConfig);
      apiConfig.setDefaultWifi(wifiConfig);
    }
    if (defaultDeviceConfig.hasCompositeDimension()) {
      apiConfig.setCompositeDimension(defaultDeviceConfig.getCompositeDimension());
    }
    apiConfig.addAllMonitoredDeviceId(labConfig.getMonitoredDeviceUuidList());

    return apiConfig.build();
  }

  /**
   * Generates a {@link Config.DeviceConfig} from the given {@link DeviceConfig} and the over_tcp
   * flag.
   */
  public static Config.DeviceConfig generateDeviceConfig(
      String deviceControlId, DeviceConfig deviceConfig, boolean overTcp) {
    BasicDeviceConfig basicDeviceConfig = deviceConfig.getBasicConfig();
    Config.DeviceConfig.Builder oldDeviceConfig =
        Config.DeviceConfig.newBuilder()
            .setId(deviceControlId)
            .addAllOwner(basicDeviceConfig.getOwnerList())
            .addAllExecutor(basicDeviceConfig.getExecutorList());
    if (overTcp) {
      oldDeviceConfig.setOverTcp(overTcp);
    }
    if (basicDeviceConfig.hasCompositeDimension()) {
      oldDeviceConfig.setCompositeDimension(basicDeviceConfig.getCompositeDimension());
    }
    if (basicDeviceConfig.hasDefaultWifi()) {
      Config.WifiConfig.Builder wifiConfig = Config.WifiConfig.newBuilder();
      ProtoUtil.convert(basicDeviceConfig.getDefaultWifi(), wifiConfig);
      oldDeviceConfig.setDefaultWifi(wifiConfig);
    }
    if (basicDeviceConfig.hasMaxConsecutiveFail()) {
      oldDeviceConfig.setMaxConsecutiveFail(basicDeviceConfig.getMaxConsecutiveFail().getValue());
    }
    if (basicDeviceConfig.hasMaxConsecutiveTest()) {
      oldDeviceConfig.setMaxConsecutiveTest(basicDeviceConfig.getMaxConsecutiveTest().getValue());
    }
    return oldDeviceConfig.build();
  }
}
