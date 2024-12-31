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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.AndroidEmulatorType;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import javax.annotation.Nullable;

/** Detector for Android JIT emulators. */
public final class AndroidJitEmulatorDetector implements Detector {
  /** Initialized in {@link #precondition()}. */
  @Nullable private volatile ImmutableList<String> emulatorIds;

  // Acloud creates cuttlefish emulator with port 6520 + index, where index is instance id minus
  // one. Example: "acloud create --local-instance 1" will create a local emulator with port 6520.
  // And instance 2 will be 6521, so on and so forth.
  private static final int EMULATOR_BASE_PORT = 6520;

  public AndroidJitEmulatorDetector() {}

  /**
   * Precondition of the detector.
   *
   * @return whether the precondition meets
   */
  @Override
  public boolean precondition() {
    int emulatorNumber = Flags.instance().androidJitEmulatorNum.getNonNull();
    ImmutableList.Builder<String> emulatorIdsBuilder = ImmutableList.builder();
    for (int i = 0; i < emulatorNumber; i++) {
      emulatorIdsBuilder.add("0.0.0.0:" + (i + EMULATOR_BASE_PORT));
    }
    emulatorIds = emulatorIdsBuilder.build();
    return emulatorNumber > 0;
  }

  /**
   * Detects the Android JIT emulators.
   *
   * @return the detection result of the Android JIT emulators
   */
  @Override
  public ImmutableList<DetectionResult> detectDevices() {
    return emulatorIds.stream()
        .map(id -> DetectionResult.of(id, DetectionType.EMULATOR, AndroidEmulatorType.JIT_EMULATOR))
        .collect(toImmutableList());
  }
}
