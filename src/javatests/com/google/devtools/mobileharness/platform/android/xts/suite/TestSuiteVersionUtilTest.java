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
public final class TestSuiteVersionUtilTest {

  @Test
  public void parse_fromValidVersionString_success() {
    assertThat(TestSuiteVersionUtil.parse("16.1_r1"))
        .isEqualTo(TestSuiteVersion.create(16, 1, 0, 1));
    assertThat(TestSuiteVersionUtil.parse("16_r1")).isEqualTo(TestSuiteVersion.create(16, 0, 0, 1));
    assertThat(TestSuiteVersionUtil.parse("8.0_r26"))
        .isEqualTo(TestSuiteVersion.create(8, 0, 0, 26));
    assertThat(TestSuiteVersionUtil.parse("4.0.3_r4"))
        .isEqualTo(TestSuiteVersion.create(4, 0, 3, 4));
    assertThat(TestSuiteVersionUtil.parse("10_R2")).isEqualTo(TestSuiteVersion.create(10, 0, 0, 2));
    assertThat(TestSuiteVersionUtil.parse("6.0")).isEqualTo(TestSuiteVersion.create(6, 0, 0, 0));
    assertThat(TestSuiteVersionUtil.parse("16.1")).isEqualTo(TestSuiteVersion.create(16, 1, 0, 0));
    assertThat(TestSuiteVersionUtil.parse("15_sts-r46"))
        .isEqualTo(TestSuiteVersion.create(15, 0, 0, 46));
  }

  @Test
  public void parse_fromInvalidVersionString_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> TestSuiteVersionUtil.parse(null));
    assertThrows(IllegalArgumentException.class, () -> TestSuiteVersionUtil.parse("16.1_2"));
    assertThrows(IllegalArgumentException.class, () -> TestSuiteVersionUtil.parse("1.2.3.4_r1"));
    assertThrows(IllegalArgumentException.class, () -> TestSuiteVersionUtil.parse(""));
    assertThrows(IllegalArgumentException.class, () -> TestSuiteVersionUtil.parse("_"));
    assertThrows(NumberFormatException.class, () -> TestSuiteVersionUtil.parse("_r1"));
    assertThrows(NumberFormatException.class, () -> TestSuiteVersionUtil.parse("r1"));
    assertThrows(NumberFormatException.class, () -> TestSuiteVersionUtil.parse("1.2_rX"));
    assertThrows(NumberFormatException.class, () -> TestSuiteVersionUtil.parse("16.1_r"));
  }
}
