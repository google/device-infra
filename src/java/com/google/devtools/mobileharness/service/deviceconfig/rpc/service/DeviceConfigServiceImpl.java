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

package com.google.devtools.mobileharness.service.deviceconfig.rpc.service;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocatorConfigPair;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.service.deviceconfig.storage.StorageClient;
import java.util.Optional;
import javax.inject.Inject;

/** Implementation of the DeviceConfigService. */
public final class DeviceConfigServiceImpl {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final StorageClient storageClient;

  @Inject
  DeviceConfigServiceImpl(StorageClient storageClient) {
    this.storageClient = storageClient;
  }

  public GetDeviceConfigsResponse getDeviceConfigs(GetDeviceConfigsRequest request) {
    GetDeviceConfigsResponse.Builder responseBuilder = GetDeviceConfigsResponse.newBuilder();
    for (DeviceLocator deviceLocator : request.getDeviceLocatorList()) {
      String deviceUuid = deviceLocator.getDeviceUuid();

      try {
        Optional<DeviceConfig> deviceConfig = storageClient.getDeviceConfig(deviceUuid);
        if (deviceConfig.isPresent()) {
          responseBuilder.addDeviceConfig(deviceConfig.get());
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to get device config for device %s", deviceUuid);
      }
    }

    return responseBuilder.build();
  }

  public UpdateDeviceConfigsResponse updateDeviceConfigs(UpdateDeviceConfigsRequest request) {
    for (DeviceLocatorConfigPair deviceLocatorConfigPair : request.getDeviceLocatorConfigList()) {
      String deviceUuid = deviceLocatorConfigPair.getDeviceLocator().getDeviceUuid();
      if (deviceUuid.isEmpty()) {
        continue;
      }

      DeviceConfig deviceConfig = deviceLocatorConfigPair.getDeviceConfig();
      try {
        storageClient.upsertDeviceConfig(deviceConfig);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to update device config for device %s", deviceUuid);
      }
    }
    return UpdateDeviceConfigsResponse.getDefaultInstance();
  }

  public DeleteDeviceConfigsResponse deleteDeviceConfigs(DeleteDeviceConfigsRequest request) {
    for (String deviceUuid : request.getDeviceUuidList()) {
      try {
        storageClient.deleteDeviceConfig(deviceUuid);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to delete device config for device %s", deviceUuid);
      }
    }
    return DeleteDeviceConfigsResponse.getDefaultInstance();
  }

  public GetLabConfigResponse getLabConfig(GetLabConfigRequest request) {
    String hostName = request.getLabHost();
    try {
      Optional<LabConfig> labConfig = storageClient.getLabConfig(hostName);
      if (labConfig.isPresent()) {
        return GetLabConfigResponse.newBuilder().setLabConfig(labConfig.get()).build();
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to get lab config for host %s", hostName);
    }
    return GetLabConfigResponse.getDefaultInstance();
  }

  public UpdateLabConfigResponse updateLabConfig(UpdateLabConfigRequest request) {
    String hostName = request.getLabConfig().getHostName();
    LabConfig labConfig = request.getLabConfig();

    try {
      storageClient.upsertLabConfig(labConfig);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to update lab config for host %s", hostName);
    }

    return UpdateLabConfigResponse.getDefaultInstance();
  }

  public DeleteLabConfigResponse deleteLabConfig(DeleteLabConfigRequest request) {
    String hostName = request.getLabHost();
    try {
      storageClient.deleteLabConfig(hostName);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to delete lab config for host %s", hostName);
    }
    return DeleteLabConfigResponse.getDefaultInstance();
  }
}
