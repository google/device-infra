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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetAddGroupBy;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetApplyFilter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterChipMetadata;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetNewChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetOpenPicker;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetStagedModification;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetViewExisting;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NoValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;

/**
 * Builds what clicking a suggestion does: the filter to apply, the picker to open, or the group-by
 * to add, together with the chip metadata the frontend needs to render the result.
 *
 * <p>Every method takes the key as a {@link KeyDescriptor} together with the entity's {@link
 * KeyVocabulary}, so pill keys, display names and plural grammar always come from the vocabulary
 * and a raw key id reaches the frontend only as the filter key it must carry. Wording of the
 * suggestion row itself lives in {@link SuggestionText}.
 */
final class SuggestionActions {

  private static final String ELLIPSIS = "\u2026";
  private static final String NOT_EQUAL = "\u2260 ";

  static FleetApplyFilter applyFilter(
      KeyVocabulary vocabulary, FleetIndex index, KeyDescriptor key, Filter filter) {
    return FleetApplyFilter.newBuilder()
        .setResultingFilter(filter)
        .setPillKey(vocabulary.pillKey(key))
        .setPillCondition(pillCondition(index, filter))
        .setMetadata(metadata(vocabulary, key))
        .build();
  }

  static FleetOpenPicker openPickerNewChip(KeyVocabulary vocabulary, KeyDescriptor key) {
    return FleetOpenPicker.newBuilder()
        .setKey(key.id())
        .setMetadata(metadata(vocabulary, key))
        .setNewChip(FleetNewChip.getDefaultInstance())
        .build();
  }

  static FleetOpenPicker openPickerViewExisting(KeyVocabulary vocabulary, KeyDescriptor key) {
    return FleetOpenPicker.newBuilder()
        .setKey(key.id())
        .setMetadata(metadata(vocabulary, key))
        .setViewExisting(FleetViewExisting.getDefaultInstance())
        .build();
  }

  static FleetOpenPicker openPickerStaged(
      KeyVocabulary vocabulary, KeyDescriptor key, ImmutableList<String> values) {
    return FleetOpenPicker.newBuilder()
        .setKey(key.id())
        .setMetadata(metadata(vocabulary, key))
        .setStagedModify(FleetStagedModification.newBuilder().addAllValues(values))
        .build();
  }

  /**
   * The picker action for a key: view the existing chip if one is applied, else start a new one.
   */
  static FleetOpenPicker openPicker(KeyVocabulary vocabulary, KeyDescriptor key, boolean inChip) {
    return inChip ? openPickerViewExisting(vocabulary, key) : openPickerNewChip(vocabulary, key);
  }

  static FleetAddGroupBy addGroupBy(KeyVocabulary vocabulary, KeyDescriptor key) {
    return FleetAddGroupBy.newBuilder()
        .setKey(key.id())
        .setPillKey(vocabulary.pillKey(key))
        .build();
  }

  /** A simple filter over the given values, included or excluded. */
  static Filter valueFilter(String keyId, ImmutableList<String> values, boolean negated) {
    SimpleMatch.Builder simple = SimpleMatch.newBuilder().setNegated(negated);
    values.forEach(value -> simple.addValues(FilterValue.newBuilder().setValue(value)));
    return Filter.newBuilder().setKey(keyId).setSimple(simple).build();
  }

  /** A simple filter over the no-value marker: "is empty" or, negated, "is not empty". */
  static Filter noValueFilter(String keyId, boolean negated) {
    return Filter.newBuilder()
        .setKey(keyId)
        .setSimple(
            SimpleMatch.newBuilder()
                .setNegated(negated)
                .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance())))
        .build();
  }

  private static FleetFilterChipMetadata metadata(KeyVocabulary vocabulary, KeyDescriptor key) {
    return FleetFilterChipMetadata.newBuilder()
        .setKeyDisplayName(vocabulary.titleDisplayName(key))
        .setCanUseAdvanced(true)
        .setIsPlural(key.display().isPlural())
        .build();
  }

  /**
   * Compact condition text for the resulting chip pill, mirroring {@link FleetChipResolver}. A
   * single value shows itself, several collapse to their count, an exclude is prefixed with the
   * not-equal sign, and a lone no-value entry reads as "empty" or "not empty".
   */
  private static String pillCondition(FleetIndex index, Filter filter) {
    if (filter.getModeCase() != Filter.ModeCase.SIMPLE) {
      return ELLIPSIS;
    }
    SimpleMatch simple = filter.getSimple();
    int count = 0;
    String lastDisplay = "";
    boolean lastIsNoValue = false;
    for (FilterValue value : simple.getValuesList()) {
      switch (value.getKindCase()) {
        case VALUE -> {
          count++;
          lastDisplay =
              SuggestionText.displayValue(
                  index, filter.getKey(), Ascii.toLowerCase(value.getValue()));
          lastIsNoValue = false;
        }
        case NO_VALUE -> {
          count++;
          lastIsNoValue = true;
        }
        case KIND_NOT_SET -> {}
      }
    }
    boolean negated = simple.getNegated();
    if (count == 0) {
      return ELLIPSIS;
    }
    if (count == 1) {
      if (lastIsNoValue) {
        return negated ? "not empty" : "empty";
      }
      return negated ? NOT_EQUAL + lastDisplay : lastDisplay;
    }
    return negated ? NOT_EQUAL + count : Integer.toString(count);
  }

  private SuggestionActions() {}
}
