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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterChipMetadata;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedFilterKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedGroupByKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;

/**
 * Produces the two zero-click key rows rendered under the search bar: the "Filter by:" row and the
 * "Group by:" row. This is the Java port of the search prototype's {@code suggested_keys}
 * (suggest_engine.py at depot HEAD, CL 959511642, lines 3078 to 3140).
 *
 * <p>Both rows answer "what is worth doing next, given the current filters". Each is a curated,
 * scenario-ordered list of candidate keys, trimmed to the keys that still discriminate within the
 * current result set: a key that would open a picker with a single value, or a grouping that would
 * yield a single bucket, adds no signal and is dropped.
 */
public final class FleetPromotedKeysProvider {

  /**
   * A grouping needs at least two buckets to be worth offering. Ported from the prototype's {@code
   * GROUP_SUGGEST_MIN} (suggest_engine.py line 3020).
   */
  private static final int MIN_GROUP_COUNT = 2;

  /**
   * The "Group by:" row is hidden once three group-by keys are already applied, matching the
   * three-key group-by cap. Ported from the prototype's {@code len(applied) < 3} guard
   * (suggest_engine.py line 3122).
   */
  private static final int MAX_APPLIED_GROUP_BY = 3;

  /**
   * Cap on the entries emitted per row. Ported from the prototype's default {@code limit=6}
   * (suggest_engine.py line 3079), which slices each row to its first six entries.
   */
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

    FleetIndex index = corpus.index();
    List<Filter> filters = request.getFiltersList();
    boolean hasFilters = !filters.isEmpty();

    ImmutableList<Integer> current = filterEngine.match(corpus, filters);

    Set<String> appliedFilterKeys = new HashSet<>();
    for (Filter filter : filters) {
      appliedFilterKeys.add(filter.getKey());
    }
    Set<String> appliedGroupByKeys = new HashSet<>(request.getGroupByList());

    boolean host = corpus.entity() == SearchEntity.SEARCH_ENTITY_HOST;
    FleetPromotedKeysResponse.Builder response = FleetPromotedKeysResponse.newBuilder();

    if (host) {
      addHostFilterKeys(
          response,
          corpus,
          index,
          current,
          hasFilters,
          appliedFilterKeys,
          curation.hostFilterByRow());
      addHostGroupByKeys(
          response, corpus, index, current, appliedGroupByKeys, curation.hostGroupByRow());
    } else {
      addDeviceFilterKeys(
          response,
          corpus,
          index,
          current,
          hasFilters,
          appliedFilterKeys,
          curation.deviceFilterByRow());
      addDeviceGroupByKeys(
          response, corpus, index, current, appliedGroupByKeys, curation.deviceGroupByRow());
    }
    return response.build();
  }

  /**
   * Appends the "Filter by:" row for devices in curated order. A key is skipped when it is unknown
   * in this fleet (no data), when it already has a filter chip (the proto contract promotes only
   * keys without a chip), or when it is a dead end. A dead end is a key with at most one distinct
   * value in the current result set, so its picker would offer nothing to choose. Following the
   * prototype, the dead-end test applies only once filters are present: the unfiltered anchor row
   * is shown whole.
   */
  private void addDeviceFilterKeys(
      FleetPromotedKeysResponse.Builder response,
      SearchCorpus corpus,
      FleetIndex index,
      ImmutableList<Integer> current,
      boolean hasFilters,
      Set<String> appliedFilterKeys,
      ImmutableList<DeviceKeyDescriptor> filterByRow) {
    int emitted = 0;
    for (DeviceKeyDescriptor desc : filterByRow) {
      if (emitted >= PROMOTED_LIMIT) {
        break;
      }
      String keyId = desc.id();
      if (!index.keyIds().contains(keyId) || appliedFilterKeys.contains(keyId)) {
        continue;
      }
      if (hasFilters && comboCount(corpus, current, keyId).distinctCombos() <= 1) {
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
   * Appends the "Filter by:" row for hosts in curated order, applying the same dead-end and
   * presence filters.
   */
  private void addHostFilterKeys(
      FleetPromotedKeysResponse.Builder response,
      SearchCorpus corpus,
      FleetIndex index,
      ImmutableList<Integer> current,
      boolean hasFilters,
      Set<String> appliedFilterKeys,
      ImmutableList<HostKeyDescriptor> filterByRow) {
    int emitted = 0;
    for (HostKeyDescriptor desc : filterByRow) {
      if (emitted >= PROMOTED_LIMIT) {
        break;
      }
      String keyId = desc.id();
      if (!index.keyIds().contains(keyId) || appliedFilterKeys.contains(keyId)) {
        continue;
      }
      if (hasFilters && comboCount(corpus, current, keyId).distinctCombos() <= 1) {
        continue;
      }
      boolean isAtsController = keyId.equals(HostKeys.PREFIX_HOST_FIELD + "ats_controller");
      response.addFilterKeys(
          FleetPromotedFilterKey.newBuilder()
              .setKey(keyId)
              .setMetadata(
                  FleetFilterChipMetadata.newBuilder()
                      .setKeyDisplayName(desc.display().name())
                      .setIsPlural(desc.display().isPlural())
                      .setCanUseAdvanced(!isAtsController)
                      .build()));
      emitted++;
    }
  }

  /**
   * Appends the "Group by:" row for devices in curated order. The row is empty once three group-by
   * keys are applied. A key is skipped when it is unknown in this fleet, when it is already an
   * applied group-by key, or when it would produce fewer than two groups (a no-op grouping). The
   * reported count is the number of buckets, distinct value combinations plus one for the "(no
   * value)" bucket when some device lacks the key, which is exactly what the user gets after
   * clicking.
   */
  private void addDeviceGroupByKeys(
      FleetPromotedKeysResponse.Builder response,
      SearchCorpus corpus,
      FleetIndex index,
      ImmutableList<Integer> current,
      Set<String> appliedGroupByKeys,
      ImmutableList<DeviceKeyDescriptor> groupByRow) {
    if (appliedGroupByKeys.size() >= MAX_APPLIED_GROUP_BY) {
      return;
    }
    int emitted = 0;
    for (DeviceKeyDescriptor desc : groupByRow) {
      if (emitted >= PROMOTED_LIMIT) {
        break;
      }
      String keyId = desc.id();
      if (!index.keyIds().contains(keyId) || appliedGroupByKeys.contains(keyId)) {
        continue;
      }
      KeyCount count = comboCount(corpus, current, keyId);
      int groups = count.distinctCombos() + (count.hasMissing() ? 1 : 0);
      if (groups < MIN_GROUP_COUNT) {
        continue;
      }
      response.addGroupByKeys(
          FleetPromotedGroupByKey.newBuilder()
              .setKey(keyId)
              .setDisplayName(desc.display().name())
              .setGroupCount(groups));
      emitted++;
    }
  }

  /**
   * Appends the "Group by:" row for hosts in curated order, calculating bucket counts over the
   * filtered host set.
   */
  private void addHostGroupByKeys(
      FleetPromotedKeysResponse.Builder response,
      SearchCorpus corpus,
      FleetIndex index,
      ImmutableList<Integer> current,
      Set<String> appliedGroupByKeys,
      ImmutableList<HostKeyDescriptor> groupByRow) {
    if (appliedGroupByKeys.size() >= MAX_APPLIED_GROUP_BY) {
      return;
    }
    int emitted = 0;
    for (HostKeyDescriptor desc : groupByRow) {
      if (emitted >= PROMOTED_LIMIT) {
        break;
      }
      String keyId = desc.id();
      if (!index.keyIds().contains(keyId) || appliedGroupByKeys.contains(keyId)) {
        continue;
      }
      KeyCount count = comboCount(corpus, current, keyId);
      int groups = count.distinctCombos() + (count.hasMissing() ? 1 : 0);
      if (groups < MIN_GROUP_COUNT) {
        continue;
      }
      response.addGroupByKeys(
          FleetPromotedGroupByKey.newBuilder()
              .setKey(keyId)
              .setDisplayName(desc.display().name())
              .setGroupCount(groups));
      emitted++;
    }
  }

  /**
   * Counts distinct value combinations for a key over the current result set, and whether any
   * device in the set lacks the key.
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

  /** Distinct value-combination count for a key plus whether some device in the set lacks it. */
  private record KeyCount(int distinctCombos, boolean hasMissing) {}
}
