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

package com.google.devtools.mobileharness.fe.v6.service.search.query.suggest;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies the raw search-bar text into exactly one suggestion shape and extracts its operands.
 *
 * <p>This class answers one question: what did the user mean by this string? It is purely lexical.
 * It knows the operator words and symbols of the search grammar but nothing about keys, values, or
 * entities, so one parse serves device and host search alike. Whether an extracted key token names
 * anything is decided afterwards by the corpus's {@link KeyVocabulary}.
 *
 * <p>Shapes are tried in a fixed order and the first match wins. Negated and emptiness forms come
 * before their positive counterparts because their markers are supersets ({@code is not} contains
 * {@code is}; {@code <key> is empty} matches the key-value shape too). The group-by prefix is
 * handled separately by {@link #groupByTerm} because it owns the whole input: a group-by that
 * matches no key yields nothing rather than falling through to a value search for the word "group".
 *
 * <p>The suggestion spec numbers its patterns; the mapping is: {@link IntentPattern#EMPTINESS}
 * pattern 5, {@link IntentPattern#KEY_VALUE_NEGATED} pattern 6, {@link
 * IntentPattern#KEY_WITH_OPERATOR} pattern 1 (operator typed, value pending), {@link
 * IntentPattern#KEY_VALUE} pattern 1, {@link IntentPattern#NEGATED_VALUE} pattern 7, {@link
 * IntentPattern#VALUE_LIST} pattern 2, and {@link IntentPattern#SINGLE_TOKEN} patterns 3 and 4
 * together. Group-by is pattern 0.
 */
public final class SuggestionIntentParser {

  /** The mutually exclusive shapes an input can take, in dispatch order. */
  public enum IntentPattern {
    /** {@code no <key>}, {@code has <key>}, {@code <key> is empty}, {@code <key> is not empty}. */
    EMPTINESS,
    /**
     * {@code <key> is not <value>}, {@code <key> != <value>}, {@code <key> doesn't include <v>}.
     */
    KEY_VALUE_NEGATED,
    /** {@code <key> is}, {@code <key>:}, {@code <key> is not}: an operator with no value yet. */
    KEY_WITH_OPERATOR,
    /** {@code <key> is <value>}, {@code <key>: <value>}, {@code <key> = <value>}. */
    KEY_VALUE,
    /** {@code not <value>}: a value to exclude, key undetermined. */
    NEGATED_VALUE,
    /** {@code <value1>, <value2>, ...}: several values, key undetermined. */
    VALUE_LIST,
    /** Anything else: one token, tried both as a key name and as a value. */
    SINGLE_TOKEN
  }

  /**
   * A classified input. Which fields carry meaning depends on the pattern: {@code key} for every
   * key-bearing pattern; {@code value} for {@code KEY_VALUE}, {@code KEY_VALUE_NEGATED}, {@code
   * NEGATED_VALUE} and {@code VALUE_LIST} (the whole text); {@code isEmpty} for {@code EMPTINESS};
   * {@code exclude} for {@code KEY_WITH_OPERATOR}; {@code values} for {@code VALUE_LIST}.
   */
  public record Intent(
      IntentPattern pattern,
      String key,
      String value,
      boolean isEmpty,
      boolean exclude,
      ImmutableList<String> values) {

    static Intent keyValue(String key, String value) {
      return new Intent(
          IntentPattern.KEY_VALUE, key.trim(), value.trim(), false, false, ImmutableList.of());
    }

    static Intent keyValueNegated(String key, String value) {
      return new Intent(
          IntentPattern.KEY_VALUE_NEGATED,
          key.trim(),
          value.trim(),
          false,
          true,
          ImmutableList.of());
    }

    static Intent emptiness(String key, boolean isEmpty) {
      return new Intent(
          IntentPattern.EMPTINESS, key.trim(), "", isEmpty, false, ImmutableList.of());
    }

    static Intent keyWithOperator(String key, boolean exclude) {
      return new Intent(
          IntentPattern.KEY_WITH_OPERATOR, key.trim(), "", false, exclude, ImmutableList.of());
    }

    static Intent negatedValue(String value) {
      return new Intent(
          IntentPattern.NEGATED_VALUE, "", value.trim(), false, true, ImmutableList.of());
    }

    static Intent valueList(String text, ImmutableList<String> values) {
      return new Intent(IntentPattern.VALUE_LIST, "", text, false, false, values);
    }

    static Intent singleToken() {
      return new Intent(IntentPattern.SINGLE_TOKEN, "", "", false, false, ImmutableList.of());
    }
  }

  // Longest first so "grouped by" is not consumed by the "group" prefix.
  private static final ImmutableList<String> GROUP_BY_PREFIXES =
      ImmutableList.of("grouped by", "group by", "groupby", "group");

  private static final String NEGATED_OPERATOR =
      "(?:is[ _]+not|isn't|are[ _]+not|aren't"
          + "|do(?:es)?[ _]+not[ _]+includes?|do(?:es)?n't[ _]+includes?)";
  private static final String POSITIVE_OPERATOR = "(?:is|are|includes?)";

  private static final Pattern EMPTY_NO = Pattern.compile("^no[ _]+(.+)$");
  private static final Pattern EMPTY_HAS = Pattern.compile("^has[ _]+(.+)$");
  private static final Pattern NON_EMPTY =
      Pattern.compile("^(.+?)[ _]+(?:is|are)[ _]+not[ _]+empty$");
  private static final Pattern NON_EMPTY_CONTRACTED =
      Pattern.compile("^(.+?)[ _]+(?:isn't|aren't)[ _]+empty$");
  private static final Pattern NON_EMPTY_BARE = Pattern.compile("^(.+?)[ _]+not[ _]+empty$");
  private static final Pattern IS_EMPTY = Pattern.compile("^(.+?)[ _]+(?:is|are)[ _]+empty$");
  private static final Pattern IS_EMPTY_BARE = Pattern.compile("^(.+?)[ _]+empty$");
  private static final Pattern KEY_VALUE_NEGATED =
      Pattern.compile("^(.+?)[ _]+" + NEGATED_OPERATOR + "[ _]+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern KEY_VALUE_NEGATED_SYMBOL =
      Pattern.compile("^(.+?)[ _]*!=[ _]*(.+)$");
  private static final Pattern KEY_NEGATED_OPERATOR_ONLY =
      Pattern.compile("^(.+?)[ _]+" + NEGATED_OPERATOR + "$", Pattern.CASE_INSENSITIVE);
  private static final Pattern KEY_NEGATED_SYMBOL_ONLY = Pattern.compile("^(.+?)[ _]*!=\\s*$");
  private static final Pattern KEY_OPERATOR_ONLY =
      Pattern.compile("^(.+?)[ _]+" + POSITIVE_OPERATOR + "$", Pattern.CASE_INSENSITIVE);
  private static final Pattern KEY_SYMBOL_ONLY = Pattern.compile("^(.+?)[ _]*[:=]\\s*$");
  private static final Pattern KEY_VALUE =
      Pattern.compile("^(.+?)[ _]+" + POSITIVE_OPERATOR + "[ _]+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern KEY_VALUE_SYMBOL = Pattern.compile("^(.+?)[ _]*[:=][ _]*(.+)$");
  private static final Pattern NEGATED_VALUE = Pattern.compile("^not\\s+(.+)$");

  /**
   * The text after a group-by prefix, or empty if the input does not start with one. A bare prefix
   * ({@code group by}) yields an empty term, meaning "offer the curated group-by candidates".
   */
  public static Optional<String> groupByTerm(String query) {
    String lower = Ascii.toLowerCase(query);
    for (String prefix : GROUP_BY_PREFIXES) {
      if (lower.equals(prefix)) {
        return Optional.of("");
      }
      if (lower.startsWith(prefix + " ")) {
        return Optional.of(query.substring(prefix.length()).trim());
      }
    }
    return Optional.empty();
  }

  /** Classifies a whitespace-normalized, non-empty query that is not a group-by. */
  public static Intent parse(String query) {
    String lower = Ascii.toLowerCase(query);

    Matcher m = EMPTY_NO.matcher(lower);
    if (m.matches()) {
      return Intent.emptiness(m.group(1), /* isEmpty= */ true);
    }
    m = EMPTY_HAS.matcher(lower);
    if (m.matches()) {
      return Intent.emptiness(m.group(1), /* isEmpty= */ false);
    }
    Optional<Matcher> nonEmpty = firstMatch(lower, NON_EMPTY, NON_EMPTY_CONTRACTED, NON_EMPTY_BARE);
    if (nonEmpty.isPresent()) {
      return Intent.emptiness(nonEmpty.get().group(1), /* isEmpty= */ false);
    }
    Optional<Matcher> empty = firstMatch(lower, IS_EMPTY, IS_EMPTY_BARE);
    if (empty.isPresent()) {
      return Intent.emptiness(empty.get().group(1), /* isEmpty= */ true);
    }

    m = KEY_VALUE_NEGATED.matcher(query);
    if (m.matches()) {
      return Intent.keyValueNegated(m.group(1), m.group(2));
    }
    m = KEY_VALUE_NEGATED_SYMBOL.matcher(query);
    if (m.matches()) {
      return Intent.keyValueNegated(m.group(1), m.group(2));
    }

    m = KEY_NEGATED_OPERATOR_ONLY.matcher(lower);
    if (m.matches()) {
      return Intent.keyWithOperator(m.group(1), /* exclude= */ true);
    }
    m = KEY_NEGATED_SYMBOL_ONLY.matcher(query);
    if (m.matches()) {
      return Intent.keyWithOperator(m.group(1), /* exclude= */ true);
    }
    m = KEY_OPERATOR_ONLY.matcher(lower);
    if (m.matches()) {
      return Intent.keyWithOperator(m.group(1), /* exclude= */ false);
    }
    m = KEY_SYMBOL_ONLY.matcher(query);
    if (m.matches()) {
      return Intent.keyWithOperator(m.group(1), /* exclude= */ false);
    }

    m = KEY_VALUE.matcher(query);
    if (m.matches()) {
      return Intent.keyValue(m.group(1), m.group(2));
    }
    m = KEY_VALUE_SYMBOL.matcher(query);
    if (m.matches()) {
      return Intent.keyValue(m.group(1), m.group(2));
    }

    m = NEGATED_VALUE.matcher(lower);
    if (m.matches()) {
      return Intent.negatedValue(m.group(1));
    }

    if (query.contains(",")) {
      ImmutableList<String> parts =
          Arrays.stream(query.split(",", -1))
              .map(String::trim)
              .filter(part -> !part.isEmpty())
              .collect(toImmutableList());
      return Intent.valueList(query, parts);
    }
    return Intent.singleToken();
  }

  private static Optional<Matcher> firstMatch(String input, Pattern... patterns) {
    return Arrays.stream(patterns)
        .map(pattern -> pattern.matcher(input))
        .filter(Matcher::matches)
        .findFirst();
  }

  private SuggestionIntentParser() {}
}
