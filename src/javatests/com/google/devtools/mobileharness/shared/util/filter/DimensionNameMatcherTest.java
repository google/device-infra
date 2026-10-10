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

package com.google.devtools.mobileharness.shared.util.filter;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DimensionNameMatcherTest {

  @Test
  public void matches_ignoresAsciiCase() {
    DimensionNameMatcher matcher = DimensionNameMatcher.of(ImmutableList.of("Host_Name"));

    assertThat(matcher.matches("host_name")).isTrue();
    assertThat(matcher.matches("HOST_NAME")).isTrue();
    assertThat(matcher.matches("hostname")).isFalse();
  }

  @Test
  public void matches_differentLengthNeverMatches() {
    DimensionNameMatcher matcher = DimensionNameMatcher.of(ImmutableList.of("id"));

    assertThat(matcher.matches("id")).isTrue();
    assertThat(matcher.matches("i")).isFalse();
    assertThat(matcher.matches("ids")).isFalse();
    assertThat(matcher.matches("a_much_longer_name")).isFalse();
    assertThat(matcher.matches("")).isFalse();
  }

  @Test
  public void matches_severalNamesOfSameAndDifferentLengths() {
    DimensionNameMatcher matcher =
        DimensionNameMatcher.of(ImmutableList.of("pool", "model", "label", "id"));

    assertThat(matcher.matches("POOL")).isTrue();
    assertThat(matcher.matches("model")).isTrue();
    assertThat(matcher.matches("Label")).isTrue();
    assertThat(matcher.matches("id")).isTrue();
    assertThat(matcher.matches("modem")).isFalse();
  }

  @Test
  public void of_emptyNamesMatchesNothing() {
    DimensionNameMatcher matcher = DimensionNameMatcher.of(ImmutableList.of());

    assertThat(matcher.matchesNothing()).isTrue();
    assertThat(matcher.matches("host_name")).isFalse();
    assertThat(matcher.filter(ImmutableList.of(dimension("host_name", "h")))).isEmpty();
  }

  @Test
  public void filter_keepsMatchingDimensionsInOrder() {
    DimensionNameMatcher matcher = DimensionNameMatcher.of(ImmutableList.of("host_name", "pool"));
    DeviceDimension pool = dimension("Pool", "shared");
    DeviceDimension host = dimension("host_name", "h1");
    DeviceDimension secondHost = dimension("HOST_NAME", "h2");

    assertThat(
            matcher.filter(
                ImmutableList.of(
                    pool, dimension("model", "m"), host, dimension("id", "x"), secondHost)))
        .containsExactly(pool, host, secondHost)
        .inOrder();
  }

  @Test
  public void filter_noMatchReturnsEmpty() {
    DimensionNameMatcher matcher = DimensionNameMatcher.of(ImmutableList.of("host_name"));

    assertThat(matcher.filter(ImmutableList.of(dimension("model", "m"), dimension("id", "x"))))
        .isEmpty();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
