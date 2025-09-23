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

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootDeviceState;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import java.util.List;
import java.util.Map;

/** Detector for fastboot. */
public class FastbootDetector implements Detector {

  private final Fastboot fastboot;

  public FastbootDetector() {
    this(new Fastboot());
  }

  @VisibleForTesting
  FastbootDetector(Fastboot fastboot) {
    this.fastboot = fastboot;
  }

  @Override
  public boolean precondition() throws InterruptedException {
    return !(new SystemUtil().isOnMac());
  }

  /**
   * Detects the fastboot devices.
   *
   * @return Lists of {@link DetectionResult} of the current active fastboot devices.
   * @throws MobileHarnessException if fails to detect devices
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  @Override
  public List<DetectionResult> detectDevices() throws MobileHarnessException, InterruptedException {
    try {
      Map<String, FastbootDeviceState> ids = fastboot.getDeviceSerialsAndDetail();
      return ids.entrySet().stream()
          .map(
              entry -> DetectionResult.of(entry.getKey(), DetectionType.FASTBOOT, entry.getValue()))
          .collect(toImmutableList());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DM_DETECTOR_FASTBOOT_ERROR,
          "Fastboot detector failed to detect devices.",
          e);
    }
  }
}
