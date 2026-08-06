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

package com.google.devtools.mobileharness.fe.v6.service.host.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostReleaseInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerReleaseState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerReleaseStatus;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LabActivitiesTest {

  @Test
  public void create_emptyReleaseInfo_coreLab_returnsNotApplicable() {
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.empty(), true);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_UNSPECIFIED);
    assertThat(releaseStatus.getTitle()).isEqualTo("N/A");
  }

  @Test
  public void create_emptyReleaseInfo_nonCoreLab_returnsUnknown() {
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.empty(), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_UNKNOWN);
    assertThat(releaseStatus.getTitle()).isEqualTo("Unknown");
  }

  @Test
  public void create_starting() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("STARTING").build();
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.of(componentInfo), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STARTING);
    assertThat(releaseStatus.getTitle()).isEqualTo("Starting");
  }

  @Test
  public void create_running_returnsRunning() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("RUNNING").build();
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.of(componentInfo), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_RUNNING);
    assertThat(releaseStatus.getTitle()).isEqualTo("Running");
  }

  @Test
  public void create_running_disconnected_stillReturnsRunning() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("RUNNING").build();
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.of(componentInfo), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_RUNNING);
    assertThat(releaseStatus.getTitle()).isEqualTo("Running");
  }

  @Test
  public void create_error() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("ERROR").build();
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.of(componentInfo), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_ERROR);
    assertThat(releaseStatus.getTitle()).isEqualTo("Error");
  }

  @Test
  public void create_draining() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("DRAINING").build();
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.of(componentInfo), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_DRAINING);
    assertThat(releaseStatus.getTitle()).isEqualTo("Draining");
  }

  @Test
  public void create_drained() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("DRAINED").build();
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.of(componentInfo), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_DRAINED);
    assertThat(releaseStatus.getTitle()).isEqualTo("Drained");
  }

  @Test
  public void create_stopping() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("STOPPING").build();
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.of(componentInfo), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STOPPING);
    assertThat(releaseStatus.getTitle()).isEqualTo("Stopping");
  }

  @Test
  public void create_stopped() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("STOPPED").build();
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.of(componentInfo), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STOPPED);
    assertThat(releaseStatus.getTitle()).isEqualTo("Stopped");
  }

  @Test
  public void create_unknownStatus_returnsUnknown() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("OTHER").build();
    LabServerReleaseStatus releaseStatus = LabActivities.create(Optional.of(componentInfo), false);
    assertThat(releaseStatus.getState())
        .isEqualTo(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_UNKNOWN);
    assertThat(releaseStatus.getTitle()).isEqualTo("Unknown");
  }
}
