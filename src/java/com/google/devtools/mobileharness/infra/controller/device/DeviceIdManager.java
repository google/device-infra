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

package com.google.devtools.mobileharness.infra.controller.device;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Device id manager for mapping Mobile Harness device control id / uuid -> {@link DeviceId}. */
public class DeviceIdManager {
  private static final DeviceIdManager INSTANCE = new DeviceIdManager();

  /** Returns the singleton */
  public static DeviceIdManager getInstance() {
    return INSTANCE;
  }

  private final Map<String, DeviceId> controlIdToDeviceId = new ConcurrentHashMap<>();

  private final Map<String, DeviceId> uuidToDeviceId = new ConcurrentHashMap<>();

  /** Gets the device uuid from device control id. */
  public Optional<DeviceId> getDeviceIdFromControlId(String deviceControlId) {
    return Optional.ofNullable(controlIdToDeviceId.get(deviceControlId));
  }

  /** Gets the device control id from device uuid. */
  public Optional<DeviceId> getDeviceIdFromUuid(String deviceUuid) {
    return Optional.ofNullable(uuidToDeviceId.get(deviceUuid));
  }

  public boolean containsControlId(String deviceControlId) {
    return controlIdToDeviceId.containsKey(deviceControlId);
  }

  public boolean containsUuid(String deviceUuid) {
    return uuidToDeviceId.containsKey(deviceUuid);
  }

  public ImmutableMap<String, DeviceId> getUuidToDeviceIdMap() {
    return ImmutableMap.copyOf(uuidToDeviceId);
  }

  /**
   * Do <b>not</b> make it public. The interface will be called when the device becomes detectable,
   * and we can assume that there will be no meaningful queries about the device until the add
   * function is complete, so we don't need to consider the thread safe here.
   */
  void add(DeviceId deviceId) {
    controlIdToDeviceId.put(deviceId.controlId(), deviceId);
    uuidToDeviceId.put(deviceId.uuid(), deviceId);
  }

  /** Do <b>not</b> make it public. */
  @VisibleForTesting
  void clearAll() {
    controlIdToDeviceId.clear();
    uuidToDeviceId.clear();
  }
}
