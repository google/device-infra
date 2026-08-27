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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ContainsSubstring;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterChipMetadata;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetResolvedFilterChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetResolvedGroupByChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesRegex;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.util.List;
import javax.inject.Inject;

/**
 * Resolves filter and group-by chips into the pill text and metadata the frontend renders, without
 * running any query. This is the Java port of the search prototype's {@code resolve_chips} plus its
 * {@code _pill_key}, {@code _pill_condition}, and {@code _bff_metadata} helpers.
 *
 * <p>Resolution is stateless beyond the snapshot: it needs only the chip structure to produce
 * display strings. It reads {@link FleetKeyDisplays} for the human key name and {@link
 * FleetIndex#valueDisplays(String)} for original value casing, mirroring the conventions in {@link
 * FleetCellMapper} and {@link FleetValueLister}. The response arrays are parallel to the request:
 * {@code filter_chips[i]} resolves {@code filters[i]} and {@code group_by_chips[j]} resolves {@code
 * group_by_keys[j]}.
 *
 * <p>A chip has two display halves. The {@code pill_key} is the short chip label, the key display
 * name with its namespace prefix stripped ({@code dim::model} to "Model", {@code prop::foo} to
 * "Host foo"). The {@code pill_condition} is the condition text describing what the filter selects.
 */
public final class FleetChipResolver {

  private static final ImmutableSet<String> PLURAL_DISPLAY_KEYS =
      ImmutableSet.of(
          DeviceKeys.PREFIX_DEVICE_FIELD + "owner",
          DeviceKeys.DRIVER.id(),
          DeviceKeys.DECORATOR.id(),
          DeviceKeys.PREFIX_DEVICE_FIELD + "executor");

  private static final ImmutableSet<String> VALUE_DISPLAY_KEYS =
      ImmutableSet.of(HostKeys.PREFIX_HOST_FIELD + "ats_controller");

  @Inject
  FleetChipResolver() {}

  /**
   * Resolves every filter and group-by key in the request into its display text and metadata.
   *
   * @param snapshot the fleet snapshot supplying key display names and value casing
   * @param request the chips to resolve
   * @return resolved chips in arrays parallel to the request
   */
  public FleetChipResolverResponse resolve(
      FleetSnapshot snapshot, FleetChipResolverRequest request) {
    FleetIndex index = snapshot.index();
    FleetChipResolverResponse.Builder response = FleetChipResolverResponse.newBuilder();
    for (Filter filter : request.getFiltersList()) {
      response.addFilterChips(resolveFilter(index, filter));
    }
    for (String keyId : request.getGroupByKeysList()) {
      response.addGroupByChips(resolveGroupBy(keyId));
    }
    return response.build();
  }

  private static FleetResolvedFilterChip resolveFilter(FleetIndex index, Filter filter) {
    String keyId = filter.getKey();
    return FleetResolvedFilterChip.newBuilder()
        .setPillKey(pillKey(keyId))
        .setPillCondition(conditionText(index, filter))
        .setMetadata(metadata(keyId))
        .build();
  }

  private static FleetResolvedGroupByChip resolveGroupBy(String keyId) {
    return FleetResolvedGroupByChip.newBuilder()
        .setPillKey(pillKey(keyId))
        .setDisplayName(displayName(keyId))
        .build();
  }

  private static FleetFilterChipMetadata metadata(String keyId) {
    return FleetFilterChipMetadata.newBuilder()
        .setKeyDisplayName(displayName(keyId))
        .setCanUseAdvanced(!VALUE_DISPLAY_KEYS.contains(keyId))
        .setIsPlural(PLURAL_DISPLAY_KEYS.contains(keyId))
        .build();
  }

  /**
   * The short chip label: the key's display name with its namespace prefix stripped. A dimension
   * loses its "Dimension " prefix and a host property's "Host Property " becomes "Host ", matching
   * the prototype's {@code _pill_key}. Built-in fields and curated dimension names (for example
   * "Model") carry no prefix and pass through unchanged.
   */
  private static String pillKey(String keyId) {
    return FleetKeyDisplays.pillKey(keyId);
  }

  /**
   * The full key display name, including the "Dimension " and "Host Property " prefixes for
   * long-tail keys (Special Case 1 for filter chip titles).
   */
  private static String displayName(String keyId) {
    return FleetKeyDisplays.titleDisplayName(keyId);
  }

  private static String conditionText(FleetIndex index, Filter filter) {
    String keyId = filter.getKey();
    return switch (filter.getModeCase()) {
      case SIMPLE -> simpleCondition(index, keyId, filter.getSimple());
      case COMPLEX -> complexCondition(index, keyId, filter.getComplex());
      case MODE_NOT_SET -> "";
    };
  }

  /**
   * Simple match condition text, kept compact so the chip stays short. The key name comes from the
   * pill key, so the condition names only the selection: a single value shows the value itself, and
   * several values collapse to their count. A negated match is prefixed with the not-equal sign.
   * The verb agreement carried by {@code is_plural} is metadata for the frontend, not part of this
   * text. The special no-value entry reads as "empty" ("not empty" when negated). Ported from the
   * prototype's {@code _pill_condition} (suggest_engine.py at depot HEAD, CL 959511642). A negated
   * SimpleMatch corresponds to the prototype's exclude branch, so its values are counted as
   * exclusions.
   *
   * <p>Formats: 1 value -> "Pixel 8"; N values -> "2"; negated 1 value -> "&ne; Pixel 8"; negated N
   * values -> "&ne; 2"; a lone no-value entry -> "empty" or "not empty". Real values and a no-value
   * entry count together toward N.
   */
  private static String simpleCondition(FleetIndex index, String keyId, SimpleMatch simple) {
    int count = 0;
    String lastDisplay = "";
    boolean lastIsNoValue = false;
    for (FilterValue value : simple.getValuesList()) {
      switch (value.getKindCase()) {
        case VALUE -> {
          count++;
          lastDisplay = displayValue(index, keyId, value.getValue());
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
      return "\u2026";
    }
    if (count == 1) {
      if (lastIsNoValue) {
        return negated ? "not empty" : "empty";
      }
      return negated ? "\u2260 " + lastDisplay : lastDisplay;
    }
    return negated ? "\u2260 " + count : Integer.toString(count);
  }

  /**
   * Complex match condition text, kept compact. The prefix, substring, and regex modes carry the
   * prototype's {@code _complex_cond_text} phrasing verbatim, extended with the not-form for the
   * two negatable modes: "starts with x", "contains x" ("does not contain x"), "matches /re/"
   * ("does not match /re/"). The substring and regex fragments are free text and shown verbatim.
   *
   * <p>TODO: matches_exactly and matches_at_least postdate the prototype's {@code _pill_condition},
   * so their compact phrasing here ("is exactly ..." / "has all of ...", the value when singular
   * and the count otherwise) is provisional pending confirmation.
   */
  private static String complexCondition(FleetIndex index, String keyId, ComplexMatch complex) {
    return switch (complex.getKindCase()) {
      case STARTS_WITH -> "starts with " + complex.getStartsWith().getValue();
      case CONTAINS_SUBSTRING -> {
        ContainsSubstring contains = complex.getContainsSubstring();
        yield (contains.getNegated() ? "does not contain " : "contains ") + contains.getValue();
      }
      case MATCHES_REGEX -> {
        MatchesRegex regex = complex.getMatchesRegex();
        yield (regex.getNegated() ? "does not match /" : "matches /") + regex.getValue() + "/";
      }
      case MATCHES_EXACTLY ->
          "is exactly " + setText(index, keyId, complex.getMatchesExactly().getValuesList());
      case MATCHES_AT_LEAST ->
          "has all of " + setText(index, keyId, complex.getMatchesAtLeast().getValuesList());
      case KIND_NOT_SET -> "";
    };
  }

  /** A value set rendered compactly: the sole value when there is one, the count otherwise. */
  private static String setText(FleetIndex index, String keyId, List<String> values) {
    if (values.size() == 1) {
      return displayValue(index, keyId, values.get(0));
    }
    return Integer.toString(values.size());
  }

  /**
   * The value's original-casing display, looked up by its normalized (lowercased) form, falling
   * back to the supplied value when the fleet has no record of it. Mirrors {@link
   * FleetValueLister}'s display resolution.
   */
  private static String displayValue(FleetIndex index, String keyId, String value) {
    return index.valueDisplays(keyId).getOrDefault(Ascii.toLowerCase(value), value);
  }
}
