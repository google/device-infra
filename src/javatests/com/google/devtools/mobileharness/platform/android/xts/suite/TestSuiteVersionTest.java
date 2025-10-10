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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestSuiteVersionTest {

  @Test
  public void create_fromValidVersionString_success() {
    assertThat(TestSuiteVersion.create("16.1_r1")).isEqualTo(TestSuiteVersion.create(16, 1, 0, 1));
    assertThat(TestSuiteVersion.create("16_r1")).isEqualTo(TestSuiteVersion.create(16, 0, 0, 1));
    assertThat(TestSuiteVersion.create("8.0_r26")).isEqualTo(TestSuiteVersion.create(8, 0, 0, 26));
    assertThat(TestSuiteVersion.create("4.0.3_r4")).isEqualTo(TestSuiteVersion.create(4, 0, 3, 4));
    assertThat(TestSuiteVersion.create("10_R2")).isEqualTo(TestSuiteVersion.create(10, 0, 0, 2));
    assertThat(TestSuiteVersion.create("6.0")).isEqualTo(TestSuiteVersion.create(6, 0, 0, 0));
    assertThat(TestSuiteVersion.create("16.1")).isEqualTo(TestSuiteVersion.create(16, 1, 0, 0));
  }

  @Test
  public void create_fromInvalidVersionString_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> TestSuiteVersion.create("16.1_2"));
    assertThrows(IllegalArgumentException.class, () -> TestSuiteVersion.create("1.2.3.4_r1"));
    assertThrows(IllegalArgumentException.class, () -> TestSuiteVersion.create(""));
    assertThrows(IllegalArgumentException.class, () -> TestSuiteVersion.create("_"));
    assertThrows(NumberFormatException.class, () -> TestSuiteVersion.create("_r1"));
    assertThrows(NumberFormatException.class, () -> TestSuiteVersion.create("r1"));
    assertThrows(NumberFormatException.class, () -> TestSuiteVersion.create("1.2_rX"));
    assertThrows(NumberFormatException.class, () -> TestSuiteVersion.create("16.1_r"));
  }

  @Test
  public void compareTo_success() {
    TestSuiteVersion version10r1 = TestSuiteVersion.create(10, 0, 0, 1);
    TestSuiteVersion version9r2 = TestSuiteVersion.create(9, 0, 0, 2);
    TestSuiteVersion version10dot1r1 = TestSuiteVersion.create(10, 1, 0, 1);
    TestSuiteVersion version10dot0dot1r1 = TestSuiteVersion.create(10, 0, 1, 1);
    TestSuiteVersion version10r2 = TestSuiteVersion.create(10, 0, 0, 2);

    assertThat(version10r1).isGreaterThan(version9r2);
    assertThat(version10dot1r1).isGreaterThan(version10r1);
    assertThat(version10dot0dot1r1).isGreaterThan(version10r1);
    assertThat(version10r2).isGreaterThan(version10r1);
    assertThat(version10r1).isEquivalentAccordingToCompareTo(TestSuiteVersion.create(10, 0, 0, 1));
  }
}
