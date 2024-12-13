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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch.Equal;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch.GreaterThanOrEqual;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition.AnyMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition.NoneMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition.SubsetMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition.Include;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition.MatchesRegex;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMultimapMatchCondition;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FilterUtilsTest {
  @Test
  public void createStringMatcher_empty() {
    StringMatchCondition condition = StringMatchCondition.getDefaultInstance();
    Predicate<String> matcher = FilterUtils.createStringMatcher(condition, s -> s);
    assertThat(matcher.test("a")).isTrue();
    assertThat(matcher.test("b")).isTrue();
    assertThat(matcher.test("A")).isTrue();
    assertThat(matcher.test("B")).isTrue();
    assertThat(matcher.test("c")).isTrue();
  }

  @Test
  public void createStringMatcher_include() {
    StringMatchCondition condition =
        StringMatchCondition.newBuilder()
            .setInclude(Include.newBuilder().addExpected("a").addExpected("b"))
            .build();
    Predicate<String> matcher = FilterUtils.createStringMatcher(condition, s -> s);
    assertThat(matcher.test("a")).isTrue();
    assertThat(matcher.test("b")).isTrue();
    assertThat(matcher.test("A")).isTrue();
    assertThat(matcher.test("B")).isTrue();
    assertThat(matcher.test("c")).isFalse();
  }

  @Test
  public void createStringMatcher_matchesRegex() {
    StringMatchCondition condition =
        StringMatchCondition.newBuilder()
            .setMatchesRegex(MatchesRegex.newBuilder().setRegex("a.*"))
            .build();
    Predicate<String> matcher = FilterUtils.createStringMatcher(condition, s -> s);
    assertThat(matcher.test("a")).isTrue();
    assertThat(matcher.test("ab")).isTrue();
    assertThat(matcher.test("A")).isFalse();
    assertThat(matcher.test("c")).isFalse();
  }

  @Test
  public void createStringListMatcher_empty() {
    StringListMatchCondition condition = StringListMatchCondition.getDefaultInstance();
    Predicate<ImmutableSet<String>> matcher =
        FilterUtils.createStringListMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableSet.of("a"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("a", "c"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("A"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("c"))).isTrue();
  }

  @Test
  public void createStringListMatcher_anyMatch_include() {
    StringListMatchCondition condition =
        StringListMatchCondition.newBuilder()
            .setAnyMatch(
                AnyMatch.newBuilder()
                    .setCondition(
                        StringMatchCondition.newBuilder()
                            .setInclude(Include.newBuilder().addExpected("a").addExpected("b"))))
            .build();
    Predicate<ImmutableSet<String>> matcher =
        FilterUtils.createStringListMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableSet.of("a"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("a", "c"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("A", "b"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("B"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("c"))).isFalse();
  }

  @Test
  public void createStringListMatcher_anyMatch_matchesRegex() {
    StringListMatchCondition condition =
        StringListMatchCondition.newBuilder()
            .setAnyMatch(
                AnyMatch.newBuilder()
                    .setCondition(
                        StringMatchCondition.newBuilder()
                            .setMatchesRegex(MatchesRegex.newBuilder().setRegex("a.*"))))
            .build();
    Predicate<ImmutableSet<String>> matcher =
        FilterUtils.createStringListMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableSet.of("a"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("a", "c"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("A"))).isFalse();
    assertThat(matcher.test(ImmutableSet.of("c"))).isFalse();
  }

  @Test
  public void createStringListMatcher_noneMatch_include() {
    StringListMatchCondition condition =
        StringListMatchCondition.newBuilder()
            .setNoneMatch(
                NoneMatch.newBuilder()
                    .setCondition(
                        StringMatchCondition.newBuilder()
                            .setInclude(Include.newBuilder().addExpected("a").addExpected("b"))))
            .build();
    Predicate<ImmutableSet<String>> matcher =
        FilterUtils.createStringListMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableSet.of("a"))).isFalse();
    assertThat(matcher.test(ImmutableSet.of("a", "c"))).isFalse();
    assertThat(matcher.test(ImmutableSet.of("A", "b"))).isFalse();
    assertThat(matcher.test(ImmutableSet.of("B"))).isFalse();
    assertThat(matcher.test(ImmutableSet.of("c"))).isTrue();
  }

  @Test
  public void createStringListMatcher_noneMatch_matchesRegex() {
    StringListMatchCondition condition =
        StringListMatchCondition.newBuilder()
            .setNoneMatch(
                NoneMatch.newBuilder()
                    .setCondition(
                        StringMatchCondition.newBuilder()
                            .setMatchesRegex(MatchesRegex.newBuilder().setRegex("a.*"))))
            .build();
    Predicate<ImmutableSet<String>> matcher =
        FilterUtils.createStringListMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableSet.of("a"))).isFalse();
    assertThat(matcher.test(ImmutableSet.of("a", "c"))).isFalse();
    assertThat(matcher.test(ImmutableSet.of("A"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("c"))).isTrue();
  }

  @Test
  public void createStringListMatcher_subsetMatch() {
    StringListMatchCondition condition =
        StringListMatchCondition.newBuilder()
            .setSubsetMatch(SubsetMatch.newBuilder().addExpected("a").addExpected("b"))
            .build();
    Predicate<ImmutableSet<String>> matcher =
        FilterUtils.createStringListMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableSet.of("a", "b"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("A", "B", "c"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("a"))).isFalse();
    assertThat(matcher.test(ImmutableSet.of("c"))).isFalse();
  }

  @Test
  public void createStringListMatcher_lengthMatch_equal() {
    StringListMatchCondition condition =
        StringListMatchCondition.newBuilder()
            .setLengthMatch(IntegerMatch.newBuilder().setEqual(Equal.newBuilder().setValue(2)))
            .build();
    Predicate<ImmutableSet<String>> matcher =
        FilterUtils.createStringListMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableSet.of("a", "b"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("a"))).isFalse();
  }

  @Test
  public void createStringListMatcher_lengthMatch_greaterThanOrEqual() {
    StringListMatchCondition condition =
        StringListMatchCondition.newBuilder()
            .setLengthMatch(
                IntegerMatch.newBuilder()
                    .setGreaterThanOrEqual(GreaterThanOrEqual.newBuilder().setMinValue(2).build()))
            .build();
    Predicate<ImmutableSet<String>> matcher =
        FilterUtils.createStringListMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableSet.of("a", "b"))).isTrue();
    assertThat(matcher.test(ImmutableSet.of("a"))).isFalse();
  }

  @Test
  public void createStringMultimapMatcher_empty() {
    StringMultimapMatchCondition condition = StringMultimapMatchCondition.getDefaultInstance();
    Predicate<ImmutableListMultimap<String, String>> matcher =
        FilterUtils.createStringMultimapMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableListMultimap.of("a", "b"))).isFalse();
    assertThat(matcher.test(ImmutableListMultimap.of("a", "b", "c", "d"))).isFalse();
    assertThat(matcher.test(ImmutableListMultimap.of("A", "B"))).isFalse();
    assertThat(matcher.test(ImmutableListMultimap.of("c", "d"))).isFalse();
  }

  @Test
  public void createStringMultimapMatcher_emptyCondition() {
    StringMultimapMatchCondition condition =
        StringMultimapMatchCondition.newBuilder().setKey("pool").build();
    Predicate<ImmutableListMultimap<String, String>> matcher =
        FilterUtils.createStringMultimapMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableListMultimap.of("pool", "shared"))).isFalse();
    assertThat(matcher.test(ImmutableListMultimap.of("pool", "shared", "label", "test"))).isFalse();
  }

  @Test
  public void createStringMultimapMatcher_anyMatch_include() {
    StringMultimapMatchCondition condition =
        StringMultimapMatchCondition.newBuilder()
            .setKey("pool")
            .setValueCondition(
                StringListMatchCondition.newBuilder()
                    .setAnyMatch(
                        AnyMatch.newBuilder()
                            .setCondition(
                                StringMatchCondition.newBuilder()
                                    .setInclude(
                                        Include.newBuilder()
                                            .addExpected("shared")
                                            .addExpected("private")))))
            .build();
    Predicate<ImmutableListMultimap<String, String>> matcher =
        FilterUtils.createStringMultimapMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableListMultimap.of("pool", "shared"))).isTrue();
    assertThat(matcher.test(ImmutableListMultimap.of("POOL", "private"))).isTrue();
    assertThat(matcher.test(ImmutableListMultimap.of("Pool", "shared", "label", "test"))).isTrue();
    assertThat(matcher.test(ImmutableListMultimap.of("pool", "private", "label", "test"))).isTrue();
  }

  @Test
  public void createStringMultimapMatcher_lengthMatch_equal() {
    StringMultimapMatchCondition condition =
        StringMultimapMatchCondition.newBuilder()
            .setKey("pool")
            .setValueCondition(
                StringListMatchCondition.newBuilder()
                    .setLengthMatch(
                        IntegerMatch.newBuilder().setEqual(Equal.newBuilder().setValue(0))))
            .build();
    Predicate<ImmutableListMultimap<String, String>> matcher =
        FilterUtils.createStringMultimapMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableListMultimap.of("pool", "shared"))).isFalse();
    assertThat(matcher.test(ImmutableListMultimap.of("POOL", "shared", "label", "test"))).isFalse();
    assertThat(matcher.test(ImmutableListMultimap.of("label", "test"))).isTrue();

    condition =
        StringMultimapMatchCondition.newBuilder()
            .setKey("pool")
            .setValueCondition(
                StringListMatchCondition.newBuilder()
                    .setLengthMatch(
                        IntegerMatch.newBuilder().setEqual(Equal.newBuilder().setValue(2))))
            .build();
    matcher = FilterUtils.createStringMultimapMatcher(condition, s -> s);
    assertThat(matcher.test(ImmutableListMultimap.of("pool", "shared"))).isFalse();
    assertThat(matcher.test(ImmutableListMultimap.of("POOL", "shared", "pool", "private")))
        .isTrue();
    assertThat(matcher.test(ImmutableListMultimap.of("label", "test"))).isFalse();
  }
}
