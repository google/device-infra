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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocatorConfigPair;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
import java.util.List;
import java.util.Optional;

/** The device config manager based on Device Config service. */
public class ConfigServiceBasedDeviceConfigManager extends DeviceConfigManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final DeviceConfigStub deviceConfigStub;

  public ConfigServiceBasedDeviceConfigManager(
      LocalDeviceManager localDeviceManager,
      DeviceIdManager deviceIdManager,
      ApiConfig apiConfig,
      String hostName,
      DeviceConfigStub deviceConfigStub) {
    super(localDeviceManager, deviceIdManager, apiConfig, hostName);
    this.deviceConfigStub = deviceConfigStub;
  }

  @Override
  protected Optional<LabConfig> loadLabConfig(String hostName) throws MobileHarnessException {
    logger.atInfo().log("Load lab config from config service.");
    GetLabConfigResponse labConfigResponse;
    try {
      labConfigResponse =
          deviceConfigStub.getLabConfig(
              GetLabConfigRequest.newBuilder().setLabHost(hostName).build());
    } catch (RpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_GET_CONFIG_FROM_CONFIG_SERVER_ERROR,
          String.format("Failed to get lab config for host %s.", hostName),
          e);
    }
    return labConfigResponse.hasLabConfig()
        ? Optional.of(labConfigResponse.getLabConfig())
        : Optional.empty();
  }

  @Override
  protected void storeLabConfig(LabConfig labConfig) throws MobileHarnessException {}

  @Override
  protected List<DeviceConfig> loadDeviceConfigs(List<DeviceLocator> deviceLocators)
      throws MobileHarnessException {
    GetDeviceConfigsRequest deviceConfigsRequest =
        GetDeviceConfigsRequest.newBuilder().addAllDeviceLocator(deviceLocators).build();
    logger.atInfo().log("GetDeviceConfigs: %s", deviceConfigsRequest);
    try {
      return deviceConfigStub.getDeviceConfigs(deviceConfigsRequest).getDeviceConfigList();
    } catch (RpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_GET_CONFIG_FROM_CONFIG_SERVER_ERROR,
          String.format("Failed to get device config for devices: %s", deviceConfigsRequest),
          e);
    }
  }

  @Override
  protected void storeDeviceConfigs(List<DeviceLocatorConfigPair> deviceConfigs)
      throws MobileHarnessException {}

  @Override
  protected void onDeviceConfigUpdatedToLocal()
      throws MobileHarnessException, InterruptedException {}
}
