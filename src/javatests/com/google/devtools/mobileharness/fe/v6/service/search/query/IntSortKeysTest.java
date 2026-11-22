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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link IntSortKeys}. */
@RunWith(JUnit4.class)
public final class IntSortKeysTest {

  @Test
  public void of_ordersIntegersNumericallyThenTextAfterTheEmptyValue() {
    ImmutableList<String> values =
        ImmutableList.of(
            "10000", "abc", "5", "", "-3", " 7 ", "0", "2147483647", "-2147483648", "99999999999");

    assertThat(ImmutableList.sortedCopyOf(comparing(IntSortKeys::of), values))
        .containsExactly(
            "", "-2147483648", "-3", "0", "5", " 7 ", "10000", "2147483647", "99999999999", "abc")
        .inOrder();
  }

  @Test
  public void of_keepsTheEmptyKeyAndMapsEqualIntegersToOneKey() {
    assertThat(IntSortKeys.of("")).isEmpty();
    assertThat(IntSortKeys.of(" 42")).isEqualTo(IntSortKeys.of("42"));
  }
}
