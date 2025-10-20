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

import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocatorConfigPair;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsResponse;
import java.util.HashMap;
import java.util.Map;

/** Implementation of the DeviceConfigService. */
public final class DeviceConfigServiceImpl {
  private final Map<String, DeviceConfig> deviceConfigMap = new HashMap<>();

  public DeviceConfigServiceImpl() {}

  public GetDeviceConfigsResponse getDeviceConfigs(GetDeviceConfigsRequest request) {
    GetDeviceConfigsResponse.Builder responseBuilder = GetDeviceConfigsResponse.newBuilder();
    for (DeviceLocator deviceLocator : request.getDeviceLocatorList()) {
      String deviceUuid = deviceLocator.getDeviceUuid();
      responseBuilder.addDeviceConfig(deviceConfigMap.get(deviceUuid));
    }
    return responseBuilder.build();
  }

  public UpdateDeviceConfigsResponse updateDeviceConfigs(UpdateDeviceConfigsRequest request) {
    for (DeviceLocatorConfigPair deviceLocatorConfigPair : request.getDeviceLocatorConfigList()) {
      String deviceUuid = deviceLocatorConfigPair.getDeviceLocator().getDeviceUuid();
      DeviceConfig deviceConfig = deviceLocatorConfigPair.getDeviceConfig();
      deviceConfigMap.put(deviceUuid, deviceConfig);
    }
    return UpdateDeviceConfigsResponse.getDefaultInstance();
  }
}
