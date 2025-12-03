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

package com.google.devtools.mobileharness.fe.v6.service.host.provider;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HostReleaseInfoTest {

  @Test
  public void componentInfo_build() {
    Instant now = Instant.now();
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder()
            .setVersion("1.0")
            .setStatus("OK")
            .setUpdateTime(now)
            .build();
    assertThat(componentInfo.version()).hasValue("1.0");
    assertThat(componentInfo.status()).hasValue("OK");
    assertThat(componentInfo.updateTime()).hasValue(now);
  }

  @Test
  public void hostReleaseInfo_build() {
    HostReleaseInfo.ComponentInfo componentInfo =
        HostReleaseInfo.ComponentInfo.builder().setVersion("1.0").build();
    HostReleaseInfo hostReleaseInfo =
        HostReleaseInfo.builder()
            .setLabServerReleaseInfo(Optional.of(componentInfo))
            .setDaemonServerReleaseInfo(Optional.of(componentInfo))
            .setLabType(Optional.of("FOO_LAB"))
            .build();

    assertThat(hostReleaseInfo.labServerReleaseInfo()).hasValue(componentInfo);
    assertThat(hostReleaseInfo.daemonServerReleaseInfo()).hasValue(componentInfo);
    assertThat(hostReleaseInfo.labType()).hasValue("FOO_LAB");
  }

  @Test
  public void hostReleaseInfo_build_defaultEmpty() {
    HostReleaseInfo hostReleaseInfo = HostReleaseInfo.builder().build();

    assertThat(hostReleaseInfo.labServerReleaseInfo()).isEmpty();
    assertThat(hostReleaseInfo.daemonServerReleaseInfo()).isEmpty();
    assertThat(hostReleaseInfo.labType()).isEmpty();
  }
}
