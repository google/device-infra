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
import com.google.devtools.mobileharness.fe.v6.service.proto.common.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
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
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private FeatureManagerFactory featureManagerFactory;
  @Mock private FeatureManager featureManager;
  @Mock private FeatureReadiness featureReadiness;

  private LogcatButtonBuilder logcatButtonBuilder;

  @Before
  public void setUp() {
    logcatButtonBuilder = new LogcatButtonBuilder(featureManagerFactory, featureReadiness);
    when(featureManagerFactory.create(UNIVERSE)).thenReturn(featureManager);
    when(featureManager.isDeviceLogcatFeatureEnabled()).thenReturn(true);
    when(featureReadiness.isDeviceLogcatReady()).thenReturn(true);
  }

  @Test
  public void build_logcatDisabled_invisible() {
    when(featureManager.isDeviceLogcatFeatureEnabled()).thenReturn(false);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();
    assertThat(logcatButtonBuilder.build(deviceInfo, UNIVERSE).getVisible()).isFalse();
  }

  @Test
  public void build_nonAndroidRealDevice_invisible() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("IosDevice"))
            .build();
    assertThat(logcatButtonBuilder.build(deviceInfo, UNIVERSE).getVisible()).isFalse();
  }

  @Test
  public void build_testbedDevice_invisible() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder().addType("AndroidRealDevice").addType("TestbedDevice"))
            .build();
    assertThat(logcatButtonBuilder.build(deviceInfo, UNIVERSE).getVisible()).isFalse();
  }

  @Test
  public void build_missingDevice_visibleAndDisabled() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.MISSING)
            .build();
    ActionButtonState state = logcatButtonBuilder.build(deviceInfo, UNIVERSE);
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
    ActionButtonState state = logcatButtonBuilder.build(deviceInfo, UNIVERSE);
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
    ActionButtonState state = logcatButtonBuilder.build(deviceInfo, UNIVERSE);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();
  }

  @Test
  public void build_logcatNotReady_isReadyFalse() {
    when(featureReadiness.isDeviceLogcatReady()).thenReturn(false);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();
    ActionButtonState state = logcatButtonBuilder.build(deviceInfo, UNIVERSE);
    assertThat(state.getIsReady()).isFalse();
  }
}
