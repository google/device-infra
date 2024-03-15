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

package com.google.devtools.mobileharness.infra.lab.rpc.service;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceStatusServ.GetDeviceStatusRequest;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceStatusServ.GetDeviceStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceStatusServ.MobileHarnessDeviceStatus;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The service logic class for MobileHarness FE to report device status. It is used to create RPC
 * service for Stubby and gRPC.
 */
public class DeviceStatusServiceImpl {

  /** Device manager for managing and controlling all devices. */
  private final LocalDeviceManager deviceManager;

  public DeviceStatusServiceImpl(LocalDeviceManager deviceManager) {
    this.deviceManager = deviceManager;
  }

  public GetDeviceStatusResponse getDeviceStatus(GetDeviceStatusRequest req)
      throws MobileHarnessException {
    GetDeviceStatusResponse.Builder responseBuilder = GetDeviceStatusResponse.newBuilder();

    try {
      Map<Device, DeviceStatusInfo> devicesToSync =
          deviceManager.getAllDeviceStatusWithoutDuplicatedUuid(/* realtimeDetect= */ true);
      for (Entry<Device, DeviceStatusInfo> entry : devicesToSync.entrySet()) {
        MobileHarnessDeviceStatus.Builder resultBuilder =
            MobileHarnessDeviceStatus.newBuilder()
                .setId(entry.getKey().getDeviceId())
                .setStatus(entry.getValue().getDeviceStatusWithTimestamp().getStatus())
                .setTimestampMs(entry.getValue().getDeviceStatusWithTimestamp().getTimestampMs());
        responseBuilder.addDeviceStatus(resultBuilder);
      }
    } catch (InterruptedException e) {
      throw new MobileHarnessException(
          BasicErrorId.COMMAND_EXEC_FAIL, "Failed to get device status ", e);
    }

    return responseBuilder.build();
  }
}
