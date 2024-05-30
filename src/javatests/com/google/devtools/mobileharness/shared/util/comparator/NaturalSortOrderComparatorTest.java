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

package com.google.devtools.mobileharness.shared.util.comparator;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NaturalSortOrderComparatorTest {

  @Test
  public void compare() {
    assertThat(
            ImmutableList.sortedCopyOf(
                new NaturalSortOrderComparator(),
                ImmutableList.of(
                    "", "1", "2", "10", "a1", "a2", "a10", "a1a", "a2a", "a10a", "a1ab", "a02ab",
                    "a002a", "", "a2a1", "a2a001", "a002a1")))
        .containsExactly(
            "", "", "1", "2", "10", "a1", "a1a", "a1ab", "a2", "a2a", "a002a", "a2a1", "a2a001",
            "a002a1", "a02ab", "a10", "a10a")
        .inOrder();
  }
}
