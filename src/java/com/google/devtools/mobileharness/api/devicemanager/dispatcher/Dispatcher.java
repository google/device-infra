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

import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import java.util.Map;
import java.util.Optional;

/** Device dispatcher for dispatching devices. */
public interface Dispatcher {

  /**
   * Checks whether this dispatcher can work in the current environment.
   *
   * @return empty if the dispatcher can work, or return the detailed incompatible reason
   */
  default Optional<String> precondition() {
    return Optional.empty();
  }

  /**
   * Dispatches the device base on the {@link DetectionResults} detector detected and the {@link
   * DispatchResults} previous dispatcher generated, returns the dispatch results.
   */
  Map<String, DispatchType> dispatchDevices(
      DetectionResults detectionResults, DispatchResults dispatchResults)
      throws MobileHarnessException, InterruptedException;

  /** Generates the device id for a device. */
  DeviceId generateDeviceId(String deviceControlId) throws MobileHarnessException;
}
