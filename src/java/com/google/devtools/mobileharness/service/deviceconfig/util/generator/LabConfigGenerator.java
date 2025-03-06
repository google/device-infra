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

import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.shared.util.message.ProtoUtil;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.protobuf.Int32Value;
import com.google.wireless.qa.mobileharness.shared.proto.Config.ApiConfig;
import com.google.wireless.qa.mobileharness.shared.proto.Config.DeviceConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The utility class to help generating {@link LabConfig} instances. */
public final class LabConfigGenerator {

  private LabConfigGenerator() {}

  /**
   * Generates a {@link LabConfig} from the given old model of {@link ApiConfig}.
   *
   * @param labHost the host name of the lab
   * @param apiConfig the old lab/device config
   */
  public static LabConfig fromApiConfig(String labHost, ApiConfig apiConfig) {
    List<DeviceConfig> deviceConfigs = apiConfig.getDeviceConfigList();
    LabConfig.Builder labConfig = LabConfig.newBuilder().setHostName(labHost);

    // If the over_tcp is set as true, the ID of a device is like "ip:port".
    labConfig.addAllOverTcpIp(
        deviceConfigs.stream()
            .filter(DeviceConfig::getOverTcp)
            .map(DeviceConfig::getId)
            .collect(Collectors.toList()));

    // Merges the monitored control IDs set in api config and all device configs.
    Set<String> monitoredControlIds = new HashSet<>(apiConfig.getMonitoredDeviceIdList());
    monitoredControlIds.addAll(
        deviceConfigs.stream()
            .filter(DeviceConfig::getMonitor)
            .map(DeviceConfig::getId)
            .collect(Collectors.toList()));
    labConfig.addAllMonitoredDeviceUuid(monitoredControlIds);

    // Generates a basic device config from the api config.
    BasicDeviceConfig.Builder basicDeviceConfig =
        BasicDeviceConfig.newBuilder()
            .addAllOwner(apiConfig.getOwnerList())
            .addAllExecutor(apiConfig.getExecutorList());
    if (apiConfig.hasCompositeDimension() || apiConfig.getDimensionCount() != 0) {
      DeviceCompositeDimension deviceCompositeDimension =
          apiConfig.getCompositeDimension().toBuilder()
              .addAllSupportedDimension(
                  StrPairUtil.convertToDeviceDimension(apiConfig.getDimensionList()))
              .build();
      basicDeviceConfig.setCompositeDimension(deviceCompositeDimension);
    }
    if (apiConfig.hasDefaultWifi()) {
      WifiConfig.Builder wifiConfig = WifiConfig.newBuilder();
      ProtoUtil.convert(apiConfig.getDefaultWifi(), wifiConfig);
      basicDeviceConfig.setDefaultWifi(wifiConfig);
    }
    if (apiConfig.hasMaxConsecutiveTest()) {
      basicDeviceConfig.setMaxConsecutiveTest(Int32Value.of(apiConfig.getMaxConsecutiveTest()));
    }
    if (apiConfig.hasMaxConsecutiveFail()) {
      basicDeviceConfig.setMaxConsecutiveFail(Int32Value.of(apiConfig.getMaxConsecutiveFail()));
    }
    // Set lab config as host mode when there's no device level device config is specified in api
    // config file so the default device config will apply for all devices in the lab host.
    if (deviceConfigs.isEmpty()) {
      labConfig
          .getHostPropertiesBuilder()
          .addHostProperty(HostProperty.newBuilder().setKey("device_config_mode").setValue("host"));
    }

    return labConfig.setDefaultDeviceConfig(basicDeviceConfig).build();
  }
}
