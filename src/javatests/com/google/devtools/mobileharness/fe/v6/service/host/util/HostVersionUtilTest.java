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

import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostReleaseInfo;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HostVersionUtilTest {

  @Test
  public void resolveCurrentVersion_fromLabInfo_success() {
    LabInfo labInfo =
        LabInfo.newBuilder()
            .setLabServerFeature(
                LabServerFeature.newBuilder()
                    .setHostProperties(
                        HostProperties.newBuilder()
                            .addHostProperty(
                                HostProperty.newBuilder()
                                    .setKey("host_version")
                                    .setValue("1.0.0"))))
            .build();

    Optional<String> version =
        HostVersionUtil.resolveCurrentVersion(Optional.of(labInfo), Optional.empty());

    assertThat(version).hasValue("1.0.0");
  }

  @Test
  public void resolveCurrentVersion_fromHostReleaseInfoFallback_success() {
    HostReleaseInfo hostReleaseInfo =
        HostReleaseInfo.builder()
            .setLabServerReleaseInfo(
                Optional.of(HostReleaseInfo.ComponentInfo.builder().setVersion("2.0.0").build()))
            .build();

    Optional<String> version =
        HostVersionUtil.resolveCurrentVersion(Optional.empty(), Optional.of(hostReleaseInfo));

    assertThat(version).hasValue("2.0.0");
  }

  @Test
  public void resolveCurrentVersion_empty_returnsEmpty() {
    Optional<String> version =
        HostVersionUtil.resolveCurrentVersion(Optional.empty(), Optional.empty());

    assertThat(version).isEmpty();
  }

  @Test
  public void normalizeVersion_null_returnsEmpty() {
    assertThat(HostVersionUtil.normalizeVersion(null)).isEmpty();
  }

  @Test
  public void normalizeVersion_withVPrefix_stripsV() {
    assertThat(HostVersionUtil.normalizeVersion("v1.2.3")).isEqualTo("1.2.3");
    assertThat(HostVersionUtil.normalizeVersion("v4.358.0")).isEqualTo("4.358.0");
    assertThat(HostVersionUtil.normalizeVersion("4.358.0")).isEqualTo("4.358.0");
  }
}
