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
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo;
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
public final class LabServerRestartButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FeatureManagerFactory mockFeatureManagerFactory;
  @Mock private FeatureManager mockFeatureManager;
  @Mock private FeatureReadiness mockFeatureReadiness;

  private LabServerRestartButtonBuilder labServerRestartButtonBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Before
  public void setUp() {
    labServerRestartButtonBuilder =
        new LabServerRestartButtonBuilder(mockFeatureManagerFactory, mockFeatureReadiness);
    when(mockFeatureManagerFactory.create(UNIVERSE)).thenReturn(mockFeatureManager);
  }

  @Test
  public void build_featureDisabled_returnsInvisible() {
    when(mockFeatureManager.isLabServerRestartFeatureEnabled()).thenReturn(false);

    var result =
        labServerRestartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            LabServerInfo.Activity.getDefaultInstance(),
            HostConnectivityStatus.getDefaultInstance(),
            DaemonServerInfo.Status.getDefaultInstance());

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_isFusionOrCore_returnsInvisible() {
    when(mockFeatureManager.isLabServerRestartFeatureEnabled()).thenReturn(true);

    var result =
        labServerRestartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.of("fusion"),
            LabServerInfo.Activity.getDefaultInstance(),
            HostConnectivityStatus.getDefaultInstance(),
            DaemonServerInfo.Status.getDefaultInstance());

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_visibleConditionMetAndReady_returnsVisibleAndEnabled() {
    when(mockFeatureManager.isLabServerRestartFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerRestartReady()).thenReturn(true);

    HostConnectivityStatus runningStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.RUNNING).build();
    DaemonServerInfo.Status daemonRunning =
        DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.RUNNING).build();
    LabServerInfo.Activity startedActivity =
        LabServerInfo.Activity.newBuilder().setState(LabServerInfo.ActivityState.STARTED).build();

    var result =
        labServerRestartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            startedActivity,
            runningStatus,
            daemonRunning);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getTooltip()).contains("Restart the lab server");
  }

  @Test
  public void build_daemonNotRunning_returnsInvisible() {
    when(mockFeatureManager.isLabServerRestartFeatureEnabled()).thenReturn(true);

    HostConnectivityStatus runningStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.RUNNING).build();
    DaemonServerInfo.Status daemonNotRunning =
        DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.MISSING).build();
    LabServerInfo.Activity startedActivity =
        LabServerInfo.Activity.newBuilder().setState(LabServerInfo.ActivityState.STARTED).build();

    var result =
        labServerRestartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            startedActivity,
            runningStatus,
            daemonNotRunning);

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_activityStateNotTarget_returnsInvisible() {
    when(mockFeatureManager.isLabServerRestartFeatureEnabled()).thenReturn(true);

    HostConnectivityStatus runningStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.RUNNING).build();
    DaemonServerInfo.Status daemonRunning =
        DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.RUNNING).build();
    LabServerInfo.Activity stoppedActivity =
        LabServerInfo.Activity.newBuilder().setState(LabServerInfo.ActivityState.STOPPED).build();

    var result =
        labServerRestartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            stoppedActivity,
            runningStatus,
            daemonRunning);

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_hostNotRunningButVisibleConditionMet_returnsVisibleAndEnabled() {
    when(mockFeatureManager.isLabServerRestartFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerRestartReady()).thenReturn(true);

    HostConnectivityStatus missingStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.MISSING).build();
    DaemonServerInfo.Status daemonRunning =
        DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.RUNNING).build();
    LabServerInfo.Activity startedActivity =
        LabServerInfo.Activity.newBuilder().setState(LabServerInfo.ActivityState.STARTED).build();

    var result =
        labServerRestartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            startedActivity,
            missingStatus,
            daemonRunning);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
  }
}
