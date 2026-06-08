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

/*
 * Copyright 2026 Google LLC
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

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
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
public final class DeviceDecommissionButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FeatureManagerFactory mockFeatureManagerFactory;
  @Mock private FeatureManager mockFeatureManager;
  @Mock private FeatureReadiness mockFeatureReadiness;

  private DeviceDecommissionButtonBuilder decommissionButtonBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Before
  public void setUp() {
    decommissionButtonBuilder =
        new DeviceDecommissionButtonBuilder(mockFeatureManagerFactory, mockFeatureReadiness);
    when(mockFeatureManagerFactory.create(UNIVERSE)).thenReturn(mockFeatureManager);
  }

  @Test
  public void build_featureDisabled_returnsInvisible() {
    when(mockFeatureManager.isDeviceDecommissionFeatureEnabled()).thenReturn(false);
    DeviceInfo deviceInfo = DeviceInfo.newBuilder().setDeviceStatus(DeviceStatus.MISSING).build();

    var result = decommissionButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_featureEnabledDeviceNotMissing_returnsInvisible() {
    when(mockFeatureManager.isDeviceDecommissionFeatureEnabled()).thenReturn(true);
    DeviceInfo deviceInfo = DeviceInfo.newBuilder().setDeviceStatus(DeviceStatus.IDLE).build();

    var result = decommissionButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_featureEnabledDeviceMissingNotReady_returnsComingSoon() {
    when(mockFeatureManager.isDeviceDecommissionFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isDeviceDecommissionReady()).thenReturn(false);
    DeviceInfo deviceInfo = DeviceInfo.newBuilder().setDeviceStatus(DeviceStatus.MISSING).build();

    var result = decommissionButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getIsReady()).isFalse();
    assertThat(result.getTooltip()).isEqualTo("Decommission the missing device");
  }

  @Test
  public void build_featureEnabledDeviceMissingReady_returnsVisibleAndEnabled() {
    when(mockFeatureManager.isDeviceDecommissionFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isDeviceDecommissionReady()).thenReturn(true);
    DeviceInfo deviceInfo = DeviceInfo.newBuilder().setDeviceStatus(DeviceStatus.MISSING).build();

    var result = decommissionButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getIsReady()).isTrue();
    assertThat(result.getTooltip()).isEqualTo("Decommission the missing device");
  }
}
