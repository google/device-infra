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
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo.Activity;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo.ActivityState;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LabActivitiesTest {

  @Test
  public void create_emptyReleaseInfo_returnsNotApplicable() {
    Activity activity =
        LabActivities.create(Optional.empty(), HostConnectivityStatus.getDefaultInstance());
    assertThat(activity.getState()).isEqualTo(ActivityState.ACTIVITY_STATE_UNSPECIFIED);
    assertThat(activity.getTitle()).isEqualTo("N/A");
  }

  @Test
  public void create_starting() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("STARTING").build();
    Activity activity =
        LabActivities.create(
            Optional.of(componentInfo), HostConnectivityStatus.getDefaultInstance());
    assertThat(activity.getState()).isEqualTo(ActivityState.STARTING);
    assertThat(activity.getTitle()).isEqualTo("Starting");
  }

  @Test
  public void create_runningAndConnected_returnsStarted() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("RUNNING").build();
    HostConnectivityStatus connectivityStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.RUNNING).build();
    Activity activity = LabActivities.create(Optional.of(componentInfo), connectivityStatus);
    assertThat(activity.getState()).isEqualTo(ActivityState.STARTED);
    assertThat(activity.getTitle()).isEqualTo("Started");
  }

  @Test
  public void create_runningAndMissing_returnsStartedButDisconnected() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("RUNNING").build();
    HostConnectivityStatus connectivityStatus =
        HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.MISSING).build();
    Activity activity = LabActivities.create(Optional.of(componentInfo), connectivityStatus);
    assertThat(activity.getState()).isEqualTo(ActivityState.STARTED_BUT_DISCONNECTED);
    assertThat(activity.getTitle()).isEqualTo("Started (but disconnected)");
  }

  @Test
  public void create_error() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("ERROR").build();
    Activity activity =
        LabActivities.create(
            Optional.of(componentInfo), HostConnectivityStatus.getDefaultInstance());
    assertThat(activity.getState()).isEqualTo(ActivityState.ERROR);
    assertThat(activity.getTitle()).isEqualTo("Error");
  }

  @Test
  public void create_draining() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("DRAINING").build();
    Activity activity =
        LabActivities.create(
            Optional.of(componentInfo), HostConnectivityStatus.getDefaultInstance());
    assertThat(activity.getState()).isEqualTo(ActivityState.DRAINING);
    assertThat(activity.getTitle()).isEqualTo("Draining");
  }

  @Test
  public void create_drained() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("DRAINED").build();
    Activity activity =
        LabActivities.create(
            Optional.of(componentInfo), HostConnectivityStatus.getDefaultInstance());
    assertThat(activity.getState()).isEqualTo(ActivityState.DRAINED);
    assertThat(activity.getTitle()).isEqualTo("Drained");
  }

  @Test
  public void create_stopping() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("STOPPING").build();
    Activity activity =
        LabActivities.create(
            Optional.of(componentInfo), HostConnectivityStatus.getDefaultInstance());
    assertThat(activity.getState()).isEqualTo(ActivityState.STOPPING);
    assertThat(activity.getTitle()).isEqualTo("Stopping");
  }

  @Test
  public void create_stopped() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("STOPPED").build();
    Activity activity =
        LabActivities.create(
            Optional.of(componentInfo), HostConnectivityStatus.getDefaultInstance());
    assertThat(activity.getState()).isEqualTo(ActivityState.STOPPED);
    assertThat(activity.getTitle()).isEqualTo("Stopped");
  }

  @Test
  public void create_unknownStatus_returnsUnknown() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setStatus("OTHER").build();
    Activity activity =
        LabActivities.create(
            Optional.of(componentInfo), HostConnectivityStatus.getDefaultInstance());
    assertThat(activity.getState()).isEqualTo(ActivityState.UNKNOWN);
    assertThat(activity.getTitle()).isEqualTo("Unknown");
  }
}
