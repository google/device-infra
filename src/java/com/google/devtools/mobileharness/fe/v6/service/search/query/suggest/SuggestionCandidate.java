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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;

import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestion;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A suggestion under construction: the proto built so far, the key it concerns, and the bookkeeping
 * the ranker needs to order it and to fill in its displayed count.
 *
 * <p>Pattern generators create candidates through the named factories below, which encode the three
 * ways a suggestion relates to a count:
 *
 * <ul>
 *   <li>{@link #valueCondition}: a concrete {@code <key> is <value>} row whose count the ranker
 *       computes under the current filters, because counting every candidate up front is wasteful
 *       when most fall below the result cap.
 *   <li>{@link #presetCondition}: a row whose generator already knows the count (emptiness and
 *       multi-value rows, which count sets rather than single values).
 *   <li>{@link #uncountedCondition}, {@link #keyOnly}, {@link #groupBy}: rows that never carry a
 *       record count (a cold long-tail filter, a picker affordance, a grouping with a group count).
 * </ul>
 *
 * <p>The key is a {@link KeyDescriptor} produced by the entity's vocabulary rather than a bare id,
 * so a candidate cannot exist for a key the vocabulary does not describe; the ranker re-checks this
 * as a safety net.
 */
final class SuggestionCandidate {

  /** What clicking the suggestion does, which also decides how ranking treats its count. */
  enum Kind {
    /** Applies or stages a filter condition. */
    CONDITION,
    /** Opens the value picker for a key. */
    KEY,
    /** Adds a group-by key. */
    GROUP_BY
  }

  /** How the displayed count is obtained. */
  enum Counting {
    /** The ranker computes it from {@link #valueLower()} under the current scope. */
    DEFERRED,
    /** The generator computed it; the ranker only displays it. */
    PRESET,
    /** The row never carries a record count. */
    NONE
  }

  private final Kind kind;
  private final KeyDescriptor key;
  private final double tier;
  private final FleetSuggestion.Builder proto;
  private final String mainText;
  private final Counting counting;
  private final String valueLower;
  private final boolean exclude;
  private final boolean inChip;
  private int rankCount;
  private final int groupRank;
  private final boolean overMax;

  @Nullable private Integer count;
  private String countPrefix = "";

  private SuggestionCandidate(
      Kind kind,
      KeyDescriptor key,
      double tier,
      FleetSuggestion.Builder proto,
      Counting counting,
      String valueLower,
      boolean exclude,
      boolean inChip,
      int rankCount,
      @Nullable Integer count,
      int groupRank,
      boolean overMax) {
    this.kind = kind;
    this.key = checkNotNull(key);
    this.tier = tier;
    this.proto = checkNotNull(proto);
    this.mainText = proto.getMainTextList().stream().map(TextSegment::getText).collect(joining());
    this.counting = counting;
    this.valueLower = valueLower;
    this.exclude = exclude;
    this.inChip = inChip;
    this.rankCount = rankCount;
    this.count = count;
    this.groupRank = groupRank;
    this.overMax = overMax;
  }

  /**
   * A {@code <key> is <value>} row. {@code globalCount} is the value's fleet-wide count, used for
   * ranking until the ranker computes the count under the current filters.
   */
  static SuggestionCandidate valueCondition(
      KeyDescriptor key,
      double tier,
      FleetSuggestion.Builder proto,
      String valueLower,
      boolean exclude,
      boolean inChip,
      int globalCount) {
    return new SuggestionCandidate(
        Kind.CONDITION,
        key,
        tier,
        proto,
        Counting.DEFERRED,
        valueLower,
        exclude,
        inChip,
        globalCount,
        /* count= */ null,
        /* groupRank= */ 0,
        /* overMax= */ false);
  }

  /** A condition whose count is already known; a null count means "show no number". */
  static SuggestionCandidate presetCondition(
      KeyDescriptor key,
      double tier,
      FleetSuggestion.Builder proto,
      @Nullable Integer count,
      int rankCount) {
    return new SuggestionCandidate(
        Kind.CONDITION,
        key,
        tier,
        proto,
        Counting.PRESET,
        "",
        false,
        false,
        rankCount,
        count,
        /* groupRank= */ 0,
        /* overMax= */ false);
  }

  /** A ready-to-apply condition on a cold long-tail key, which has no index to count against. */
  static SuggestionCandidate uncountedCondition(
      KeyDescriptor key, double tier, FleetSuggestion.Builder proto) {
    return new SuggestionCandidate(
        Kind.CONDITION,
        key,
        tier,
        proto,
        Counting.NONE,
        "",
        false,
        false,
        /* rankCount= */ 0,
        /* count= */ null,
        /* groupRank= */ 0,
        /* overMax= */ false);
  }

  /** A picker affordance for a key. */
  static SuggestionCandidate keyOnly(
      KeyDescriptor key, double tier, FleetSuggestion.Builder proto) {
    return new SuggestionCandidate(
        Kind.KEY,
        key,
        tier,
        proto,
        Counting.NONE,
        "",
        false,
        false,
        /* rankCount= */ 0,
        /* count= */ null,
        /* groupRank= */ 0,
        /* overMax= */ false);
  }

  /**
   * A group-by row. {@code groupRank} is the key-name match quality (0 exact, 1 prefix, 2 contains)
   * and {@code groups} the number of buckets the grouping would produce.
   */
  static SuggestionCandidate groupBy(
      KeyDescriptor key,
      FleetSuggestion.Builder proto,
      int groups,
      int groupRank,
      boolean overMax) {
    return new SuggestionCandidate(
        Kind.GROUP_BY,
        key,
        /* tier= */ 0,
        proto,
        Counting.PRESET,
        "",
        false,
        false,
        /* rankCount= */ groups,
        groups,
        groupRank,
        overMax);
  }

  /**
   * A group-by row for a cold long-tail key whose overlay is not loaded yet, so no bucket count is
   * shown. {@code rankCount} is {@link Integer#MAX_VALUE} so counted groupings of the same priority
   * and match quality rank ahead of uncounted ones.
   */
  static SuggestionCandidate uncountedGroupBy(
      KeyDescriptor key, FleetSuggestion.Builder proto, int groupRank) {
    return new SuggestionCandidate(
        Kind.GROUP_BY,
        key,
        /* tier= */ 0,
        proto,
        Counting.NONE,
        "",
        false,
        false,
        /* rankCount= */ Integer.MAX_VALUE,
        /* count= */ null,
        groupRank,
        /* overMax= */ false);
  }

  Kind kind() {
    return kind;
  }

  KeyDescriptor key() {
    return key;
  }

  /** Match quality: 3 full, 2 prefix, 1 contains; key-only rows sit 0.4 below their condition. */
  double tier() {
    return tier;
  }

  /** The dim leading label ("Add filter", "Modify <Key>", "Group by"). */
  String label() {
    return proto.getLabel();
  }

  /** The concatenated main text, used for deduplication and as the stable tie-break. */
  String mainText() {
    return mainText;
  }

  Counting counting() {
    return counting;
  }

  String valueLower() {
    return valueLower;
  }

  boolean exclude() {
    return exclude;
  }

  boolean inChip() {
    return inChip;
  }

  /** The count used for ordering before scope counts exist (fleet-wide for value rows). */
  int rankCount() {
    return rankCount;
  }

  int groupRank() {
    return groupRank;
  }

  boolean overMax() {
    return overMax;
  }

  /** The displayed count once assigned; empty means no number is shown. */
  Optional<Integer> count() {
    return Optional.ofNullable(count);
  }

  /** Sets the displayed count; a null count shows no number. The prefix is "+" for a delta. */
  void assignCount(@Nullable Integer count, String countPrefix) {
    this.count = count;
    this.rankCount = count != null ? count : 0;
    this.countPrefix = countPrefix;
  }

  FleetSuggestion build() {
    if (count != null) {
      proto.setCount(count);
    }
    if (!countPrefix.isEmpty()) {
      proto.setCountPrefix(countPrefix);
    }
    return proto.build();
  }
}
