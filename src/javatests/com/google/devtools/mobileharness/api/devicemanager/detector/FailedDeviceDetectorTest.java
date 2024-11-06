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

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.faileddevice.FailedDeviceTable;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FailedDeviceDetectorTest {

  private static final String DEVICE_0 = "DEVICE_0";
  private static final String DEVICE_1 = "DEVICE_1";

  private FailedDeviceDetector detector;
  private final FailedDeviceTable table = FailedDeviceTable.getInstance();

  @Before
  public void setUp() {
    detector = new FailedDeviceDetector();
    table.remove(DEVICE_0);
    table.remove(DEVICE_1);
  }

  @Test
  public void precondition_success() throws Exception {
    assertThat(detector.precondition()).isTrue();
  }

  @Test
  public void detectDeviceInTable() throws MobileHarnessException, InterruptedException {
    ensureDeviceAdded(DEVICE_0);
    ensureDeviceAdded(DEVICE_1);
    assertThat(detector.detectDevices())
        .containsExactly(
            DetectionResult.of(DEVICE_0, DetectionType.FAILED),
            DetectionResult.of(DEVICE_1, DetectionType.FAILED));
  }

  @Test
  public void emptyWithoutAdding() throws MobileHarnessException, InterruptedException {
    assertThat(detector.detectDevices()).isEmpty();
  }

  @Test
  public void notFailedWhenAddedOnce() throws MobileHarnessException, InterruptedException {
    table.add(DEVICE_0);
    table.add(DEVICE_1);
    assertThat(detector.detectDevices()).isEmpty();
  }

  @Test
  public void notDetectableAfterRemoval() throws MobileHarnessException, InterruptedException {
    ensureDeviceAdded(DEVICE_0);
    ensureDeviceAdded(DEVICE_1);
    table.remove(DEVICE_0);
    assertThat(detector.detectDevices())
        .containsExactly(DetectionResult.of(DEVICE_1, DetectionType.FAILED));
  }

  private void ensureDeviceAdded(String id) {
    for (int i = 0; i < Flags.instance().maxInitFailuresBeforeFail.getNonNull(); i++) {
      table.add(id);
    }
  }
}
