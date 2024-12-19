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
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Detector for Android JIT emulators. */
public final class AndroidJitEmulatorDetector implements Detector {
  private final List<String> emulatorIds;

  public AndroidJitEmulatorDetector() {
    emulatorIds = new ArrayList<>();
  }

  /**
   * Precondition of the detector.
   *
   * @return whether the precondition meets
   */
  @Override
  public boolean precondition() {
    int emulatorNumber = Flags.instance().androidJitEmulatorNum.getNonNull();
    for (int i = 0; i < emulatorNumber; i++) {
      emulatorIds.add("0.0.0.0:" + (i + 6520));
    }
    return Flags.instance().androidJitEmulatorNum.getNonNull() > 0;
  }

  /**
   * Detects the Android JIT emulators.
   *
   * @return the detection result of the Android JIT emulators
   */
  @Override
  public List<DetectionResult> detectDevices() {
    return emulatorIds.stream()
        .map(id -> DetectionResult.of(id, DetectionType.JIT_EMULATOR))
        .collect(Collectors.toList());
  }
}
