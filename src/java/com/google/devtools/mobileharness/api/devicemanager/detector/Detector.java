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

package com.google.devtools.mobileharness.api.devicemanager.detector;

import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Device detector for detecting active devices. */
public interface Detector {

  /**
   * Returns whether the current machine supports detecting/managing the device type. You may need
   * to check:
   *
   * <ul>
   *   <li>Whether the current host machine is able to control the device type. For example, iPhones
   *       can only be controlled with Mac, no Linux.
   *   <li>Whether all required command lines tools are deployed, such as Python command line tools,
   *       Android SDK, etc. If not, you can try to deploy them within this method.
   * </ul>
   *
   * If the current machine can't do it, return false so MH will just ignore those devices.
   */
  boolean precondition() throws InterruptedException;

  /**
   * Detects the current active devices, and return the list of {@link DetectionResult}s, MH DM will
   * combine them, then dispatch them to Dispatcher to generate the corresponding Device class.
   *
   * @throws MobileHarnessException if fails to detect devices
   */
  List<DetectionResult> detectDevices() throws MobileHarnessException, InterruptedException;

  /**
   * The detection interval of the detector, device manager will trigger the {@code detectDevices}
   * based on the interval, if detector doesn't specify it, will use the default global interval
   * instead.
   */
  default Optional<Duration> getDetectionInterval() {
    return Optional.empty();
  }
}
