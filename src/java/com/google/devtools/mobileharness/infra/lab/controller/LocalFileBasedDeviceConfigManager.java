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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocatorConfigPair;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.LabDevice.LabDeviceConfig;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfigFileProcessor;
import java.util.List;
import java.util.Optional;

/** The device config manager based on local config file. */
public class LocalFileBasedDeviceConfigManager extends DeviceConfigManager {

  private final ApiConfigFileProcessor apiConfigFileProcessor;

  public LocalFileBasedDeviceConfigManager(
      LocalDeviceManager localDeviceManager,
      DeviceIdManager deviceIdManager,
      ApiConfig apiConfig,
      ApiConfigFileProcessor apiConfigFileProcessor) {
    super(localDeviceManager, deviceIdManager, apiConfig, "");
    this.apiConfigFileProcessor = apiConfigFileProcessor;
  }

  @Override
  protected Optional<LabConfig> loadLabConfig(String hostName) throws MobileHarnessException {
    Optional<LabDeviceConfig> result = apiConfigFileProcessor.readConfigFile();
    return result.map(labDeviceConfig -> labDeviceConfig.getLabConfig());
  }

  @Override
  protected void storeLabConfig(LabConfig labConfig) {}

  @Override
  protected List<DeviceConfig> loadDeviceConfigs(List<DeviceLocator> deviceLocators)
      throws MobileHarnessException {
    ImmutableSet<String> deviceUuids =
        deviceLocators.stream().map(DeviceLocator::getDeviceUuid).collect(toImmutableSet());
    Optional<LabDeviceConfig> result = apiConfigFileProcessor.readConfigFile();
    return result
        .map(
            labDeviceConfig ->
                labDeviceConfig.getDeviceConfigList().stream()
                    .filter(config -> deviceUuids.contains(config.getUuid()))
                    .collect(toImmutableList()))
        .orElseGet(ImmutableList::of);
  }

  @Override
  protected void storeDeviceConfigs(List<DeviceLocatorConfigPair> deviceConfigs) {}

  @Override
  protected void onDeviceConfigUpdatedToLocal() {}
}
