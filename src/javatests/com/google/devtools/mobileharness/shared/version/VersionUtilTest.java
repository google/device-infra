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

package com.google.devtools.mobileharness.shared.version;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.shared.version.proto.VersionProto.BuildVersion;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VersionUtilTest {

  private static final Version LAB_VERSION = new Version(4, 268, 0);

  @Test
  public void formatVersionString_fullBuildVersion() {
    assertThat(
            VersionUtil.formatVersionString(
                LAB_VERSION,
                Optional.of(
                    BuildVersion.newBuilder()
                        .setGoogleVersion(761411824L)
                        .setKokoroBuild(1148L)
                        .setGithubBranch("main")
                        .setGithubVersion("fece030c657cddecc2cf7a122aa1fc4976dfed9c")
                        .build())))
        .isEqualTo(
            "lab-4.268.0 cl/761411824 kokoro-1148 branch-main"
                + " github-fece030c657cddecc2cf7a122aa1fc4976dfed9c");
  }

  @Test
  public void formatVersionString_partialBuildVersion() {
    assertThat(
            VersionUtil.formatVersionString(
                LAB_VERSION,
                Optional.of(BuildVersion.newBuilder().setGoogleVersion(761411824L).build())))
        .isEqualTo("lab-4.268.0 cl/761411824");
  }

  @Test
  public void formatVersionString_noBuildVersion() {
    assertThat(VersionUtil.formatVersionString(LAB_VERSION, Optional.empty()))
        .isEqualTo("lab-4.268.0");
    assertThat(
            VersionUtil.formatVersionString(
                LAB_VERSION, Optional.of(BuildVersion.getDefaultInstance())))
        .isEqualTo("lab-4.268.0");
  }

  @Test
  public void getVersionString() {
    assertThat(VersionUtil.getVersionString()).startsWith("lab-" + Version.LAB_VERSION);
  }
}
