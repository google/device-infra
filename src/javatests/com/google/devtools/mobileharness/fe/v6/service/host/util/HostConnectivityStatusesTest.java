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

import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HostConnectivityStatusesTest {

  @Test
  public void create_emptyLabInfo_returnsUnknown() {
    HostConnectivityStatus status = HostConnectivityStatuses.create(Optional.empty());
    assertThat(status.getState()).isEqualTo(HostConnectivityStatus.State.UNKNOWN);
    assertThat(status.getTitle()).isEqualTo("Unknown");
  }

  @Test
  public void create_labRunning_returnsRunning() {
    LabInfo labInfo = LabInfo.newBuilder().setLabStatus(LabStatus.LAB_RUNNING).build();
    HostConnectivityStatus status = HostConnectivityStatuses.create(Optional.of(labInfo));
    assertThat(status.getState()).isEqualTo(HostConnectivityStatus.State.RUNNING);
    assertThat(status.getTitle()).isEqualTo("Running");
  }

  @Test
  public void create_labMissing_returnsMissing() {
    LabInfo labInfo = LabInfo.newBuilder().setLabStatus(LabStatus.LAB_MISSING).build();
    HostConnectivityStatus status = HostConnectivityStatuses.create(Optional.of(labInfo));
    assertThat(status.getState()).isEqualTo(HostConnectivityStatus.State.MISSING);
    assertThat(status.getTitle()).isEqualTo("Missing");
  }
}
