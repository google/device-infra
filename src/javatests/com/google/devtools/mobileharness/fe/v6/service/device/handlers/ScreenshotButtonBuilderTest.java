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
public final class ScreenshotButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private FeatureManager featureManager;

  private ScreenshotButtonBuilder screenshotButtonBuilder;

  @Before
  public void setUp() {
    screenshotButtonBuilder = new ScreenshotButtonBuilder(featureManager);
    when(featureManager.isDeviceScreenshotEnabled()).thenReturn(true);
  }

  @Test
  public void build_screenshotDisabled_invisible() {
    when(featureManager.isDeviceScreenshotEnabled()).thenReturn(false);
    assertThat(screenshotButtonBuilder.build(DeviceInfo.getDefaultInstance()).getVisible())
        .isFalse();
  }

  @Test
  public void build_missingDevice_visibleAndDisabled() {
    DeviceInfo deviceInfo = DeviceInfo.newBuilder().setDeviceStatus(DeviceStatus.MISSING).build();
    ActionButtonState state = screenshotButtonBuilder.build(deviceInfo);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isFalse();
  }

  @Test
  public void build_screenshotNotSupported_visibleAndDisabled() {
    ActionButtonState state = screenshotButtonBuilder.build(DeviceInfo.getDefaultInstance());
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isFalse();
  }

  @Test
  public void build_screenshotSupportedAndDeviceNotMissing_visibleAndEnabled() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder()
                                    .setName("screenshot_able")
                                    .setValue("true"))))
            .build();
    ActionButtonState state = screenshotButtonBuilder.build(deviceInfo);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();
  }
}
