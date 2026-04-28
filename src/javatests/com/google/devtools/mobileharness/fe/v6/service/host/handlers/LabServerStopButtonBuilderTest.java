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
public final class LabServerStopButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FeatureManagerFactory mockFeatureManagerFactory;
  @Mock private FeatureManager mockFeatureManager;
  @Mock private FeatureReadiness mockFeatureReadiness;

  private LabServerStopButtonBuilder labServerStopButtonBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Before
  public void setUp() {
    labServerStopButtonBuilder =
        new LabServerStopButtonBuilder(mockFeatureManagerFactory, mockFeatureReadiness);
    when(mockFeatureManagerFactory.create(UNIVERSE)).thenReturn(mockFeatureManager);
  }

  @Test
  public void build_featureDisabled_returnsInvisible() {
    when(mockFeatureManager.isLabServerStopFeatureEnabled()).thenReturn(false);

    var result =
        labServerStopButtonBuilder.build(
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
    when(mockFeatureManager.isLabServerStopFeatureEnabled()).thenReturn(true);

    var result =
        labServerStopButtonBuilder.build(
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
    when(mockFeatureManager.isLabServerStopFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerStopReady()).thenReturn(true);

    HostConnectivityStatus runningStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.RUNNING).build();
    DaemonServerInfo.Status daemonRunning =
        DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.RUNNING).build();
    LabServerInfo.Activity startedActivity =
        LabServerInfo.Activity.newBuilder().setState(LabServerInfo.ActivityState.STARTED).build();

    var result =
        labServerStopButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            startedActivity,
            runningStatus,
            daemonRunning);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getTooltip()).contains("stops the lab server");
  }

  @Test
  public void build_visibleConditionMetAndReadyDisconnectedActivity_returnsVisibleAndEnabled() {
    when(mockFeatureManager.isLabServerStopFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerStopReady()).thenReturn(true);

    HostConnectivityStatus runningStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.RUNNING).build();
    DaemonServerInfo.Status daemonRunning =
        DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.RUNNING).build();
    LabServerInfo.Activity disconnectedActivity =
        LabServerInfo.Activity.newBuilder()
            .setState(LabServerInfo.ActivityState.STARTED_BUT_DISCONNECTED)
            .build();

    var result =
        labServerStopButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            disconnectedActivity,
            runningStatus,
            daemonRunning);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
  }

  @Test
  public void build_visibleConditionMetAndReadyErrorActivity_returnsVisibleAndEnabled() {
    when(mockFeatureManager.isLabServerStopFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerStopReady()).thenReturn(true);

    HostConnectivityStatus runningStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.RUNNING).build();
    DaemonServerInfo.Status daemonRunning =
        DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.RUNNING).build();
    LabServerInfo.Activity errorActivity =
        LabServerInfo.Activity.newBuilder().setState(LabServerInfo.ActivityState.ERROR).build();

    var result =
        labServerStopButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            errorActivity,
            runningStatus,
            daemonRunning);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
  }

  @Test
  public void build_hostNotRunningButVisibleConditionMet_returnsVisibleAndEnabled() {
    when(mockFeatureManager.isLabServerStopFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerStopReady()).thenReturn(true);

    HostConnectivityStatus missingStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.MISSING).build();
    DaemonServerInfo.Status daemonRunning =
        DaemonServerInfo.Status.newBuilder().setState(DaemonServerInfo.State.RUNNING).build();
    LabServerInfo.Activity startedActivity =
        LabServerInfo.Activity.newBuilder().setState(LabServerInfo.ActivityState.STARTED).build();

    var result =
        labServerStopButtonBuilder.build(
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
