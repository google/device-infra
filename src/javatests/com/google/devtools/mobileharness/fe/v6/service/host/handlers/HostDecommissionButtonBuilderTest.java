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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DaemonServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class HostDecommissionButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FeatureManagerFactory mockFeatureManagerFactory;
  @Mock private FeatureManager mockFeatureManager;
  @Mock private FeatureReadiness mockFeatureReadiness;

  private HostDecommissionButtonBuilder hostDecommissionButtonBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();
  private static final DaemonServerInfo.Status DAEMON_RUNNING =
      DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.RUNNING).build();
  private static final DaemonServerInfo.Status DAEMON_MISSING =
      DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.MISSING).build();

  @Before
  public void setUp() {
    hostDecommissionButtonBuilder =
        new HostDecommissionButtonBuilder(mockFeatureManagerFactory, mockFeatureReadiness);
    when(mockFeatureManagerFactory.create(UNIVERSE)).thenReturn(mockFeatureManager);
  }

  @Test
  public void build_decommissionFeatureDisabled_returnsInvisible() {
    when(mockFeatureManager.isHostDecommissionFeatureEnabled()).thenReturn(false);

    var result =
        hostDecommissionButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), DAEMON_RUNNING);

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_decommissionFeatureEnabledStateNotMissing_returnsInvisible() {
    when(mockFeatureManager.isHostDecommissionFeatureEnabled()).thenReturn(true);

    LabInfo labInfo = LabInfo.newBuilder().setLabStatus(LabStatus.LAB_RUNNING).build();

    var result =
        hostDecommissionButtonBuilder.build(
            UNIVERSE, Optional.of(labInfo), Optional.empty(), DAEMON_RUNNING);

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_decommissionFeatureEnabledStateMissingNotReady_returnsComingSoon() {
    when(mockFeatureManager.isHostDecommissionFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isHostDecommissionReady()).thenReturn(false);

    LabInfo labInfo = LabInfo.newBuilder().setLabStatus(LabStatus.LAB_MISSING).build();

    var result =
        hostDecommissionButtonBuilder.build(
            UNIVERSE, Optional.of(labInfo), Optional.empty(), DAEMON_MISSING);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getIsReady()).isFalse();
    assertThat(result.getTooltip()).isEqualTo("Decommission the host");
  }

  @Test
  public void build_decommissionFeatureEnabledStateMissingReady_returnsVisibleAndEnabled() {
    when(mockFeatureManager.isHostDecommissionFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isHostDecommissionReady()).thenReturn(true);

    LabInfo labInfo = LabInfo.newBuilder().setLabStatus(LabStatus.LAB_MISSING).build();

    var result =
        hostDecommissionButtonBuilder.build(
            UNIVERSE, Optional.of(labInfo), Optional.empty(), DAEMON_MISSING);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getTooltip()).isEqualTo("Decommission the host");
  }

  @Test
  public void build_decommissionFeatureDisabledStateMissing_returnsInvisible() {
    when(mockFeatureManager.isHostDecommissionFeatureEnabled()).thenReturn(false);
    LabInfo labInfo = LabInfo.newBuilder().setLabStatus(LabStatus.LAB_MISSING).build();

    var result =
        hostDecommissionButtonBuilder.build(
            UNIVERSE, Optional.of(labInfo), Optional.empty(), DAEMON_MISSING);

    assertThat(result.getVisible()).isFalse();
  }
}
