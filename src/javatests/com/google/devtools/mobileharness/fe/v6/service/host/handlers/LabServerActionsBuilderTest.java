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

import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
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
  public void build_calculatesAnyActionVisibleCorrectly_oneVisible() {
    ActionButtonState invisible = ActionButtonState.newBuilder().setVisible(false).build();
    ActionButtonState visible = ActionButtonState.newBuilder().setVisible(true).build();

    when(mockStartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS))
        .thenReturn(invisible);
    when(mockRestartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS))
        .thenReturn(visible);
    when(mockStopButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS))
        .thenReturn(invisible);

    ActionButtonState releaseStateIfVisible =
        ActionButtonState.newBuilder().setTooltip("Visible").build();
    ActionButtonState releaseStateIfInvisible =
        ActionButtonState.newBuilder().setTooltip("Invisible").build();

    when(mockReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), true, DAEMON_STATUS))
        .thenReturn(releaseStateIfVisible);
    when(mockReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), false, DAEMON_STATUS))
        .thenReturn(releaseStateIfInvisible);

    var result =
        labServerActionsBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS);

    assertThat(result.getRelease().getTooltip()).isEqualTo("Visible");
  }

  @Test
  public void build_calculatesAnyActionVisibleCorrectly_noneVisible() {
    ActionButtonState invisible = ActionButtonState.newBuilder().setVisible(false).build();

    when(mockStartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS))
        .thenReturn(invisible);
    when(mockRestartButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS))
        .thenReturn(invisible);
    when(mockStopButtonBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS))
        .thenReturn(invisible);

    ActionButtonState releaseStateIfVisible =
        ActionButtonState.newBuilder().setTooltip("Visible").build();
    ActionButtonState releaseStateIfInvisible =
        ActionButtonState.newBuilder().setTooltip("Invisible").build();

    when(mockReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), true, DAEMON_STATUS))
        .thenReturn(releaseStateIfVisible);
    when(mockReleaseButtonBuilder.build(
            UNIVERSE, Optional.empty(), Optional.empty(), false, DAEMON_STATUS))
        .thenReturn(releaseStateIfInvisible);

    var result =
        labServerActionsBuilder.build(
            UNIVERSE,
            Optional.empty(),
            Optional.empty(),
            ACTIVITY,
            CONNECTIVITY_STATUS,
            DAEMON_STATUS);

    assertThat(result.getRelease().getTooltip()).isEqualTo("Invisible");
  }
}
