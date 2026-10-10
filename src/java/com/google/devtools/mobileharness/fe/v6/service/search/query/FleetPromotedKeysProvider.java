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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterChipMetadata;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedFilterKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedGroupByKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;

/**
 * Produces the two zero-click key rows rendered under the search bar: the "Filter by:" row and the
 * "Group by:" row.
 *
 * <p>The "Filter by:" row offers a curated, stable list of candidate filter keys for quick access.
 * The "Group by:" row offers candidate grouping keys trimmed to those that produce at least two
 * distinct buckets within the current result set.
 */
public final class FleetPromotedKeysProvider {

  /**
   * A grouping needs at least two buckets to be worth offering. Ported from the prototype's {@code
   * GROUP_SUGGEST_MIN} (suggest_engine.py line 3020).
   */
  private static final int MIN_GROUP_COUNT = 2;

  /**
   * The maximum number of group-by keys supported in search. When this limit is reached, other
   * group-by options are marked disabled.
   */
  private static final int MAX_APPLIED_GROUP_BY = 3;

  /** Cap on the entries emitted per row. */
  private static final int PROMOTED_LIMIT = 6;

  private final FleetFilterEngine filterEngine;

  @Inject
  FleetPromotedKeysProvider(FleetFilterEngine filterEngine) {
    this.filterEngine = filterEngine;
  }

  /**
   * Returns the promoted filter and group-by rows for the current query.
   *
   * @param corpus the corpus to read
   * @param request the current filters and applied group-by keys
   */
  public FleetPromotedKeysResponse getPromotedKeys(
      SearchCorpus corpus, FleetPromotedKeysRequest request) {
    ScenarioCuration curation = corpus.curation();
    if (curation == null) {
      return FleetPromotedKeysResponse.getDefaultInstance();
    }

    boolean host = corpus.entity() == SearchEntity.SEARCH_ENTITY_HOST;
    ImmutableList<? extends KeyDescriptor> filterRow =
        host ? curation.hostFilterByRow() : curation.deviceFilterByRow();
    ImmutableList<? extends KeyDescriptor> groupByRow =
        host ? curation.hostGroupByRow() : curation.deviceGroupByRow();

    FleetIndex index = corpus.index();
    FleetPromotedKeysResponse.Builder response = FleetPromotedKeysResponse.newBuilder();

    addFilterKeys(response, index, filterRow);

    Set<String> appliedGroupByKeys = new HashSet<>(request.getGroupByList());
    ImmutableList<Integer> current = filterEngine.match(corpus, request.getFiltersList());
    addGroupByKeys(response, corpus, index, current, appliedGroupByKeys, groupByRow);

    return response.build();
  }

  /**
   * Appends the "Filter by:" row in curated order. A key is skipped when it is unknown in this
   * fleet (no data). Promoted filter keys remain stable and are not removed when already applied as
   * filters.
   */
  private static void addFilterKeys(
      FleetPromotedKeysResponse.Builder response,
      FleetIndex index,
      ImmutableList<? extends KeyDescriptor> filterByRow) {
    int emitted = 0;
    for (KeyDescriptor desc : filterByRow) {
      if (emitted >= PROMOTED_LIMIT) {
        break;
      }
      String keyId = desc.id();
      if (!index.keyIds().contains(keyId)) {
        continue;
      }
      response.addFilterKeys(
          FleetPromotedFilterKey.newBuilder()
              .setKey(keyId)
              .setMetadata(
                  FleetFilterChipMetadata.newBuilder()
                      .setKeyDisplayName(desc.display().name())
                      .setIsPlural(desc.display().isPlural())
                      .setCanUseAdvanced(true)
                      .build()));
      emitted++;
    }
  }

  /**
   * Appends the "Group by:" row in curated order. A key is skipped when it is unknown in this
   * fleet, or when it would produce fewer than two groups (a no-op grouping). Promoted group-by
   * keys remain stable and are not removed when already applied as group-bys. When the maximum
   * number of group-bys is reached, unapplied group-by options are marked disabled.
   */
  private static void addGroupByKeys(
      FleetPromotedKeysResponse.Builder response,
      SearchCorpus corpus,
      FleetIndex index,
      ImmutableList<Integer> current,
      Set<String> appliedGroupByKeys,
      ImmutableList<? extends KeyDescriptor> groupByRow) {
    boolean atLimit = appliedGroupByKeys.size() >= MAX_APPLIED_GROUP_BY;
    int emitted = 0;
    for (KeyDescriptor desc : groupByRow) {
      if (emitted >= PROMOTED_LIMIT) {
        break;
      }
      String keyId = desc.id();
      if (!index.keyIds().contains(keyId)) {
        continue;
      }
      KeyCount count = comboCount(corpus, current, keyId);
      int groups = count.distinctCombos() + (count.hasMissing() ? 1 : 0);
      if (groups < MIN_GROUP_COUNT && !appliedGroupByKeys.contains(keyId)) {
        continue;
      }
      boolean isDisabled = atLimit && !appliedGroupByKeys.contains(keyId);
      FleetPromotedGroupByKey.Builder groupByKey =
          FleetPromotedGroupByKey.newBuilder()
              .setKey(keyId)
              .setDisplayName(desc.display().name())
              .setGroupCount(groups)
              .setDisabled(isDisabled);
      if (isDisabled) {
        groupByKey.setDisabledReason(
            "Maximum of " + MAX_APPLIED_GROUP_BY + " group-by keys reached");
      }
      response.addGroupByKeys(groupByKey);
      emitted++;
    }
  }

  /**
   * Counts distinct value combinations for a key over the current result set, and whether any
   * entity in the set lacks the key.
   */
  private static KeyCount comboCount(
      SearchCorpus corpus, ImmutableList<Integer> current, String keyId) {
    Set<String> combos = new HashSet<>();
    boolean hasMissing = false;
    for (int recordIndex : current) {
      ImmutableSet<String> values = corpus.valuesForKey(recordIndex, keyId);
      if (values.isEmpty()) {
        hasMissing = true;
      } else {
        combos.add(String.join("\u0000", new TreeSet<>(values)));
      }
    }
    return new KeyCount(combos.size(), hasMissing);
  }

  /** Distinct value-combination count for a key plus whether some record in the set lacks it. */
  private record KeyCount(int distinctCombos, boolean hasMissing) {}
}
