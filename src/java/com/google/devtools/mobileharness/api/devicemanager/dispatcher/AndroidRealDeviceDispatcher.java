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

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootDeviceState;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.util.DeviceIdGenerator;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Dispatcher for Android real devices. */
public class AndroidRealDeviceDispatcher extends CacheableDispatcher {
  private final DeviceIdGenerator deviceIdGenerator;

  public AndroidRealDeviceDispatcher() {
    this(new DeviceIdGenerator());
  }

  @VisibleForTesting
  AndroidRealDeviceDispatcher(DeviceIdGenerator deviceIdGenerator) {
    this.deviceIdGenerator = deviceIdGenerator;
  }

  @Override
  public Map<String, DispatchType> dispatchLiveDevices(
      DetectionResults detectionResults, DispatchResults dispatchResults) {
    Collection<DetectionResult> resultList =
        detectionResults.getByTypeAndDetail(DetectionType.ADB, DeviceState.DEVICE);
    if (!DeviceUtil.inSharedLab()) {
      resultList.addAll(
          detectionResults.getByTypeAndDetail(DetectionType.ADB, DeviceState.RECOVERY));
    }
    resultList.addAll(
        detectionResults.getByTypeAndDetail(DetectionType.FASTBOOT, FastbootDeviceState.FASTBOOT));
    return resultList.stream()
        .collect(Collectors.toMap(DetectionResult::deviceControlId, v -> DispatchType.LIVE));
  }

  @Override
  public DeviceId generateDeviceId(String deviceControlId) {
    return deviceIdGenerator.getAndroidDeviceId(deviceControlId);
  }
}
