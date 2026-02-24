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

import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Detector to detect AndroidDesktopExecutorDevices. The number of devices detected is based on the
 * value of the flag AndroidDesktopExecutorDevicesNum. The device IDs of each device is
 * "al_device_$i" where i is 1 to AndroidDesktopExecutorDevicesNum.
 */
public class AndroidDesktopExecutorDetector implements Detector {

  @Override
  public boolean precondition() {
    return Flags.instance().androidDesktopExecutorDevicesNum.getNonNull() > 0;
  }

  @Override
  public List<DetectionResult> detectDevices() {
    return IntStream.range(1, Flags.instance().androidDesktopExecutorDevicesNum.getNonNull() + 1)
        .mapToObj(i -> "al_device_" + i)
        .map(deviceId -> DetectionResult.of(deviceId, DetectionType.ANDROID_DESKTOP_EXECUTOR))
        .collect(toImmutableList());
  }
}
