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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.util.DeviceIdGenerator;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AndroidDesktopExecutorDeviceDispatcherTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private DeviceIdGenerator mockDeviceIdGenerator;

  private static final String DEVICE_CONTROL_ID = "35352252-3d64-49a3-b1a3-4a5a92a52d73";
  private static final DetectionResult DETECTION_RESULT =
      DetectionResult.of(DEVICE_CONTROL_ID, DetectionType.ANDROID_DESKTOP_EXECUTOR);
  private static final DetectionResults DETECTION_RESULTS =
      new DetectionResults(ImmutableList.of(DETECTION_RESULT));

  private AndroidDesktopExecutorDeviceDispatcher dispatcher;

  @Before
  public void setUp() {
    dispatcher = new AndroidDesktopExecutorDeviceDispatcher(mockDeviceIdGenerator);
  }

  @Test
  public void dispatchDevices() {
    assertThat(dispatcher.dispatchDevices(DETECTION_RESULTS, new DispatchResults()))
        .containsExactly(DEVICE_CONTROL_ID, DispatchType.LIVE);
  }

  @Test
  public void generateDeviceId() {
    DeviceId deviceId = DeviceId.of("fake_device_id", "fake_uuid", /* isUuidVolatile= */ true);
    when(mockDeviceIdGenerator.getNoOpDeviceId(DEVICE_CONTROL_ID)).thenReturn(deviceId);

    assertThat(dispatcher.generateDeviceId(DEVICE_CONTROL_ID)).isEqualTo(deviceId);
  }
}
