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

import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Base class for dispatcher. */
public abstract class BaseDispatcher implements Dispatcher {
  /** The detection type need to dispatch from the detectionResults. */
  private final DetectionType detectionType;

  /** The detection detail need to dispatch from the detectionResults. */
  @Nullable private final Object detectionDetail;

  public BaseDispatcher(DetectionType detectionType) {
    this(detectionType, null /* detectionDetail */);
  }

  public BaseDispatcher(DetectionType detectionType, @Nullable Object detectionDetail) {
    this.detectionType = detectionType;
    this.detectionDetail = detectionDetail;
  }

  @Override
  public Map<String, DispatchType> dispatchDevices(
      DetectionResults detectionResults, DispatchResults dispatchResults) {
    return ((detectionDetail == null)
            ? detectionResults.getByType(detectionType)
            : detectionResults.getByTypeAndDetail(detectionType, detectionDetail))
        .stream()
            .collect(Collectors.toMap(DetectionResult::deviceControlId, v -> DispatchType.LIVE));
  }

  @Override
  public DeviceId generateDeviceId(String deviceControlId) {
    return DeviceId.of(deviceControlId, deviceControlId /* deviceUuid */);
  }
}
