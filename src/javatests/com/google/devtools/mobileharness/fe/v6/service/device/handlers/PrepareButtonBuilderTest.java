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
public final class PrepareButtonBuilderTest {
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private FeatureManagerFactory featureManagerFactory;
  @Mock private FeatureManager featureManager;
  @Mock private FeatureReadiness featureReadiness;

  private PrepareButtonBuilder prepareButtonBuilder;

  @Before
  public void setUp() {
    prepareButtonBuilder = new PrepareButtonBuilder(featureManagerFactory, featureReadiness);
    when(featureManagerFactory.create(UNIVERSE)).thenReturn(featureManager);
    when(featureManager.isPrepareDeviceFeatureEnabled()).thenReturn(true);
    when(featureReadiness.isPrepareDeviceReady()).thenReturn(true);
  }

  private static DeviceInfo fusionDevice(DeviceStatus status) {
    return DeviceInfo.newBuilder()
        .setDeviceStatus(status)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(
                            DeviceDimension.newBuilder().setName("dm_type").setValue("fusion"))))
        .build();
  }

  @Test
  public void build_prepareDisabled_invisible() {
    when(featureManager.isPrepareDeviceFeatureEnabled()).thenReturn(false);
    assertThat(prepareButtonBuilder.build(fusionDevice(DeviceStatus.IDLE), UNIVERSE).getVisible())
        .isFalse();
  }

  @Test
  public void build_missingDevice_invisible() {
    assertThat(
            prepareButtonBuilder.build(fusionDevice(DeviceStatus.MISSING), UNIVERSE).getVisible())
        .isFalse();
  }

  @Test
  public void build_nonFusionDevice_invisible() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("dm_type").setValue("mh"))))
            .build();
    assertThat(prepareButtonBuilder.build(deviceInfo, UNIVERSE).getVisible()).isFalse();
  }

  @Test
  public void build_noDmTypeDimension_invisible() {
    assertThat(prepareButtonBuilder.build(DeviceInfo.getDefaultInstance(), UNIVERSE).getVisible())
        .isFalse();
  }

  @Test
  public void build_fusionDeviceNotMissing_visibleAndEnabled() {
    ActionButtonState state = prepareButtonBuilder.build(fusionDevice(DeviceStatus.IDLE), UNIVERSE);
    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();
  }

  @Test
  public void build_prepareNotReady_isReadyFalse() {
    when(featureReadiness.isPrepareDeviceReady()).thenReturn(false);
    ActionButtonState state = prepareButtonBuilder.build(fusionDevice(DeviceStatus.IDLE), UNIVERSE);
    assertThat(state.getIsReady()).isFalse();
  }
}
