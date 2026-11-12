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
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteGlobalConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteGlobalConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetGlobalConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetGlobalConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateGlobalConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateGlobalConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.GlobalConfig;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.service.deviceconfig.storage.StorageClient;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Implementation of the DeviceConfigService. */
@Singleton
public final class DeviceConfigServiceImpl {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final StorageClient storageClient;
  private final Object globalConfigLock = new Object();

  @Inject
  DeviceConfigServiceImpl(StorageClient storageClient) {
    this.storageClient = storageClient;
  }

  public GetDeviceConfigsResponse getDeviceConfigs(GetDeviceConfigsRequest request)
      throws MobileHarnessException {
    GetDeviceConfigsResponse.Builder responseBuilder = GetDeviceConfigsResponse.newBuilder();
    for (DeviceLocator deviceLocator : request.getDeviceLocatorList()) {
      storageClient
          .getDeviceConfig(deviceLocator.getDeviceUuid())
          .ifPresent(responseBuilder::addDeviceConfig);
    }

    return responseBuilder.build();
  }

  public UpdateDeviceConfigsResponse updateDeviceConfigs(UpdateDeviceConfigsRequest request) {
    UpdateDeviceConfigsResponse.Builder responseBuilder = UpdateDeviceConfigsResponse.newBuilder();
    for (DeviceLocatorConfigPair deviceLocatorConfigPair : request.getDeviceLocatorConfigList()) {
      String deviceUuid = deviceLocatorConfigPair.getDeviceLocator().getDeviceUuid();
      if (deviceUuid.isEmpty()) {
        continue;
      }

      DeviceConfig deviceConfig = deviceLocatorConfigPair.getDeviceConfig();
      if (deviceConfig.getUuid().isEmpty()) {
        deviceConfig = deviceConfig.toBuilder().setUuid(deviceUuid).build();
      } else if (!deviceConfig.getUuid().equals(deviceUuid)) {
        String errorMessage =
            String.format(
                "DeviceLocator UUID %s does not match DeviceConfig UUID %s",
                deviceUuid, deviceConfig.getUuid());
        logger.atWarning().log("%s", errorMessage);
        responseBuilder.putErrorMessage(deviceUuid, errorMessage);
        continue;
      }
      try {
        storageClient.upsertDeviceConfig(deviceConfig);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to update device config for device %s", deviceUuid);
        responseBuilder.putErrorMessage(
            deviceUuid, e.getMessage() != null ? e.getMessage() : e.getErrorId().name());
      }
    }
    return responseBuilder.build();
  }

  public DeleteDeviceConfigsResponse deleteDeviceConfigs(DeleteDeviceConfigsRequest request) {
    DeleteDeviceConfigsResponse.Builder responseBuilder = DeleteDeviceConfigsResponse.newBuilder();
    for (String deviceUuid : request.getDeviceUuidList()) {
      try {
        storageClient.deleteDeviceConfig(deviceUuid);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to delete device config for device %s", deviceUuid);
        responseBuilder.putErrorMessage(
            deviceUuid, e.getMessage() != null ? e.getMessage() : e.getErrorId().name());
      }
    }
    return responseBuilder.build();
  }

  public GetLabConfigResponse getLabConfig(GetLabConfigRequest request)
      throws MobileHarnessException {
    return storageClient
        .getLabConfig(request.getLabHost())
        .map(config -> GetLabConfigResponse.newBuilder().setLabConfig(config).build())
        .orElse(GetLabConfigResponse.getDefaultInstance());
  }

  public UpdateLabConfigResponse updateLabConfig(UpdateLabConfigRequest request)
      throws MobileHarnessException {
    storageClient.upsertLabConfig(request.getLabConfig());
    return UpdateLabConfigResponse.getDefaultInstance();
  }

  public DeleteLabConfigResponse deleteLabConfig(DeleteLabConfigRequest request)
      throws MobileHarnessException {
    storageClient.deleteLabConfig(request.getLabHost());
    return DeleteLabConfigResponse.getDefaultInstance();
  }

  public GetGlobalConfigResponse getGlobalConfig(GetGlobalConfigRequest request)
      throws MobileHarnessException {
    synchronized (globalConfigLock) {
      return storageClient
          .getGlobalConfig()
          .map(config -> GetGlobalConfigResponse.newBuilder().setGlobalConfig(config).build())
          .orElse(GetGlobalConfigResponse.getDefaultInstance());
    }
  }

  public UpdateGlobalConfigResponse updateGlobalConfig(UpdateGlobalConfigRequest request)
      throws MobileHarnessException {
    GlobalConfig globalConfig = request.getGlobalConfig();
    FieldMask updateMask = request.getUpdateMask();
    boolean hasNonEmptyMask = request.hasUpdateMask() && !updateMask.getPathsList().isEmpty();
    if (hasNonEmptyMask && !FieldMaskUtil.isValid(GlobalConfig.class, updateMask)) {
      throw new MobileHarnessException(
          InfraErrorId.GLOBAL_CONFIG_ILLEGAL,
          String.format("Invalid update_mask for GlobalConfig: %s", updateMask));
    }
    synchronized (globalConfigLock) {
      if (hasNonEmptyMask) {
        GlobalConfig.Builder mergedConfig =
            storageClient.getGlobalConfig().orElse(GlobalConfig.getDefaultInstance()).toBuilder();
        FieldMaskUtil.merge(
            updateMask,
            globalConfig,
            mergedConfig,
            new FieldMaskUtil.MergeOptions()
                .setReplaceRepeatedFields(true)
                .setReplaceMessageFields(true)
                .setReplacePrimitiveFields(true));
        globalConfig = mergedConfig.build();
      }
      storageClient.upsertGlobalConfig(globalConfig);
    }
    return UpdateGlobalConfigResponse.getDefaultInstance();
  }

  public DeleteGlobalConfigResponse deleteGlobalConfig(DeleteGlobalConfigRequest request)
      throws MobileHarnessException {
    synchronized (globalConfigLock) {
      storageClient.deleteGlobalConfig();
    }
    return DeleteGlobalConfigResponse.getDefaultInstance();
  }
}
