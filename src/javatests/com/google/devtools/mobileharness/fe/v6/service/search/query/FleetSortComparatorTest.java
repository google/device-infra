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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetSortComparator}. */
@RunWith(JUnit4.class)
public final class FleetSortComparatorTest {

  @Test
  public void signedIntegers_orderByNumericValueWithEmptyLast() {
    ImmutableList<String> unsorted =
        ImmutableList.of("9", "", "38", "-1", "100", "25", "-15", "0", "19");

    ImmutableList<String> sorted =
        ImmutableList.sortedCopyOf(FleetSortComparator.DECIMAL, unsorted);

    assertThat(sorted)
        .containsExactly("-15", "-1", "0", "9", "19", "25", "38", "100", "")
        .inOrder();
  }

  @Test
  public void decimalMode_ordersSignedSubUnitAndUnsignedDecimalsConsistently() {
    ImmutableList<String> unsorted =
        ImmutableList.of(
            "18.3", "0.2", "1.5", "+1.5", "-1.25", "0.15", "1.25", "0.0012", "-90", "+1.25",
            "18.25", "-1.5", "0", "0.015", "-1", "-0.05");

    ImmutableList<String> sorted =
        ImmutableList.sortedCopyOf(FleetSortComparator.DECIMAL, unsorted);

    assertThat(sorted)
        .containsExactly(
            "-90", "-1.5", "-1.25", "-1", "-0.05", "0", "0.0012", "0.015", "0.15", "0.2", "+1.25",
            "1.25", "+1.5", "1.5", "18.25", "18.3")
        .inOrder();
    assertThat(
            ImmutableList.sortedCopyOf(
                FleetSortComparator.DECIMAL, ImmutableList.of("0.9", "1.9", "0.10", "1.10")))
        .containsExactly("0.10", "0.9", "1.10", "1.9")
        .inOrder();
  }

  @Test
  public void unitSuffixedMeasurements_orderByNumericMagnitudeInDecimalMode() {
    ImmutableList<String> storage =
        ImmutableList.of(
            "107GB", "9.5GB", "209GB", "18.3GB", "17.58GB", "2.76GB", "2GB", "94.49GB", "18.25GB");
    ImmutableList<String> percentages =
        ImmutableList.of("98.35%", "9.5%", "85.47%", "9.25%", "5.2%", "55.53%");

    assertThat(ImmutableList.sortedCopyOf(FleetSortComparator.DECIMAL, storage))
        .containsExactly(
            "2GB", "2.76GB", "9.5GB", "17.58GB", "18.25GB", "18.3GB", "94.49GB", "107GB", "209GB")
        .inOrder();
    assertThat(ImmutableList.sortedCopyOf(FleetSortComparator.DECIMAL, percentages))
        .containsExactly("5.2%", "9.25%", "9.5%", "55.53%", "85.47%", "98.35%")
        .inOrder();
  }

  @Test
  public void lexicographicalMode_preservesFixedWidthHexPrefixOrder() {
    ImmutableList<String> macs =
        ImmutableList.of("1a:2b:3c", "09:2b:3c", "10:2b:3c", "0A:2b:3c", "0a:2b:3c");

    ImmutableList<String> sorted =
        ImmutableList.sortedCopyOf(FleetSortComparator.LEXICOGRAPHICAL, macs);

    assertThat(sorted)
        .containsExactly("09:2b:3c", "0A:2b:3c", "0a:2b:3c", "10:2b:3c", "1a:2b:3c")
        .inOrder();
  }

  @Test
  public void versionModeIpsAndNaturalIdentifiers_orderSegmentsNumerically() {
    ImmutableList<String> versions =
        ImmutableList.of("18.0", "9", "17.10", "17.4.1", "16", "17.4", "10", "17.5");
    ImmutableList<String> zeroAndOneMajorVersions = ImmutableList.of("1.10", "0.10", "1.9", "0.9");
    ImmutableList<String> letterSuffixedVersions =
        ImmutableList.of("17.10a", "17.9b", "17.10", "17.9a", "17.9");
    ImmutableList<String> multiValueVersionHeaders =
        ImmutableList.of("17.10, 18.0", "17.5", "17.4, 18.0");
    ImmutableList<String> kernelVersions =
        ImmutableList.of("5.10-android12", "5.15.104-android13", "5.4-android11");
    ImmutableList<String> ips =
        ImmutableList.of("10.10.0.1", "10.0.10.1", "10.0.2.1", "10.9.0.1", "10.0.9.1");
    ImmutableList<String> models = ImmutableList.of("Pixel 10", "pixel 8", "Pixel 9", "Pixel 7");
    ImmutableList<String> iccids =
        ImmutableList.of("89014103211118510720", "8901410321111851072", "89014103211118510719");

    assertThat(ImmutableList.sortedCopyOf(FleetSortComparator.VERSION, versions))
        .containsExactly("9", "10", "16", "17.4", "17.4.1", "17.5", "17.10", "18.0")
        .inOrder();
    assertThat(ImmutableList.sortedCopyOf(FleetSortComparator.VERSION, zeroAndOneMajorVersions))
        .containsExactly("0.9", "0.10", "1.9", "1.10")
        .inOrder();
    assertThat(ImmutableList.sortedCopyOf(FleetSortComparator.VERSION, letterSuffixedVersions))
        .containsExactly("17.9", "17.9a", "17.9b", "17.10", "17.10a")
        .inOrder();
    assertThat(ImmutableList.sortedCopyOf(FleetSortComparator.VERSION, multiValueVersionHeaders))
        .containsExactly("17.4, 18.0", "17.5", "17.10, 18.0")
        .inOrder();
    for (FleetSortComparator mode :
        ImmutableList.of(FleetSortComparator.DECIMAL, FleetSortComparator.VERSION)) {
      assertThat(ImmutableList.sortedCopyOf(mode, kernelVersions))
          .containsExactly("5.4-android11", "5.10-android12", "5.15.104-android13")
          .inOrder();
      assertThat(ImmutableList.sortedCopyOf(mode, ips))
          .containsExactly("10.0.2.1", "10.0.9.1", "10.0.10.1", "10.9.0.1", "10.10.0.1")
          .inOrder();
      assertThat(ImmutableList.sortedCopyOf(mode, models))
          .containsExactly("Pixel 7", "pixel 8", "Pixel 9", "Pixel 10")
          .inOrder();
      assertThat(ImmutableList.sortedCopyOf(mode, iccids))
          .containsExactly("8901410321111851072", "89014103211118510719", "89014103211118510720")
          .inOrder();
    }
  }

  @Test
  public void allComparatorModes_satisfyStrictTotalOrderContract() {
    ImmutableList<String> domain =
        ImmutableList.of(
            "",
            "(no value)",
            "-abc",
            "-90",
            "-1.5",
            "-1.25",
            "-1",
            "-0.0",
            "-0",
            "0",
            "+0",
            "00",
            "0.0",
            "0.0012",
            "0.015",
            "0.10",
            "0.15",
            "0.2",
            "0.9",
            "0.9.0",
            "0.10.0",
            "1",
            "1.10",
            "+1.25",
            "1.25",
            "+1.5",
            "1.5",
            "1.9",
            "2GB",
            "2.76GB",
            "5",
            "5.4-android11",
            "5.10-android12",
            "5.15.104-android13",
            "5a",
            "6",
            "09",
            "9",
            "+9",
            "9.25%",
            "9.5%",
            "10",
            "17.4",
            "17.4, 18.0",
            "17.4.1",
            "17.5",
            "17.9a",
            "17.10",
            "17.10a",
            "17.10, 18.0",
            "18.25",
            "18.25GB",
            "18.3",
            "18.3GB",
            "100",
            "09:aa",
            "0a:aa",
            "10:aa",
            "1a:aa",
            "device-0",
            "device-2",
            "device-10",
            "failed",
            "unknown",
            "Verizon",
            "verizon");

    for (FleetSortComparator mode : FleetSortComparator.values()) {
      ImmutableList<String> sorted = ImmutableList.sortedCopyOf(mode, domain);
      assertThat(sorted).hasSize(domain.size());
      assertThat(Iterables.getLast(sorted)).isEmpty();

      for (int i = 0; i < sorted.size(); i++) {
        assertThat(mode.compare(sorted.get(i), sorted.get(i))).isEqualTo(0);
        for (int j = i + 1; j < sorted.size(); j++) {
          assertThat(mode.compare(sorted.get(i), sorted.get(j))).isLessThan(0);
          assertThat(mode.compare(sorted.get(j), sorted.get(i))).isGreaterThan(0);
          for (int k = j + 1; k < sorted.size(); k++) {
            assertThat(mode.compare(sorted.get(i), sorted.get(k))).isLessThan(0);
          }
        }
      }
    }
  }
}
