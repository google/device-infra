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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUiStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NoOpConfigPusherHelperTest {

  private NoOpConfigPusherHelper helper;

  @Before
  public void setUp() {
    helper = new NoOpConfigPusherHelper();
  }

  @Test
  public void decorateUiStatus_alwaysReturnsUnchanged() {
    LabConfig labConfig = LabConfig.newBuilder().setHostName("host").build();
    HostConfigUiStatus baseStatus = HostConfigUiStatus.getDefaultInstance();

    HostConfigUiStatus decoratedStatus = helper.decorateUiStatus(baseStatus, labConfig);

    assertThat(decoratedStatus).isEqualTo(baseStatus);
  }

  @Test
  public void validateUpdate_allowsAllUpdates() throws Exception {
    LabConfig labConfig = LabConfig.newBuilder().setHostName("host").build();
    HostConfig incoming = HostConfig.getDefaultInstance();

    helper.validateUpdate(HostConfigSection.HOST_PERMISSIONS, incoming, labConfig);
    helper.validateUpdate(HostConfigSection.HOST_PROPERTIES, incoming, labConfig);
    helper.validateUpdate(HostConfigSection.DEVICE_CONFIG_MODE, incoming, labConfig);
    helper.validateUpdate(HostConfigSection.DEVICE_CONFIG, incoming, labConfig);
    helper.validateUpdate(HostConfigSection.DEVICE_DISCOVERY, incoming, labConfig);
  }

  @Test
  public void isSectionRestricted_alwaysReturnsFalse() {
    LabConfig labConfig = LabConfig.newBuilder().setHostName("host").build();

    assertThat(helper.isSectionRestricted(HostConfigSection.HOST_PERMISSIONS, labConfig)).isFalse();
    assertThat(helper.isSectionRestricted(HostConfigSection.HOST_PROPERTIES, labConfig)).isFalse();
    assertThat(helper.isSectionRestricted(HostConfigSection.DEVICE_CONFIG_MODE, labConfig))
        .isFalse();
    assertThat(helper.isSectionRestricted(HostConfigSection.DEVICE_CONFIG, labConfig)).isFalse();
    assertThat(helper.isSectionRestricted(HostConfigSection.DEVICE_DISCOVERY, labConfig)).isFalse();
  }
}
