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

import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.protobuf.Any;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.Optional;

/** The class to operate lab and device config. */
public interface ApiConfig {

  static ApiConfig getInstance() {
    return ApiConfigV5.getInstance();
  }

  /** Initializes. */
  void initialize(boolean isDefaultPublic, boolean isDefaultSynced, String hostName);

  /** Gets all the monitored device ids. */
  List<String> getMonitoredDeviceUuids();

  /** Gets all the monitored device ids. */
  List<String> getOverTcpDeviceControlIds();

  /** Gets the max failed consecutive test count for the given driver. */
  int getMaxConsecutiveFail(String deviceControlId);

  /** Gets the max consecutive test count for the device in the lab. */
  int getMaxConsecutiveTest(String deviceControlId);

  /** Gets the owners of the given device. */
  List<String> getOwners(String deviceControlId);

  /** Gets the executors of the given device. */
  List<String> getExecutors(String deviceControlId);

  /** Gets the customized config of the given device. */
  Optional<Any> getCustomizedConfig(String deviceControlId);

  /** Get the supported dimensions of the given device. */
  List<StrPair> getSupportedDimensions(String deviceControlId);

  /** Gets the required dimensions of the given device. */
  List<StrPair> getRequiredDimensions(String deviceControlId);

  /** Gets all over ssh devices info. */
  Map<String, List<String>> getOverSshDevice();

  /** Gets the wifi config for the given device. */
  WifiConfig getDefaultWifi(String deviceControlId);

  void setDeviceConfigs(Map<String, DeviceConfig> deviceConfigList);

  void setLabConfig(LabConfig labConfig) throws MobileHarnessException;

  Optional<DeviceConfig> getDeviceConfigToStore(String deviceControlId);

  LabConfig getLabConfigToStore();

  List<String> getTestbedUuidList();

  /** Gets the host properties. */
  HostProperties getHostProperties();

  List<String> getMiscDeviceUuids();

  /** Adds the observer to listen to config change. */
  void addObserver(Observer observer);

  /** Gets whether the device config is synced between local and remote. */
  boolean isDeviceConfigSynced(String deviceControlId);

  /** Sets the device config has been synced between local and remote. */
  void setDeviceConfigSynced(String deviceControlId);

  /**
   * Waits until the device config is synced between local and remote.
   *
   * @param deviceControlId the device control id
   * @param timeout the max wait timeout
   * @return whether the device config is synced between local and remote
   */
  boolean waitUntilDeviceConfigSynced(String deviceControlId, Duration timeout)
      throws InterruptedException;
}
