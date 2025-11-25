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

import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.util.DeviceIdGenerator;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import java.util.Map;

/** Dispatcher for Android Desktop Executor device. */
public class AndroidDesktopExecutorDeviceDispatcher implements Dispatcher {

  private final DeviceIdGenerator deviceIdGenerator;

  public AndroidDesktopExecutorDeviceDispatcher() {
    this(new DeviceIdGenerator());
  }

  public AndroidDesktopExecutorDeviceDispatcher(DeviceIdGenerator deviceIdGenerator) {
    this.deviceIdGenerator = deviceIdGenerator;
  }

  @Override
  public Map<String, DispatchType> dispatchDevices(
      DetectionResults detectionResults, DispatchResults dispatchResults) {
    return detectionResults.getByType(DetectionType.ANDROID_DESKTOP_EXECUTOR).stream()
        .collect(toImmutableMap(DetectionResult::deviceControlId, unused -> DispatchType.LIVE));
  }

  @Override
  public DeviceId generateDeviceId(String deviceControlId) {
    return deviceIdGenerator.getNoOpDeviceId(deviceControlId);
  }
}
