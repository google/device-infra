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

import com.google.devtools.mobileharness.fe.v6.service.proto.common.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DaemonServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo;
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
public final class LabServerActionsBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LabServerReleaseButtonBuilder mockReleaseButtonBuilder;
  @Mock private LabServerStartButtonBuilder mockStartButtonBuilder;
  @Mock private LabServerRestartButtonBuilder mockRestartButtonBuilder;
  @Mock private LabServerStopButtonBuilder mockStopButtonBuilder;

  private LabServerActionsBuilder labServerActionsBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();
  private static final LabServerInfo.Activity ACTIVITY =
      LabServerInfo.Activity.getDefaultInstance();
  private static final HostConnectivityStatus CONNECTIVITY_STATUS =
      HostConnectivityStatus.getDefaultInstance();
  private static final DaemonServerInfo.Status DAEMON_STATUS =
      DaemonServerInfo.Status.getDefaultInstance();

  @Before
  public void setUp() {
    labServerActionsBuilder =
        new LabServerActionsBuilder(
            mockReleaseButtonBuilder,
            mockStartButtonBuilder,
            mockRestartButtonBuilder,
            mockStopButtonBuilder);
  }

  @Test
  public void build_returnsAllActions() {
    ActionButtonState startState = ActionButtonState.newBuilder().setTooltip("Start").build();
    ActionButtonState restartState = ActionButtonState.newBuilder().setTooltip("Restart").build();
    ActionButtonState stopState = ActionButtonState.newBuilder().setTooltip("Stop").build();
    ActionButtonState releaseState = ActionButtonState.newBuilder().setTooltip("Release").build();

    when(mockStartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS))
        .thenReturn(startState);
    when(mockRestartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS))
        .thenReturn(restartState);
    when(mockStopButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS))
        .thenReturn(stopState);
    when(mockReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), DAEMON_STATUS))
        .thenReturn(releaseState);

    var result =
        labServerActionsBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS);

    assertThat(result.getStart()).isEqualTo(startState);
    assertThat(result.getRestart()).isEqualTo(restartState);
    assertThat(result.getStop()).isEqualTo(stopState);
    assertThat(result.getRelease()).isEqualTo(releaseState);
  }
}
