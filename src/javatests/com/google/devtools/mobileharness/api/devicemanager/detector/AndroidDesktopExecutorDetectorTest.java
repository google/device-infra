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

import static com.google.common.flags.Flags.disableStateCheckingForTest;
import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AndroidDesktopExecutorDetectorTest {
  @Test
  public void detectDevices_returnsExpectedDevices() {
    disableStateCheckingForTest();
    Flags.instance().androidDesktopExecutorDevicesNum.setForTest(3);
    AndroidDesktopExecutorDetector detector = new AndroidDesktopExecutorDetector();

    List<DetectionResult> detectionResults = detector.detectDevices();

    assertThat(detectionResults).hasSize(3);
    assertThat(detectionResults.get(0).deviceControlId()).isEqualTo("al_device_1");
    assertThat(detectionResults.get(1).deviceControlId()).isEqualTo("al_device_2");
    assertThat(detectionResults.get(2).deviceControlId()).isEqualTo("al_device_3");
  }
}
