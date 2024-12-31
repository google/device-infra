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

package com.google.devtools.mobileharness.api.devicemanager.dispatcher;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.AndroidEmulatorType;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.util.DeviceIdGenerator;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;

/** Dispatcher for Android JIT Emulator devices. */
public final class AndroidJitEmulatorDispatcher implements Dispatcher {
  private final DeviceIdGenerator deviceIdGenerator;

  public AndroidJitEmulatorDispatcher() {
    this(new DeviceIdGenerator());
  }

  @VisibleForTesting
  AndroidJitEmulatorDispatcher(DeviceIdGenerator deviceIdGenerator) {
    this.deviceIdGenerator = deviceIdGenerator;
  }

  @Override
  public ImmutableMap<String, DispatchType> dispatchDevices(
      DetectionResults detectionResults, DispatchResults dispatchResults) {
    return detectionResults
        .getByTypeAndDetail(DetectionType.EMULATOR, AndroidEmulatorType.JIT_EMULATOR)
        .stream()
        .collect(toImmutableMap(DetectionResult::deviceControlId, s -> DispatchType.LIVE));
  }

  @Override
  public DeviceId generateDeviceId(String deviceControlId) {
    return deviceIdGenerator.getAndroidDeviceId(deviceControlId);
  }
}
