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
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Detector for generating {@link com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice}
 * which are virtual devices which don't map to any physical devices or emulators/simulators.
 *
 * <p>This is for integration test of Mobile Harness itself. Only for debug/test purpose. If
 * --no_op_device_num is set, all other devices will be disabled.
 */
public class NoOpDeviceDetector implements Detector {

  private static final Random RANDOM = new Random();

  /** Set in {@link #precondition()}. */
  private volatile ImmutableList<DetectionResult> detectionResults;

  @Override
  public boolean precondition() throws InterruptedException {
    detectionResults = createDetectionResults();
    return !detectionResults.isEmpty();
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  @Override
  public List<DetectionResult> detectDevices() throws MobileHarnessException, InterruptedException {
    if (!Flags.instance().noOpDeviceRandomOffline.getNonNull() || RANDOM.nextInt(100) >= 1) {
      return detectionResults;
    }

    // With a 1% change, makes one device be offline.
    int deviceIndexToTakeOffline = RANDOM.nextInt(detectionResults.size());
    List<DetectionResult> newDetectionResults = new ArrayList<>(detectionResults.size() - 1);
    for (int i = 0; i < detectionResults.size(); i++) {
      if (i != deviceIndexToTakeOffline) {
        newDetectionResults.add(detectionResults.get(i));
      }
    }
    return newDetectionResults;
  }

  private static ImmutableList<DetectionResult> createDetectionResults() {
    String noOpDeviceType = Flags.instance().noOpDeviceType.getNonNull();
    String deviceIdPrefix = noOpDeviceType.isEmpty() ? "NoOpDevice" : noOpDeviceType;

    return IntStream.range(0, Flags.instance().noOpDeviceNum.getNonNull())
        .mapToObj(
            offset ->
                DetectionResult.of(
                    String.format(
                        "%s-%d",
                        deviceIdPrefix,
                        Flags.instance().noOpDeviceStartIndex.getNonNull() + offset),
                    DetectionType.NO_OP))
        .collect(toImmutableList());
  }
}
