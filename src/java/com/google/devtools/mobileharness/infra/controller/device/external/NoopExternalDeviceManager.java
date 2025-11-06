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

package com.google.devtools.mobileharness.infra.controller.device.external;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager.DeviceReservation;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A dummy external device manager which can be used when there's no another device manager on the
 * lab host besides MH lab server.
 */
public class NoopExternalDeviceManager implements ExternalDeviceManager {

  @Override
  public DeviceReservation reserveDevice(
      String deviceId, String deviceClass, Set<String> deviceTypes, Duration timeout) {
    return new DeviceReservation() {
      @Override
      public void close() {}

      @Override
      public String getReservationId() {
        return "";
      }

      @Override
      public void markRunningTest() {}
    };
  }

  @Override
  public DeviceStatus getDeviceStatus(
      String deviceId, String deviceClass, Set<String> deviceTypes) {
    return DeviceStatus.IDLE;
  }

  @Override
  public Map<Dimension.Name, Optional<String>> getExtraDimensions(String deviceId) {
    return ImmutableMap.of();
  }

  @Override
  public boolean isManagingDeviceLifeCycle() {
    return false;
  }

  @Override
  public boolean isManagingDeviceRecovery() {
    return false;
  }
}
