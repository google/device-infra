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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ContainsSubstring;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterChipMetadata;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetInvalidFilterChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetInvalidGroupByChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetResolvedFilterChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetResolvedGroupByChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValidFilterChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValidGroupByChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesRegex;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Resolves filter and group-by chips into the pill text and metadata the frontend renders, without
 * running any query. This is the Java port of the search prototype's {@code resolve_chips} plus its
 * {@code _pill_key}, {@code _pill_condition}, and {@code _bff_metadata} helpers.
 *
 * <p>Resolution reads descriptors from the per-fleet {@link ScenarioCuration}'s {@link
 * DeviceKeyRegistry} and {@link HostKeyRegistry}, formatting presentation through {@link
 * DeviceKeyDisplays} and {@link HostKeyDisplays}.
 */
@Singleton
public final class FleetChipResolver {

  private final Map<Fleet, ScenarioCuration> curations;

  @Inject
  FleetChipResolver(Map<Fleet, ScenarioCuration> curations) {
    this.curations = checkNotNull(curations);
  }

  public FleetChipResolver() {
    this(ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()));
  }

  /**
   * Resolves every filter and group-by key in the request into its display text and metadata.
   *
   * @param request the chips to resolve
   * @return resolved chips in arrays parallel to the request
   */
  public FleetChipResolverResponse resolve(FleetChipResolverRequest request) {
    if (request.getEntity() == SearchEntity.SEARCH_ENTITY_UNSPECIFIED) {
      throw FeServiceException.invalidArgument(
          "entity must be specified in FleetChipResolverRequest");
    }
    Fleet fleet =
        request.getFleet() == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : request.getFleet();
    ScenarioCuration curation = curations.get(fleet);
    if (curation == null) {
      throw FeServiceException.invalidArgument("Unsupported fleet: " + fleet);
    }
    DeviceKeyRegistry deviceKeyRegistry = curation.deviceKeyRegistry();
    HostKeyRegistry hostKeyRegistry = curation.hostKeyRegistry();

    SearchEntity entity = request.getEntity();
    FleetChipResolverResponse.Builder response = FleetChipResolverResponse.newBuilder();
    for (Filter filter : request.getFiltersList()) {
      response.addFilterChips(resolveFilter(entity, deviceKeyRegistry, hostKeyRegistry, filter));
    }
    for (String keyId : request.getGroupByKeysList()) {
      response.addGroupByChips(resolveGroupBy(entity, deviceKeyRegistry, hostKeyRegistry, keyId));
    }
    return response.build();
  }

  private static FleetResolvedFilterChip resolveFilter(
      SearchEntity entity,
      DeviceKeyRegistry deviceKeyRegistry,
      HostKeyRegistry hostKeyRegistry,
      Filter filter) {
    String keyId = filter.getKey();
    if (keyId.trim().isEmpty()) {
      return FleetResolvedFilterChip.newBuilder()
          .setInvalid(FleetInvalidFilterChip.newBuilder().setReason("Filter key must not be empty"))
          .build();
    }
    String normalizedKey = keyId.trim();

    if (entity == SearchEntity.SEARCH_ENTITY_HOST) {
      Optional<HostKeyDescriptor> hostKey = hostKeyRegistry.getKey(normalizedKey);
      if (hostKey.isEmpty()) {
        return FleetResolvedFilterChip.newBuilder()
            .setInvalid(
                FleetInvalidFilterChip.newBuilder().setReason("Unknown host filter key: " + keyId))
            .build();
      }
      ValidationResult conditionValidation = validateCondition(filter);
      if (!conditionValidation.isValid()) {
        return FleetResolvedFilterChip.newBuilder()
            .setInvalid(
                FleetInvalidFilterChip.newBuilder().setReason(conditionValidation.errorMessage()))
            .build();
      }
      HostKeyDescriptor key = hostKey.get();
      return FleetResolvedFilterChip.newBuilder()
          .setValid(
              FleetValidFilterChip.newBuilder()
                  .setPillKey(pillKey(key))
                  .setPillCondition(conditionText(filter))
                  .setMetadata(metadata(key)))
          .build();
    } else {
      Optional<DeviceKeyDescriptor> deviceKey = deviceKeyRegistry.getKey(normalizedKey);
      if (deviceKey.isEmpty()) {
        return FleetResolvedFilterChip.newBuilder()
            .setInvalid(
                FleetInvalidFilterChip.newBuilder()
                    .setReason("Unknown device filter key: " + keyId))
            .build();
      }
      ValidationResult conditionValidation = validateCondition(filter);
      if (!conditionValidation.isValid()) {
        return FleetResolvedFilterChip.newBuilder()
            .setInvalid(
                FleetInvalidFilterChip.newBuilder().setReason(conditionValidation.errorMessage()))
            .build();
      }
      DeviceKeyDescriptor key = deviceKey.get();
      return FleetResolvedFilterChip.newBuilder()
          .setValid(
              FleetValidFilterChip.newBuilder()
                  .setPillKey(pillKey(key))
                  .setPillCondition(conditionText(filter))
                  .setMetadata(metadata(key)))
          .build();
    }
  }

  private static FleetResolvedGroupByChip resolveGroupBy(
      SearchEntity entity,
      DeviceKeyRegistry deviceKeyRegistry,
      HostKeyRegistry hostKeyRegistry,
      String rawKeyId) {
    String keyId = rawKeyId.trim();
    if (keyId.isEmpty()) {
      return FleetResolvedGroupByChip.newBuilder()
          .setInvalid(
              FleetInvalidGroupByChip.newBuilder().setReason("Group-by key must not be empty"))
          .build();
    }
    if (entity == SearchEntity.SEARCH_ENTITY_HOST) {
      Optional<HostKeyDescriptor> hostKey = hostKeyRegistry.getKey(keyId);
      if (hostKey.isEmpty()) {
        return FleetResolvedGroupByChip.newBuilder()
            .setInvalid(
                FleetInvalidGroupByChip.newBuilder()
                    .setReason("Unknown host group-by key: " + rawKeyId))
            .build();
      }
      HostKeyDescriptor key = hostKey.get();
      return FleetResolvedGroupByChip.newBuilder()
          .setValid(
              FleetValidGroupByChip.newBuilder()
                  .setPillKey(pillKey(key))
                  .setDisplayName(displayName(key)))
          .build();
    } else {
      Optional<DeviceKeyDescriptor> deviceKey = deviceKeyRegistry.getKey(keyId);
      if (deviceKey.isEmpty()) {
        return FleetResolvedGroupByChip.newBuilder()
            .setInvalid(
                FleetInvalidGroupByChip.newBuilder()
                    .setReason("Unknown device group-by key: " + rawKeyId))
            .build();
      }
      DeviceKeyDescriptor key = deviceKey.get();
      return FleetResolvedGroupByChip.newBuilder()
          .setValid(
              FleetValidGroupByChip.newBuilder()
                  .setPillKey(pillKey(key))
                  .setDisplayName(displayName(key)))
          .build();
    }
  }

  private record ValidationResult(boolean isValid, String errorMessage) {
    static ValidationResult valid() {
      return new ValidationResult(true, "");
    }

    static ValidationResult invalid(String message) {
      return new ValidationResult(false, message);
    }
  }

  private static ValidationResult validateCondition(Filter filter) {
    return switch (filter.getModeCase()) {
      case SIMPLE -> validateSimple(filter.getSimple());
      case COMPLEX -> validateComplex(filter.getComplex());
      case MODE_NOT_SET -> ValidationResult.invalid("Filter condition is not set");
    };
  }

  private static ValidationResult validateSimple(SimpleMatch simple) {
    if (simple.getValuesList().isEmpty()) {
      return ValidationResult.invalid("Simple filter condition must have at least one value");
    }
    return ValidationResult.valid();
  }

  private static ValidationResult validateComplex(ComplexMatch complex) {
    return switch (complex.getKindCase()) {
      case STARTS_WITH ->
          complex.getStartsWith().getValue().isEmpty()
              ? ValidationResult.invalid("Starts-with condition value must not be empty")
              : ValidationResult.valid();
      case CONTAINS_SUBSTRING ->
          complex.getContainsSubstring().getValue().isEmpty()
              ? ValidationResult.invalid("Contains-substring condition value must not be empty")
              : ValidationResult.valid();
      case MATCHES_REGEX -> {
        String pattern = complex.getMatchesRegex().getValue();
        if (pattern.isEmpty()) {
          yield ValidationResult.invalid("Regular expression must not be empty");
        }
        try {
          Pattern.compile(pattern);
          yield ValidationResult.valid();
        } catch (PatternSyntaxException e) {
          yield ValidationResult.invalid(
              "Invalid regular expression '" + pattern + "': " + e.getDescription());
        }
      }
      case MATCHES_EXACTLY ->
          complex.getMatchesExactly().getValuesList().isEmpty()
              ? ValidationResult.invalid("Matches-exactly condition must have at least one value")
              : ValidationResult.valid();
      case MATCHES_AT_LEAST ->
          complex.getMatchesAtLeast().getValuesList().isEmpty()
              ? ValidationResult.invalid("Matches-at-least condition must have at least one value")
              : ValidationResult.valid();
      case KIND_NOT_SET -> ValidationResult.invalid("Complex match kind is not set");
    };
  }

  private static FleetFilterChipMetadata metadata(DeviceKeyDescriptor key) {
    return FleetFilterChipMetadata.newBuilder()
        .setKeyDisplayName(displayName(key))
        .setCanUseAdvanced(true)
        .setIsPlural(key.display().isPlural())
        .build();
  }

  private static FleetFilterChipMetadata metadata(HostKeyDescriptor key) {
    return FleetFilterChipMetadata.newBuilder()
        .setKeyDisplayName(displayName(key))
        .setCanUseAdvanced(true)
        .setIsPlural(key.display().isPlural())
        .build();
  }

  /**
   * The short chip label: the key's display name with its namespace prefix stripped. A dimension
   * loses its "Dimension " prefix and a host property's "Host Property " becomes "Host ", matching
   * the prototype's {@code _pill_key}. Built-in fields and curated dimension names (for example
   * "Model") carry no prefix and pass through unchanged.
   */
  private static String pillKey(DeviceKeyDescriptor key) {
    return DeviceKeyDisplays.pillKey(key);
  }

  /**
   * Short chip label for host pills: built-in display name for built-ins, bare name for properties.
   */
  private static String pillKey(HostKeyDescriptor key) {
    return HostKeyDisplays.pillKey(key);
  }

  /**
   * The full key display name, including the "Dimension " and "Host Property " prefixes for
   * long-tail keys (Special Case 1 for filter chip titles).
   */
  private static String displayName(DeviceKeyDescriptor key) {
    return DeviceKeyDisplays.titleDisplayName(key);
  }

  /**
   * Title display name for host dialogs and suggestions: clean names for built-in host fields and
   * "Host Property <name>" for discovered host properties.
   */
  private static String displayName(HostKeyDescriptor key) {
    return HostKeyDisplays.titleDisplayName(key);
  }

  private static String conditionText(Filter filter) {
    return switch (filter.getModeCase()) {
      case SIMPLE -> simpleCondition(filter.getSimple());
      case COMPLEX -> complexCondition(filter.getComplex());
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
  private static String simpleCondition(SimpleMatch simple) {
    int count = 0;
    String lastDisplay = "";
    boolean lastIsNoValue = false;
    for (FilterValue value : simple.getValuesList()) {
      switch (value.getKindCase()) {
        case VALUE -> {
          count++;
          lastDisplay = value.getValue();
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
  private static String complexCondition(ComplexMatch complex) {
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
      case MATCHES_EXACTLY -> "is exactly " + setText(complex.getMatchesExactly().getValuesList());
      case MATCHES_AT_LEAST -> "has all of " + setText(complex.getMatchesAtLeast().getValuesList());
      case KIND_NOT_SET -> "";
    };
  }

  /** A value set rendered compactly: the sole value when there is one, the count otherwise. */
  private static String setText(List<String> values) {
    if (values.size() == 1) {
      return values.get(0);
    }
    return Integer.toString(values.size());
  }
}
