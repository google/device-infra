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

import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugString;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMultimapMatchCondition;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utility class to create {@link Predicate} to match a string field of an entity based on the given
 * {@link StringMatchCondition} , {@link StringListMatchCondition} or {@link
 * StringMultimapMatchCondition}.
 */
public final class FilterUtils {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private FilterUtils() {}

  /**
   * Creates a {@link Predicate} to match a string field of an entity based on the given {@link
   * StringMatchCondition}.
   */
  public static <T> Predicate<T> createStringMatcher(
      StringMatchCondition condition, Function<T, String> stringExtractor) {
    try {
      switch (condition.getConditionCase()) {
        case INCLUDE:
          ImmutableSet<String> expectedValues =
              ImmutableSet.copyOf(
                  condition.getInclude().getExpectedList().stream()
                      .map(Ascii::toLowerCase)
                      .collect(toImmutableList()));
          return entity -> expectedValues.contains(toLowerCase(stringExtractor.apply(entity)));
        case MATCHES_REGEX:
          Pattern pattern = Pattern.compile(condition.getMatchesRegex().getRegex());
          return entity -> pattern.matcher(stringExtractor.apply(entity)).matches();
        case CONDITION_NOT_SET:
          break;
      }
      return entity -> true;
    } catch (RuntimeException e) {
      logger.atWarning().log(
          "Invalid StringMatchCondition [%s], cause=[%s]",
          shortDebugString(condition), shortDebugString(e));
      return entity -> false;
    }
  }

  /**
   * Creates a {@link Predicate} to match a string list field of an entity based on the given {@link
   * StringListMatchCondition}.
   *
   * <p>For all conditions, If the expected list is empty, the condition will always match.
   *
   * <p>For AnyMatch and NoneMatch, if Include is set, it is case-insensitive. If MatchesRegex is
   * set, it is case-sensitive. For SubsetMatch, it is case-insensitive.
   *
   * <p>E.g. condition { any_match { condition { include { expected: "a" expected: "b" } } } } will
   * match {"a"}, {"a", "c"}, {"A", "b"}, and {"B"}.
   *
   * <p>E.g. condition { any_match { condition { matches_regex { regex: "a.*" } } } will match
   * {"a"}, {"a", "c"} but not match {"A"}.
   *
   * <p>E.g. condition { none_match { condition { include { expected: "a" expected: "b" } } } } will
   * not match {"a"}, {"a", "c"}, {"A", "b"}, and {"B"}.
   *
   * <p>E.g. condition { none_match { condition { matches_regex { regex: "a.*" } } } will not match
   * {"a"}, {"a", "c"} but will match {"A"}.
   *
   * <p>E.g. condition { subset_match { expected: "a" expected: "b" } } will match {"a", "b"} and
   * {"A", "B", "c"} but not match {"a", "c"}.
   *
   * <p>E.g. condition { integer_match { equal: 2 } } will match {"a", "b"} but not match {"a"}.
   */
  public static <T> Predicate<T> createStringListMatcher(
      StringListMatchCondition condition, Function<T, ImmutableSet<String>> stringListExtractor) {
    try {
      switch (condition.getConditionCase()) {
        case ANY_MATCH:
          return createStringListMatcherForAnyMatch(
              condition.getAnyMatch().getCondition(), stringListExtractor);
        case NONE_MATCH:
          return createStringListMatcherForNoneMatch(
              condition.getNoneMatch().getCondition(), stringListExtractor);
        case SUBSET_MATCH:
          ImmutableSet<String> expectedValues =
              ImmutableSet.copyOf(
                  condition.getSubsetMatch().getExpectedList().stream()
                      .map(Ascii::toLowerCase)
                      .collect(toImmutableList()));
          return entity ->
              expectedValues.stream()
                  .allMatch(
                      value ->
                          stringListExtractor.apply(entity).stream()
                              .map(Ascii::toLowerCase)
                              .collect(toImmutableSet())
                              .contains(value));
        case LENGTH_MATCH:
          return createStringListMatcherForLengthMatch(
              condition.getLengthMatch(), stringListExtractor);
        case CONDITION_NOT_SET:
          break;
      }
      return entity -> true;
    } catch (RuntimeException e) {
      logger.atWarning().log(
          "Invalid StringListMatchCondition [%s], cause=[%s]", condition, shortDebugString(e));
      return entity -> false;
    }
  }

  /**
   * Creates a {@link Predicate} to match a string list field of an entity based on the given {@link
   * StringMatchCondition}.
   *
   * <p>For AnyMatch, if Include is set, it is case-insensitive. If MatchesRegex is set, it is
   * case-sensitive.
   *
   * <p>E.g. condition { any_match { condition { include { expected: "a" expected: "b" } } } } will
   * match {"a"}, {"a", "c"}, {"A", "b"}, and {"B"}.
   *
   * <p>E.g. condition { any_match { condition { matches_regex { regex: "a.*" } } } will match
   * {"a"}, {"a", "c"} but not match {"A"}.
   */
  private static <T> Predicate<T> createStringListMatcherForAnyMatch(
      StringMatchCondition condition, Function<T, ImmutableSet<String>> stringListExtractor) {
    switch (condition.getConditionCase()) {
      case INCLUDE:
        ImmutableSet<String> expectedValues =
            ImmutableSet.copyOf(
                condition.getInclude().getExpectedList().stream()
                    .map(Ascii::toLowerCase)
                    .collect(toImmutableList()));
        return entity ->
            stringListExtractor.apply(entity).stream()
                .anyMatch(value -> expectedValues.contains(toLowerCase(value)));
      case MATCHES_REGEX:
        Pattern pattern = Pattern.compile(condition.getMatchesRegex().getRegex());
        return entity ->
            stringListExtractor.apply(entity).stream()
                .anyMatch(value -> pattern.matcher(value).matches());
      case CONDITION_NOT_SET:
        break;
    }
    return entity -> true;
  }

  /**
   * Creates a {@link Predicate} to match a string list field of an entity based on the given {@link
   * StringMatchCondition}.
   *
   * <p>For NoneMatch, if Include is set, it is case-insensitive. If MatchesRegex is set, it is
   * case-sensitive.
   *
   * <p>E.g. condition { none_match { condition { include { expected: "a" expected: "b" } } } } will
   * not match {"a"}, {"a", "c"}, {"A", "b"}, and {"B"}.
   *
   * <p>E.g. condition { none_match { condition { matches_regex { regex: "a.*" } } } will not match
   * {"a"}, {"a", "c"} but will match {"A"}.
   */
  private static <T> Predicate<T> createStringListMatcherForNoneMatch(
      StringMatchCondition condition, Function<T, ImmutableSet<String>> stringListExtractor) {
    switch (condition.getConditionCase()) {
      case INCLUDE:
        ImmutableSet<String> expectedValues =
            ImmutableSet.copyOf(
                condition.getInclude().getExpectedList().stream()
                    .map(Ascii::toLowerCase)
                    .collect(toImmutableList()));
        return entity ->
            stringListExtractor.apply(entity).stream()
                .noneMatch(value -> expectedValues.contains(toLowerCase(value)));
      case MATCHES_REGEX:
        Pattern pattern = Pattern.compile(condition.getMatchesRegex().getRegex());
        return entity ->
            stringListExtractor.apply(entity).stream()
                .noneMatch(value -> pattern.matcher(value).matches());
      case CONDITION_NOT_SET:
        break;
    }
    return entity -> true;
  }

  /**
   * Creates a {@link Predicate} to match a string list field of an entity based on the given {@link
   * IntegerMatch}.
   *
   * <p>E.g. condition { integer_match { equal: 2 } } will match {"a", "b"} but not match {"a"}.
   *
   * <p>E.g. condition { integer_match { greater_than_or_equal: 2 } } will match {"a", "b"} and
   * {"a", "b", "c"}
   */
  private static <T> Predicate<T> createStringListMatcherForLengthMatch(
      IntegerMatch condition, Function<T, ImmutableSet<String>> stringListExtractor) {
    switch (condition.getConditionCase()) {
      case EQUAL:
        return entity ->
            stringListExtractor.apply(entity).size() == condition.getEqual().getValue();
      case GREATER_THAN_OR_EQUAL:
        return entity ->
            stringListExtractor.apply(entity).size()
                >= condition.getGreaterThanOrEqual().getMinValue();
      case CONDITION_NOT_SET:
        break;
    }
    return entity -> true;
  }

  /**
   * Creates a {@link Predicate} to match a string map field of an entity based on the given {@link
   * StringMultimapMatchCondition}.
   *
   * <p>If the key is not set or the value condition is not set, the match condition is invalid and
   * will not match any entity.
   *
   * <p>If the value condition is a default instance, the condition will match any entity.
   *
   * <p>E.g. condition { key: "pool" } will not match any entity.
   *
   * <p>E.g. condition { key: "pool" value_condition { any_match { condition { include { expected:
   * "shared" expected: "private" } } } } } will match {"pool": "shared"}, {"pool": "private"},
   * {"pool": "shared", "label": "test"}.
   *
   * <p>E.g. condition { key: "pool" value_condition { none_match { condition { include { expected:
   * "shared" expected: "private" } } } } } will not match {"pool": "shared"}, {"pool": "private"},
   * {"pool": "shared", "label": "test"}.
   *
   * <p>E.g. condition { key: "pool" value_condition { subset_match { expected: "shared" expected:
   * "private" } } } will match {"pool": "shared", "pool": "private"} but not match {"pool":
   * "shared"}
   *
   * <p>E.g. condition { key: "pool" value_condition { length_match { equal: 0 } } } will match
   * {"label": "test"} but not match {"pool": "shared"}.
   */
  public static <T> Predicate<T> createStringMultimapMatcher(
      StringMultimapMatchCondition condition,
      Function<T, ImmutableListMultimap<String, String>> stringMapExtractor) {
    if (condition.getKey().isEmpty() || !condition.hasValueCondition()) {
      return entity -> false;
    }
    try {
      return entity ->
          createStringListMatcher(
                  condition.getValueCondition(),
                  value ->
                      stringMapExtractor.apply(entity).entries().stream()
                          .filter(
                              entry -> Ascii.equalsIgnoreCase(entry.getKey(), condition.getKey()))
                          .map(Entry::getValue)
                          .collect(toImmutableSet()))
              .test(
                  stringMapExtractor.apply(entity).entries().stream()
                      .filter(entry -> Ascii.equalsIgnoreCase(entry.getKey(), condition.getKey()))
                      .map(Entry::getValue)
                      .collect(toImmutableSet()));
    } catch (RuntimeException e) {
      logger.atWarning().log(
          "Invalid StringMultimapMatchCondition [%s], cause=[%s]", condition, shortDebugString(e));
      return entity -> false;
    }
  }
}
