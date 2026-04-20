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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.DeviceDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.SubDeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import com.google.devtools.mobileharness.fe.v6.service.shared.SubDeviceInfoListFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityContext;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityResult;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RemoteControlButtonBuilderTest {
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private RemoteControlEligibilityChecker checker;
  @Bind @Mock private SubDeviceInfoListFactory subDeviceInfoListFactory;
  @Bind @Mock private FeatureManagerFactory featureManagerFactory;
  @Bind @Mock private FeatureReadiness featureReadiness;

  @Mock private FeatureManager featureManager;

  @Inject private RemoteControlButtonBuilder remoteControlButtonBuilder;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    when(featureManagerFactory.create(any())).thenReturn(featureManager);
    when(featureManager.isDeviceRemoteControlFeatureEnabled()).thenReturn(true);
    when(featureReadiness.isDeviceRemoteControlReady()).thenReturn(true);
  }

  @Test
  public void build_success() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .build();

    when(checker.checkTechnicalEligibility(any(RemoteControlEligibilityContext.class)))
        .thenReturn(RemoteControlEligibilityResult.builder().setIsEligible(true).build());

    ActionButtonState state = remoteControlButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();

    ArgumentCaptor<RemoteControlEligibilityContext> contextCaptor =
        ArgumentCaptor.forClass(RemoteControlEligibilityContext.class);
    verify(checker).checkTechnicalEligibility(contextCaptor.capture());
    RemoteControlEligibilityContext context = contextCaptor.getValue();
    assertThat(context.isMultipleSelection()).isFalse();
    assertThat(context.isSubDevice()).isFalse();
    assertThat(context.hasCommSubDevice()).isFalse();
    assertThat(context.deviceStatus()).isEqualTo(DeviceStatus.IDLE);
    assertThat(context.types()).containsExactly("AndroidRealDevice");
  }

  @Test
  public void build_testbedWithCommSubDevice() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("TestbedDevice"))
            .build();

    SubDeviceInfo subDevice =
        SubDeviceInfo.newBuilder()
            .setId("sub_id")
            .addDimensions(
                DeviceDimension.newBuilder().setName("communication_type").setValue("adb"))
            .build();

    when(subDeviceInfoListFactory.create(any())).thenReturn(ImmutableList.of(subDevice));
    when(checker.checkTechnicalEligibility(any(RemoteControlEligibilityContext.class)))
        .thenReturn(RemoteControlEligibilityResult.builder().setIsEligible(true).build());

    ActionButtonState state = remoteControlButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();

    ArgumentCaptor<RemoteControlEligibilityContext> contextCaptor =
        ArgumentCaptor.forClass(RemoteControlEligibilityContext.class);
    verify(checker).checkTechnicalEligibility(contextCaptor.capture());
    RemoteControlEligibilityContext context = contextCaptor.getValue();
    assertThat(context.hasCommSubDevice()).isTrue();
    assertThat(context.types()).containsExactly("TestbedDevice");
  }

  @Test
  public void build_testbedWithoutCommSubDevice() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("TestbedDevice"))
            .build();

    SubDeviceInfo subDevice =
        SubDeviceInfo.newBuilder()
            .setId("sub_id")
            .addDimensions(DeviceDimension.newBuilder().setName("other").setValue("value"))
            .build();

    when(subDeviceInfoListFactory.create(any())).thenReturn(ImmutableList.of(subDevice));
    when(checker.checkTechnicalEligibility(any(RemoteControlEligibilityContext.class)))
        .thenReturn(RemoteControlEligibilityResult.builder().setIsEligible(true).build());

    ActionButtonState state = remoteControlButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();

    ArgumentCaptor<RemoteControlEligibilityContext> contextCaptor =
        ArgumentCaptor.forClass(RemoteControlEligibilityContext.class);
    verify(checker).checkTechnicalEligibility(contextCaptor.capture());
    RemoteControlEligibilityContext context = contextCaptor.getValue();
    assertThat(context.hasCommSubDevice()).isFalse();
  }

  @Test
  public void build_ineligibleButVisible_deviceNotIdle() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .build();

    when(checker.checkTechnicalEligibility(any(RemoteControlEligibilityContext.class)))
        .thenReturn(
            RemoteControlEligibilityResult.builder()
                .setIsEligible(false)
                .setReasonCode(IneligibilityReasonCode.DEVICE_NOT_IDLE)
                .build());

    ActionButtonState state = remoteControlButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isFalse();
  }

  @Test
  public void build_ineligibleAndNotVisible_permissionDenied() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .build();

    when(checker.checkTechnicalEligibility(any(RemoteControlEligibilityContext.class)))
        .thenReturn(
            RemoteControlEligibilityResult.builder()
                .setIsEligible(false)
                .setReasonCode(IneligibilityReasonCode.PERMISSION_DENIED)
                .build());

    ActionButtonState state = remoteControlButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(state.getVisible()).isFalse();
    assertThat(state.getEnabled()).isFalse();
  }

  @Test
  public void build_remoteControlFeatureDisabled() {
    DeviceInfo deviceInfo = DeviceInfo.getDefaultInstance();
    when(featureManager.isDeviceRemoteControlFeatureEnabled()).thenReturn(false);

    ActionButtonState state = remoteControlButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(state.getVisible()).isFalse();
  }

  @Test
  public void build_remoteControlNotReady() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .build();
    when(checker.checkTechnicalEligibility(any(RemoteControlEligibilityContext.class)))
        .thenReturn(RemoteControlEligibilityResult.builder().setIsEligible(true).build());
    when(featureReadiness.isDeviceRemoteControlReady()).thenReturn(false);

    ActionButtonState state = remoteControlButtonBuilder.build(deviceInfo, UNIVERSE);

    assertThat(state.getIsReady()).isFalse();
  }
}
