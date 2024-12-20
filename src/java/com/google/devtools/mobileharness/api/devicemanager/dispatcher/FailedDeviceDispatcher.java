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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.api.model.lab.DeviceInfo;
import com.google.devtools.mobileharness.infra.controller.device.DeviceInfoManager;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

/** Dispatcher for FailedDevice type. */
public class FailedDeviceDispatcher extends CacheableDispatcher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * {@inheritDoc}
   *
   * @return a map whose value is {@link DispatchType#LIVE}, and whose key is the control_id of a
   *     device that matches the following condition: 'in detection_results, for this control_id,
   *     there are a [control_id, FAILED] item AND a [control_id, ANOTHER_TYPE] item where
   *     ANOTHER_TYPE is a detection result type not in {FAILED, MONITOR, NO_OP}'.
   * @param detectionResults {@link DetectionResults} from all the detectors in this iteration.
   * @param dispatchResults {@link DispatchResults} from all dispatchers that have executed before.
   */
  @Override
  public Map<String, DispatchType> dispatchLiveDevices(
      DetectionResults detectionResults, DispatchResults dispatchResults) {
    return detectionResults.getAll().asMap().entrySet().stream()
        .filter(
            entry ->
                entry.getValue().stream()
                    .map(DetectionResult::detectionType)
                    .anyMatch(DetectionType.FAILED::equals))
        .filter(
            entry ->
                entry.getValue().stream()
                    .map(DetectionResult::detectionType)
                    .anyMatch(
                        detectionType ->
                            detectionType != DetectionType.FAILED
                                && detectionType != DetectionType.MONITOR
                                && detectionType != DetectionType.NO_OP))
        .collect(Collectors.toMap(Entry::getKey, entry -> DispatchType.LIVE));
  }

  @Override
  public DeviceId generateDeviceId(String deviceControlId) {
    Optional<DeviceInfo> deviceInfo = DeviceInfoManager.getInstance().get(deviceControlId);
    if (deviceInfo.isPresent() && !deviceInfo.get().deviceId().uuid().isEmpty()) {
      // We should be able to reuse the DeviceInfo of this device's previous runner.
      return deviceInfo.get().deviceId();
    } else {
      logger.atWarning().log(
          "Can not find previous uuid for device: %s, using controlId for uuid!", deviceControlId);
      return DeviceId.of(deviceControlId, /* uuid= */ deviceControlId);
    }
  }
}
