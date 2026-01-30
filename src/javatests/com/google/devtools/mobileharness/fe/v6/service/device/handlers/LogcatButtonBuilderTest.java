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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LogcatButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private FeatureManager featureManager;

  private LogcatButtonBuilder logcatButtonBuilder;

  @Before
  public void setUp() {
    logcatButtonBuilder = new LogcatButtonBuilder(featureManager);
    when(featureManager.isDeviceLogcatButtonEnabled()).thenReturn(true);
  }

  @Test
  public void build_logcatDisabled_invisible() {
    when(featureManager.isDeviceLogcatButtonEnabled()).thenReturn(false);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();
    assertThat(logcatButtonBuilder.build(deviceInfo).getVisible()).isFalse();
  }

  @Test
  public void build_nonAndroidRealDevice_invisible() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("IosDevice"))
            .build();
    assertThat(logcatButtonBuilder.build(deviceInfo).getVisible()).isFalse();
  }

  @Test
  public void build_testbedDevice_invisible() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder().addType("AndroidRealDevice").addType("TestbedDevice"))
            .build();
    assertThat(logcatButtonBuilder.build(deviceInfo).getVisible()).isFalse();
  }

  @Test
  public void build_missingDevice_visibleAndDisabled() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.MISSING)
            .build();
    ActionButtonState state = logcatButtonBuilder.build(deviceInfo);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isFalse();
  }

  @Test
  public void build_sharedDevice_visibleAndDisabled() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("pool").setValue("shared"))))
            .build();
    ActionButtonState state = logcatButtonBuilder.build(deviceInfo);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isFalse();
  }

  @Test
  public void build_satelliteDevice_visibleAndEnabled() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();
    ActionButtonState state = logcatButtonBuilder.build(deviceInfo);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();
  }
}
