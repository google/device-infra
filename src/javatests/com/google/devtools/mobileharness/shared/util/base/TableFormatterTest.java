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

package com.google.devtools.mobileharness.shared.util.base;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TableFormatterTest {

  @Test
  public void displayTable() {
    assertThat(
            TableFormatter.displayTable(
                ImmutableList.of(
                    ImmutableList.of("CA", "CB", "CC", "CD"),
                    ImmutableList.of("ABCDE", "N/A", "Hello, World!", "Foo"))))
        .isEqualTo("CA     CB   CC             CD\nABCDE  N/A  Hello, World!  Foo");
  }
}
