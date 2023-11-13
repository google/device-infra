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

import com.google.common.base.Strings;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Detector for generating {@link com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice}
 * which are virtual devices which don't map to any physical devices or emulators/simulators.
 *
 * <p>This is for integration test of Mobile Harness itself. Only for debug/test purpose. If
 * --no_op_device_num is set, all other devices will be disabled.
 */
public class NoOpDeviceDetector implements Detector {
  private static final Random RANDOM = new Random();

  private final String deviceIdPrefix;

  public NoOpDeviceDetector() {
    String supportedDeviceType = Flags.instance().noOpDeviceType.getNonNull();
    if (!Strings.isNullOrEmpty(supportedDeviceType)) {
      deviceIdPrefix = supportedDeviceType;
    } else {
      deviceIdPrefix = "NoOpDevice";
    }
  }

  @Override
  public boolean precondition() throws InterruptedException {
    return Flags.instance().noOpDeviceNum.getNonNull() > 0;
  }

  @Override
  public List<DetectionResult> detectDevices() throws MobileHarnessException, InterruptedException {
    List<DetectionResult> detectionResults = new ArrayList<>();
    for (int i = 0; i < Flags.instance().noOpDeviceNum.getNonNull(); i++) {
      detectionResults.add(
          DetectionResult.of(String.format("%s-%d", deviceIdPrefix, i), DetectionType.NO_OP));
    }
    return detectionResults;
  }
}
