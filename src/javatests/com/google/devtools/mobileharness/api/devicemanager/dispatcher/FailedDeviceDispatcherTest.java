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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FailedDeviceDispatcherTest {

  private static final String DEVICE_CONTROL_ID_0 = "device_id_0";
  private static final String DEVICE_CONTROL_ID_1 = "device_id_1";
  private static final DetectionResult FAILED_DEVICE_0 =
      DetectionResult.of(DEVICE_CONTROL_ID_0, DetectionType.FAILED);
  private static final DetectionResult FAILED_DEVICE_1 =
      DetectionResult.of(DEVICE_CONTROL_ID_1, DetectionType.FAILED);

  private FailedDeviceDispatcher dispatcher;

  @Before
  public void setUp() {
    dispatcher = new FailedDeviceDispatcher();
  }

  @Test
  public void noFailedDeviceDispatchWithFailedDetectionAlone() {
    assertThat(
            dispatcher.dispatchLiveDevices(
                new DetectionResults(ImmutableList.of(FAILED_DEVICE_0)), new DispatchResults()))
        .isEmpty();
  }

  @Test
  public void dispatchDevice() {
    DetectionResult linuxDetection = DetectionResult.of(DEVICE_CONTROL_ID_1, DetectionType.LINUX);
    DetectionResults detectionResults =
        new DetectionResults(ImmutableList.of(linuxDetection, FAILED_DEVICE_1, FAILED_DEVICE_0));
    assertThat(dispatcher.dispatchLiveDevices(detectionResults, new DispatchResults()))
        .doesNotContainKey(DEVICE_CONTROL_ID_0);
    assertThat(dispatcher.dispatchLiveDevices(detectionResults, new DispatchResults()))
        .containsAtLeast(DEVICE_CONTROL_ID_1, DispatchType.LIVE);
  }

  @Test
  public void noConflictWithNoOpDevice() {
    DetectionResults detectionResults =
        new DetectionResults(
            ImmutableList.of(
                DetectionResult.of(DEVICE_CONTROL_ID_0, DetectionType.NO_OP),
                FAILED_DEVICE_1,
                FAILED_DEVICE_0));
    assertThat(dispatcher.dispatchLiveDevices(detectionResults, new DispatchResults())).isEmpty();
  }

  @Test
  public void noConflictWithDisconnectedDevice() {
    DetectionResults detectionResults =
        new DetectionResults(
            ImmutableList.of(
                DetectionResult.of(DEVICE_CONTROL_ID_0, DetectionType.MONITOR),
                FAILED_DEVICE_1,
                FAILED_DEVICE_0));
    assertThat(dispatcher.dispatchLiveDevices(detectionResults, new DispatchResults())).isEmpty();
  }

  @Test
  public void generateUuid() {
    assertThat(dispatcher.generateDeviceId(DEVICE_CONTROL_ID_0))
        .isEqualTo(DeviceId.of(DEVICE_CONTROL_ID_0, DEVICE_CONTROL_ID_0));
  }
}
