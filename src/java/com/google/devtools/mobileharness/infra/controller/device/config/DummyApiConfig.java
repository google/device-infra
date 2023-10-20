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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.protobuf.Any;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;

/** The dummy ApiConfig which won't do anything */
public class DummyApiConfig extends Observable implements ApiConfig {
  private static final int DEFAULT_MAX_CONSECUTIVE_FAILS = 5;
  private static final int DEFAULT_MAX_CONSECUTIVE_TESTS = 10000;

  /** Singleton holder for lazy initialization. */
  private static class SingletonHolder {

    /** The singleton instance of {@link DummyApiConfig}. */
    private static final DummyApiConfig singleton;

    static {
      singleton = new DummyApiConfig();
    }
  }

  public static DummyApiConfig getInstance() {
    return SingletonHolder.singleton;
  }

  DummyApiConfig() {}

  @Override
  public void init(boolean isDefaultPublic, String hostName) {}

  @Override
  public int getMaxConsecutiveFail(String deviceControlId) {
    return DEFAULT_MAX_CONSECUTIVE_FAILS;
  }

  @Override
  public int getMaxConsecutiveTest(String deviceControlId) {
    return DEFAULT_MAX_CONSECUTIVE_TESTS;
  }

  @Override
  public List<String> getOwners(String deviceControlId) {
    return ImmutableList.of();
  }

  @Override
  public List<String> getExecutors(String deviceControlId) {
    return ImmutableList.of();
  }

  @Override
  public Optional<Any> getCustomizedConfig(String deviceControlId) {
    return Optional.empty();
  }

  @Override
  public List<StrPair> getSupportedDimensions(String deviceControlId) {
    return ImmutableList.of();
  }

  @Override
  public List<StrPair> getRequiredDimensions(String deviceControlId) {
    return ImmutableList.of();
  }

  @Override
  public Basic.WifiConfig getDefaultWifi(String deviceControlId) {
    return Basic.WifiConfig.getDefaultInstance();
  }

  @Override
  public void setDeviceConfigs(Map<String, DeviceConfig> deviceConfigList) {}

  @Override
  public void setLabConfig(LabConfig labConfig) throws MobileHarnessException {}

  @Override
  public Optional<DeviceConfig> getDeviceConfigToStore(String deviceControlId) {
    return Optional.empty();
  }

  @Override
  public LabConfig getLabConfigToStore() {
    return LabConfig.getDefaultInstance();
  }

  @Override
  public List<String> getTestbedUuidList() {
    return ImmutableList.of();
  }

  @Override
  public List<String> getMonitoredDeviceUuids() {
    return ImmutableList.of();
  }

  @Override
  public List<String> getOverTcpDeviceControlIds() {
    return ImmutableList.of();
  }

  @Override
  public Map<String, List<String>> getOverSshDevice() {
    return ImmutableMap.of();
  }

  @Override
  public HostProperties getHostProperties() {
    return HostProperties.getDefaultInstance();
  }
}
