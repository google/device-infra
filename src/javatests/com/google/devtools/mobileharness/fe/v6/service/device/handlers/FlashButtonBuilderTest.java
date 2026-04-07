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
import com.google.devtools.mobileharness.fe.v6.service.proto.device.FlashActionButtonState;
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
public final class FlashButtonBuilderTest {
  private static final UniverseScope SELF_UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private FeatureManagerFactory featureManagerFactory;
  @Mock private FeatureManager featureManager;
  @Mock private FeatureReadiness featureReadiness;

  private FlashButtonBuilder flashButtonBuilder;

  @Before
  public void setUp() {
    flashButtonBuilder = new FlashButtonBuilder(featureManagerFactory, featureReadiness);
    when(featureManagerFactory.create(SELF_UNIVERSE)).thenReturn(featureManager);
    when(featureReadiness.isDeviceFlashingReady()).thenReturn(true);
  }

  @Test
  public void build_whenDeviceFlashingDisabled_shouldBeInvisible() {
    when(featureManager.isDeviceFlashingFeatureEnabled()).thenReturn(false);
    DeviceInfo deviceInfo = DeviceInfo.getDefaultInstance();

    FlashActionButtonState state = flashButtonBuilder.build(deviceInfo, SELF_UNIVERSE);

    assertThat(state.getState().getVisible()).isFalse();
  }

  @Test
  public void build_whenDeviceFlashingDisabled_forAndroidDevice_shouldBeInvisible() {
    when(featureManager.isDeviceFlashingFeatureEnabled()).thenReturn(false);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();

    FlashActionButtonState state = flashButtonBuilder.build(deviceInfo, SELF_UNIVERSE);

    assertThat(state.getState().getVisible()).isFalse();
  }

  @Test
  public void build_whenNotFlashableDevice_shouldBeInvisible() {
    when(featureManager.isDeviceFlashingFeatureEnabled()).thenReturn(true);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("IosRealDevice"))
            .build();

    FlashActionButtonState state = flashButtonBuilder.build(deviceInfo, SELF_UNIVERSE);

    assertThat(state.getState().getVisible()).isFalse();
  }

  @Test
  public void build_whenSharedDevice_shouldBeVisibleAndDisabled() {
    when(featureManager.isDeviceFlashingFeatureEnabled()).thenReturn(true);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("pool").setValue("shared"))))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();

    FlashActionButtonState state = flashButtonBuilder.build(deviceInfo, SELF_UNIVERSE);

    assertThat(state.getState().getVisible()).isTrue();
    assertThat(state.getState().getEnabled()).isFalse();
    assertThat(state.getState().getTooltip())
        .isEqualTo("Device flashing is only supported on satellite lab Android devices.");
  }

  @Test
  public void build_whenTestbedDevice_shouldBeVisibleAndDisabled() {
    when(featureManager.isDeviceFlashingFeatureEnabled()).thenReturn(true);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder().addType("AndroidRealDevice").addType("TestbedDevice"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();

    FlashActionButtonState state = flashButtonBuilder.build(deviceInfo, SELF_UNIVERSE);

    assertThat(state.getState().getVisible()).isTrue();
    assertThat(state.getState().getEnabled()).isFalse();
    assertThat(state.getState().getTooltip())
        .isEqualTo("Device flashing is only supported on satellite lab Android devices.");
  }

  @Test
  public void build_whenSatelliteDeviceIdle_shouldBeVisibleAndEnabled() {
    when(featureManager.isDeviceFlashingFeatureEnabled()).thenReturn(true);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();

    FlashActionButtonState state = flashButtonBuilder.build(deviceInfo, SELF_UNIVERSE);

    assertThat(state.getState().getVisible()).isTrue();
    assertThat(state.getState().getEnabled()).isTrue();
    assertThat(state.getState().getTooltip()).isEqualTo("Flash the device");
  }

  @Test
  public void build_whenSatelliteDeviceBusy_shouldBeVisibleAndDisabled() {
    when(featureManager.isDeviceFlashingFeatureEnabled()).thenReturn(true);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.BUSY)
            .build();

    FlashActionButtonState state = flashButtonBuilder.build(deviceInfo, SELF_UNIVERSE);

    assertThat(state.getState().getVisible()).isTrue();
    assertThat(state.getState().getEnabled()).isFalse();
    assertThat(state.getState().getTooltip())
        .isEqualTo(
            "Device flash is only allowed on IDLE device. Please wait for device idle and retry.");
  }

  @Test
  public void build_withAndroidFlashableDevice_shouldBeVisibleAndEnabled() {
    when(featureManager.isDeviceFlashingFeatureEnabled()).thenReturn(true);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidFlashableDevice"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();

    FlashActionButtonState state = flashButtonBuilder.build(deviceInfo, SELF_UNIVERSE);

    assertThat(state.getState().getVisible()).isTrue();
    assertThat(state.getState().getEnabled()).isTrue();
    assertThat(state.getState().getTooltip()).isEqualTo("Flash the device");
  }

  @Test
  public void build_whenFlashingNotReady_shouldSetIsReadyFalse() {
    when(featureManager.isDeviceFlashingFeatureEnabled()).thenReturn(true);
    when(featureReadiness.isDeviceFlashingReady()).thenReturn(false);
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .build();

    FlashActionButtonState state = flashButtonBuilder.build(deviceInfo, SELF_UNIVERSE);

    assertThat(state.getState().getIsReady()).isFalse();
  }
}
