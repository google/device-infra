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

package com.google.devtools.deviceaction.common.schemas;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VersionTest {
  @Test
  public void of_expectedResult() {
    assertThat(Version.of("2.1.1").fullVersion()).isEqualTo("2.1.1");
    assertThat(Version.of("2.1.1-alpha").fullVersion()).isEqualTo("2.1.1-alpha");
    assertThrows(IllegalArgumentException.class, () -> Version.of("xx.2"));
    assertThrows(IllegalArgumentException.class, () -> Version.of(""));
  }

  @Test
  public void compareTo_expectedResult() {
    assertThat(Version.of("1.1.3")).isLessThan(Version.of("2.0.0"));
    assertThat(Version.of("1.0.0")).isLessThan(Version.of("1.1.0"));
    assertThat(Version.of("1.0.0")).isLessThan(Version.of("1.1.3"));
    assertThat(Version.of("1.0.0-alpha")).isLessThan(Version.of("1.0.0"));
    assertThat(Version.of("1.0.0-beta.11")).isLessThan(Version.of("1.0.0-beta.2"));
    assertThat(Version.of("1.0.0-beta.11")).isLessThan(Version.of("1.0.0-rc.1"));
  }
}
