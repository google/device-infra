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
public final class LabServerReleaseButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FeatureManagerFactory mockFeatureManagerFactory;
  @Mock private FeatureManager mockFeatureManager;
  @Mock private FeatureReadiness mockFeatureReadiness;

  private LabServerReleaseButtonBuilder labServerReleaseButtonBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();
  private static final DaemonServerInfo.Status DAEMON_RUNNING =
      DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.RUNNING).build();
  private static final DaemonServerInfo.Status DAEMON_MISSING =
      DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.MISSING).build();

  @Before
  public void setUp() {
    labServerReleaseButtonBuilder =
        new LabServerReleaseButtonBuilder(mockFeatureManagerFactory, mockFeatureReadiness);
    when(mockFeatureManagerFactory.create(UNIVERSE)).thenReturn(mockFeatureManager);
  }

  @Test
  public void build_featureDisabled_returnsInvisible() {
    when(mockFeatureManager.isLabServerReleaseFeatureEnabled()).thenReturn(false);

    var result =
        labServerReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), true, DAEMON_RUNNING);

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_noActionVisibleAndNotDaemonMissing_returnsInvisible() {
    when(mockFeatureManager.isLabServerReleaseFeatureEnabled()).thenReturn(true);

    var result =
        labServerReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), false, DAEMON_RUNNING);

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_noActionVisibleButDaemonMissing_returnsVisibleAndDisabled() {
    when(mockFeatureManager.isLabServerReleaseFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerReleaseReady()).thenReturn(true);

    var result =
        labServerReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), false, DAEMON_MISSING);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isFalse();
    assertThat(result.getTooltip()).contains("daemon server is missing");
  }

  @Test
  public void build_actionVisibleDaemonRunning_returnsVisibleAndEnabled() {
    when(mockFeatureManager.isLabServerReleaseFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerReleaseReady()).thenReturn(true);

    var result =
        labServerReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), true, DAEMON_RUNNING);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getTooltip()).contains("Deploy a release");
  }

  @Test
  public void build_actionVisibleButDaemonMissing_returnsVisibleAndDisabled() {
    when(mockFeatureManager.isLabServerReleaseFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerReleaseReady()).thenReturn(true);

    var result =
        labServerReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), true, DAEMON_MISSING);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isFalse();
    assertThat(result.getTooltip()).contains("daemon server is missing");
  }
}
