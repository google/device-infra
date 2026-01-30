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

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
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
public final class QuarantineButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private FeatureManager featureManager;

  private QuarantineButtonBuilder quarantineButtonBuilder;

  @Before
  public void setUp() {
    quarantineButtonBuilder = new QuarantineButtonBuilder(featureManager);
    when(featureManager.isDeviceQuarantineEnabled()).thenReturn(true);
  }

  @Test
  public void build_quarantineDisabled_invisible() {
    when(featureManager.isDeviceQuarantineEnabled()).thenReturn(false);
    assertThat(quarantineButtonBuilder.build(DeviceInfo.getDefaultInstance()).getVisible())
        .isFalse();
  }

  @Test
  public void build_quarantinedDevice_visibleEnabledWithUnquarantineTooltip() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceCondition(
                DeviceCondition.newBuilder()
                    .addTempDimension(
                        TempDimension.newBuilder()
                            .setDimension(
                                DeviceDimension.newBuilder()
                                    .setName("quarantined")
                                    .setValue("true"))))
            .build();
    ActionButtonState state = quarantineButtonBuilder.build(deviceInfo);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();
    assertThat(state.getTooltip())
        .isEqualTo("Unquarantine the device to allow it to be allocated by other tests.");
  }

  @Test
  public void build_notQuarantinedDevice_visibleEnabledWithQuarantineTooltip() {
    ActionButtonState state = quarantineButtonBuilder.build(DeviceInfo.getDefaultInstance());
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();
    assertThat(state.getTooltip())
        .isEqualTo("Quarantine the device to prevent it from being allocated by other tests.");
  }

  @Test
  public void build_quarantinedDimensionFalse_visibleEnabledWithQuarantineTooltip() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceCondition(
                DeviceCondition.newBuilder()
                    .addTempDimension(
                        TempDimension.newBuilder()
                            .setDimension(
                                DeviceDimension.newBuilder()
                                    .setName("quarantined")
                                    .setValue("false"))))
            .build();
    ActionButtonState state = quarantineButtonBuilder.build(deviceInfo);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();
    assertThat(state.getTooltip())
        .isEqualTo("Quarantine the device to prevent it from being allocated by other tests.");
  }

  @Test
  public void build_otherDimensionTrue_visibleEnabledWithQuarantineTooltip() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceCondition(
                DeviceCondition.newBuilder()
                    .addTempDimension(
                        TempDimension.newBuilder()
                            .setDimension(
                                DeviceDimension.newBuilder().setName("other").setValue("true"))))
            .build();
    ActionButtonState state = quarantineButtonBuilder.build(deviceInfo);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();
    assertThat(state.getTooltip())
        .isEqualTo("Quarantine the device to prevent it from being allocated by other tests.");
  }
}
