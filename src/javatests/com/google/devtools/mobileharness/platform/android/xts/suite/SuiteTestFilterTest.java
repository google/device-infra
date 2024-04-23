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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SuiteTestFilterTest {

  @Test
  public void testParseFilter_module() {
    SuiteTestFilter filter = SuiteTestFilter.create("x86_64 module");
    assertThat(filter.abi()).hasValue("x86_64");
    assertThat(filter.moduleName()).isEqualTo("module");
    assertThat(filter.testName()).isEmpty();
    assertThat(filter.filterString()).isEqualTo("x86_64 module");
  }

  @Test
  public void testParseFilter() {
    SuiteTestFilter filter = SuiteTestFilter.create("x86 module class#method");
    assertThat(filter.abi()).hasValue("x86");
    assertThat(filter.moduleName()).isEqualTo("module");
    assertThat(filter.testName()).hasValue("class#method");
    assertThat(filter.filterString()).isEqualTo("x86 module class#method");
  }

  @Test
  public void testParseFilter_space() {
    SuiteTestFilter filter = SuiteTestFilter.create("x86 module    class#method");
    assertThat(filter.abi()).hasValue("x86");
    assertThat(filter.moduleName()).isEqualTo("module");
    assertThat(filter.testName()).hasValue("class#method");
  }

  @Test
  public void testParseFilter_moduleParam() {
    SuiteTestFilter filter = SuiteTestFilter.create("x86_64 module[instant]");

    assertThat(filter.abi()).hasValue("x86_64");
    assertThat(filter.moduleName()).isEqualTo("module[instant]");
    assertThat(filter.testName()).isEmpty();
    assertThat(filter.filterString()).isEqualTo("x86_64 module[instant]");
  }

  @Test
  public void testParseFilter_testNameWithSpace() {
    SuiteTestFilter filter =
        SuiteTestFilter.create("x86_64 module[instant] class#method[value_a value_b value_c]");
    assertThat(filter.abi()).hasValue("x86_64");
    assertThat(filter.moduleName()).isEqualTo("module[instant]");
    assertThat(filter.testName()).hasValue("class#method[value_a value_b value_c]");
    assertThat(filter.filterString())
        .isEqualTo("x86_64 module[instant] class#method[value_a value_b value_c]");

    SuiteTestFilter filterWithoutAbi =
        SuiteTestFilter.create("module[instant] class#method[value_a value_b value_c]");
    assertThat(filterWithoutAbi.abi()).isEmpty();
    assertThat(filterWithoutAbi.moduleName()).isEqualTo("module[instant]");
    assertThat(filterWithoutAbi.testName()).hasValue("class#method[value_a value_b value_c]");
    assertThat(filterWithoutAbi.filterString())
        .isEqualTo("module[instant] class#method[value_a value_b value_c]");
  }

  @Test
  public void testEquals() {
    SuiteTestFilter filter1 = SuiteTestFilter.create("x86 module class#method");
    SuiteTestFilter filter2 = SuiteTestFilter.create("x86 module class#method");
    assertThat(filter1.equals(filter2)).isTrue();
  }

  @Test
  public void testNotEquals() {
    SuiteTestFilter filter1 = SuiteTestFilter.create("0 x86 module class#method");
    SuiteTestFilter filter2 = SuiteTestFilter.create("x86 module class#method");
    assertThat(filter1.equals(filter2)).isFalse();
  }
}
