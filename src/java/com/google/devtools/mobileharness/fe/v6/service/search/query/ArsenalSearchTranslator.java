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
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ArsenalFilterParam;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ContainsSubstring;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesAtLeast;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesExactly;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesRegex;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NoValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TranslateArsenalSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TranslateArsenalSearchResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates unpacked Alkali Arsenal search parameters ({@link TranslateArsenalSearchRequest}) into
 * canonical FE v6 Fleet search state ({@link TranslateArsenalSearchResponse}): target {@link
 * Fleet}, canonical {@link Filter} protos, resolved filter and group-by chips, and validated table
 * column descriptors.
 */
@Singleton
public final class ArsenalSearchTranslator {

  private static final String GOOGLE_1P_UNIVERSE = "google_1p";
  private static final String UNIVERSE_KEY = "universe";
  private static final String DIMENSION_PREFIX = "dimension_";
  private static final String HOST_PROPERTY_PREFIX = "host_property_";

  private static final ImmutableMap<String, String> COMMON_DEVICE_KEY_ALIASES =
      ImmutableMap.of(
          "uuid", DeviceKeys.UUID.id(),
          "status", DeviceKeys.STATUS.id(),
          "type", DeviceKeys.TYPE.id(),
          "driver", DeviceKeys.DRIVER.id(),
          "decorator", DeviceKeys.DECORATOR.id(),
          "host_name", DeviceKeys.HOST_NAME.id(),
          "model", DeviceKeys.MODEL.id(),
          "label", DeviceKeys.dimensionKeyId("label"));

  /** Arsenal device keys that exist only in table columns, not in filters or group-by. */
  private static final ImmutableMap<String, String> DEVICE_COLUMN_ONLY_KEY_ALIASES =
      ImmutableMap.of("version", DeviceKeys.SDK_OR_SOFTWARE_VERSION.id());

  private static final ImmutableMap<String, String> COMMON_HOST_KEY_ALIASES =
      ImmutableMap.of(
          "host_name", HostKeys.HOST_NAME.id(),
          "host_ip", HostKeys.HOST_IP.id(),
          "host_os", HostKeys.HOST_OS.id(),
          "device_count", HostKeys.DEVICE_COUNT.id(),
          "lab_server_version", HostKeys.LAB_SERVER_VERSION.id());

  private static final ImmutableSet<String> DROPPED_DEVICE_KEYS =
      ImmutableSet.of(UNIVERSE_KEY, "required_dimensions");
  private static final ImmutableSet<String> DROPPED_HOST_KEYS = ImmutableSet.of(UNIVERSE_KEY);

  /** Default device table columns in Alkali Arsenal when {@code column} is omitted from the URL. */
  private static final ImmutableList<String> DEFAULT_ARSENAL_DEVICE_COLUMNS =
      ImmutableList.of("uuid", "host_name", "type", "label", "model", "version", "status", "owner");

  /** Default host table columns in Alkali Arsenal when {@code column} is omitted from the URL. */
  private static final ImmutableList<String> DEFAULT_ARSENAL_HOST_COLUMNS =
      ImmutableList.of(
          "host_name", "host_ip", "host_os", "lab_server_release_status", "release_type");

  private final Map<Fleet, ScenarioCuration> curations;
  private final FleetChipResolver chipResolver;

  @Inject
  ArsenalSearchTranslator(Map<Fleet, ScenarioCuration> curations, FleetChipResolver chipResolver) {
    this.curations = checkNotNull(curations);
    this.chipResolver = checkNotNull(chipResolver);
  }

  /** Translates unpacked Arsenal search parameters into canonical FE v6 Fleet search state. */
  public TranslateArsenalSearchResponse translate(TranslateArsenalSearchRequest request) {
    if (request.getEntity() == SearchEntity.SEARCH_ENTITY_UNSPECIFIED) {
      throw FeServiceException.invalidArgument(
          "entity must be specified in TranslateArsenalSearchRequest");
    }
    SearchEntity entity = request.getEntity();

    UniverseTranslation universeResult = resolveUniverse(request.getFiltersList());
    Fleet targetFleet =
        curations.containsKey(universeResult.fleet()) ? universeResult.fleet() : Fleet.FLEET_SELF;

    ScenarioCuration curation = curations.get(targetFleet);
    if (curation == null) {
      throw FeServiceException.invalidArgument("Unsupported fleet: " + targetFleet);
    }

    Map<String, Filter> filterMap = new LinkedHashMap<>();
    if (universeResult.atsControllerParam().isPresent()
        && curation.atsControllerIdKey().isPresent()) {
      translateCondition(
              curation.atsControllerIdKey().get(), universeResult.atsControllerParam().get())
          .ifPresent(f -> filterMap.put(f.getKey(), f));
    }

    for (ArsenalFilterParam param : request.getFiltersList()) {
      String rawKey = param.getKey().trim();
      if (Ascii.equalsIgnoreCase(rawKey, UNIVERSE_KEY)) {
        continue;
      }
      Optional<String> v6Key = translateKey(entity, curation, rawKey);
      if (v6Key.isEmpty()) {
        continue;
      }
      translateCondition(v6Key.get(), param).ifPresent(f -> filterMap.put(f.getKey(), f));
    }
    ImmutableList<Filter> filters = ImmutableList.copyOf(filterMap.values());

    Set<String> groupBySet = new LinkedHashSet<>();
    for (String rawGb : request.getGroupByKeysList()) {
      translateKey(entity, curation, rawGb.trim()).ifPresent(groupBySet::add);
    }
    ImmutableList<String> groupByKeys = ImmutableList.copyOf(groupBySet);

    FleetChipResolverResponse resolvedChips =
        chipResolver.resolve(
            FleetChipResolverRequest.newBuilder()
                .setEntity(entity)
                .setFleet(targetFleet)
                .addAllFilters(filters)
                .addAllGroupByKeys(groupByKeys)
                .build());

    ImmutableList<FleetColumnDescriptor> columns =
        translateColumns(entity, curation, request.getColumnsList());

    return TranslateArsenalSearchResponse.newBuilder()
        .setFleet(targetFleet)
        .addAllFilters(filters)
        .addAllFilterChips(resolvedChips.getFilterChipsList())
        .addAllGroupByKeys(groupByKeys)
        .addAllGroupByChips(resolvedChips.getGroupByChipsList())
        .addAllColumns(columns)
        .build();
  }

  private record UniverseTranslation(
      Fleet fleet, Optional<ArsenalFilterParam> atsControllerParam) {}

  /**
   * Applies the universe filter migration rules:
   *
   * <ul>
   *   <li>If no universe filter is present, or if a positive match includes {@code google_1p},
   *       routes to {@link Fleet#FLEET_SELF} and drops the universe filter chip.
   *   <li>If the universe filter only matches ATS controllers (without {@code google_1p}), routes
   *       to {@link Fleet#FLEET_ATS} and carries the remaining values for the ATS controller ID
   *       filter.
   *   <li>If the universe filter excludes {@code google_1p} ({@code not_match} / {@code
   *       none_match}), routes to {@link Fleet#FLEET_ATS} and carries a negated ATS controller ID
   *       filter only if additional ATS controller IDs were also excluded.
   * </ul>
   */
  private static UniverseTranslation resolveUniverse(List<ArsenalFilterParam> filters) {
    Optional<ArsenalFilterParam> universeParamOpt =
        Streams.findLast(
            filters.stream().filter(p -> Ascii.equalsIgnoreCase(p.getKey().trim(), UNIVERSE_KEY)));
    if (universeParamOpt.isEmpty()) {
      return new UniverseTranslation(Fleet.FLEET_SELF, Optional.empty());
    }

    ArsenalFilterParam param = universeParamOpt.get();
    String matchType = Ascii.toLowerCase(param.getMatchType().trim());
    ImmutableList<String> values =
        param.getValuesList().stream()
            .map(String::trim)
            .filter(v -> !v.isEmpty())
            .collect(toImmutableList());

    boolean isNegated = matchType.equals("not_match") || matchType.equals("none_match");
    boolean containsGoogle1p =
        values.stream().anyMatch(v -> Ascii.equalsIgnoreCase(v, GOOGLE_1P_UNIVERSE));

    if (isNegated) {
      if (!containsGoogle1p) {
        return new UniverseTranslation(Fleet.FLEET_SELF, Optional.empty());
      }
      ImmutableList<String> remainingAtsControllers =
          values.stream()
              .filter(v -> !Ascii.equalsIgnoreCase(v, GOOGLE_1P_UNIVERSE))
              .collect(toImmutableList());
      if (remainingAtsControllers.isEmpty()) {
        return new UniverseTranslation(Fleet.FLEET_ATS, Optional.empty());
      }
      ArsenalFilterParam atsParam =
          param.toBuilder().clearValues().addAllValues(remainingAtsControllers).build();
      return new UniverseTranslation(Fleet.FLEET_ATS, Optional.of(atsParam));
    }

    if (values.isEmpty() || containsGoogle1p) {
      return new UniverseTranslation(Fleet.FLEET_SELF, Optional.empty());
    }

    return new UniverseTranslation(Fleet.FLEET_ATS, Optional.of(param));
  }

  private static Optional<String> translateKey(
      SearchEntity entity, ScenarioCuration curation, String rawArsenalKey) {
    if (rawArsenalKey.isEmpty()) {
      return Optional.empty();
    }
    if (entity == SearchEntity.SEARCH_ENTITY_HOST) {
      return translateHostKey(curation, rawArsenalKey);
    }
    return translateDeviceKey(curation, rawArsenalKey);
  }

  private static Optional<String> translateDeviceKey(ScenarioCuration curation, String rawKey) {
    String normalized = Ascii.toLowerCase(rawKey);
    String curatedAlias = curation.arsenalDeviceKeyAliases().get(normalized);
    if (curatedAlias != null) {
      return Optional.of(curatedAlias);
    }
    String commonAlias = COMMON_DEVICE_KEY_ALIASES.get(normalized);
    if (commonAlias != null) {
      return Optional.of(commonAlias);
    }
    if (DROPPED_DEVICE_KEYS.contains(normalized)) {
      return Optional.empty();
    }
    if (rawKey.startsWith(DIMENSION_PREFIX)) {
      String dimName = rawKey.substring(DIMENSION_PREFIX.length()).trim();
      return dimName.isEmpty() ? Optional.empty() : Optional.of(DeviceKeys.dimensionKeyId(dimName));
    }
    if (rawKey.startsWith(HOST_PROPERTY_PREFIX)) {
      String propKey = rawKey.substring(HOST_PROPERTY_PREFIX.length()).trim();
      return propKey.isEmpty()
          ? Optional.empty()
          : Optional.of(HostKeys.hostPropertyKeyId(propKey));
    }
    return Optional.of(rawKey);
  }

  private static Optional<String> translateDeviceColumnKey(
      ScenarioCuration curation, String rawKey) {
    if (rawKey.isEmpty()) {
      return Optional.empty();
    }
    String columnOnlyAlias = DEVICE_COLUMN_ONLY_KEY_ALIASES.get(Ascii.toLowerCase(rawKey));
    if (columnOnlyAlias != null) {
      return Optional.of(columnOnlyAlias);
    }
    return translateDeviceKey(curation, rawKey);
  }

  private static Optional<String> translateHostKey(ScenarioCuration curation, String rawKey) {
    String normalized = Ascii.toLowerCase(rawKey);
    String curatedAlias = curation.arsenalHostKeyAliases().get(normalized);
    if (curatedAlias != null) {
      return Optional.of(curatedAlias);
    }
    String commonAlias = COMMON_HOST_KEY_ALIASES.get(normalized);
    if (commonAlias != null) {
      return Optional.of(commonAlias);
    }
    if (DROPPED_HOST_KEYS.contains(normalized)) {
      return Optional.empty();
    }
    if (rawKey.startsWith(HOST_PROPERTY_PREFIX)) {
      String propKey = rawKey.substring(HOST_PROPERTY_PREFIX.length()).trim();
      return propKey.isEmpty()
          ? Optional.empty()
          : Optional.of(HostKeys.hostPropertyKeyId(propKey));
    }
    return Optional.of(rawKey);
  }

  private static Optional<Filter> translateCondition(String v6Key, ArsenalFilterParam param) {
    String matchType = Ascii.toLowerCase(param.getMatchType().trim());
    String valueType = Ascii.toLowerCase(param.getValueType().trim());

    if (matchType.equals("empty")) {
      return Optional.of(noValueFilter(v6Key, /* negated= */ false));
    }
    if (matchType.equals("non_empty")) {
      return Optional.of(noValueFilter(v6Key, /* negated= */ true));
    }

    boolean negated = matchType.equals("not_match") || matchType.equals("none_match");
    String rawPattern = Iterables.getFirst(param.getValuesList(), "");
    if (valueType.equals("substring")) {
      return Optional.of(
          complexFilter(
              v6Key,
              ComplexMatch.newBuilder()
                  .setContainsSubstring(
                      ContainsSubstring.newBuilder().setValue(rawPattern).setNegated(negated))));
    }
    if (valueType.equals("regex")) {
      return Optional.of(
          complexFilter(
              v6Key,
              ComplexMatch.newBuilder()
                  .setMatchesRegex(
                      MatchesRegex.newBuilder().setValue(rawPattern).setNegated(negated))));
    }

    ImmutableList<String> values =
        param.getValuesList().stream()
            .map(String::trim)
            .filter(v -> !v.isEmpty())
            .collect(toImmutableList());
    if (matchType.equals("exact_match")) {
      return Optional.of(
          complexFilter(
              v6Key,
              ComplexMatch.newBuilder()
                  .setMatchesExactly(MatchesExactly.newBuilder().addAllValues(values))));
    }
    if (matchType.equals("match_at_least")) {
      return Optional.of(
          complexFilter(
              v6Key,
              ComplexMatch.newBuilder()
                  .setMatchesAtLeast(MatchesAtLeast.newBuilder().addAllValues(values))));
    }

    SimpleMatch.Builder simple = SimpleMatch.newBuilder().setNegated(negated);
    for (String v : values) {
      simple.addValues(FilterValue.newBuilder().setValue(v));
    }
    return Optional.of(Filter.newBuilder().setKey(v6Key).setSimple(simple).build());
  }

  private static Filter noValueFilter(String v6Key, boolean negated) {
    return Filter.newBuilder()
        .setKey(v6Key)
        .setSimple(
            SimpleMatch.newBuilder()
                .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance()))
                .setNegated(negated))
        .build();
  }

  private static Filter complexFilter(String v6Key, ComplexMatch.Builder complex) {
    return Filter.newBuilder().setKey(v6Key).setComplex(complex).build();
  }

  private static ImmutableList<FleetColumnDescriptor> translateColumns(
      SearchEntity entity, ScenarioCuration curation, List<String> rawColumns) {
    List<String> effectiveColumns =
        rawColumns.isEmpty()
            ? (entity == SearchEntity.SEARCH_ENTITY_HOST
                ? DEFAULT_ARSENAL_HOST_COLUMNS
                : DEFAULT_ARSENAL_DEVICE_COLUMNS)
            : rawColumns;
    if (entity == SearchEntity.SEARCH_ENTITY_HOST) {
      HostKeyRegistry hostKeyRegistry = curation.hostKeyRegistry();
      return buildColumnDescriptors(
          effectiveColumns,
          HostKeys.HOST_NAME.id(),
          HostKeyDisplays.standardDisplayName(HostKeys.HOST_NAME),
          rawCol ->
              translateHostKey(curation, rawCol)
                  .flatMap(hostKeyRegistry::getKey)
                  .map(
                      d ->
                          columnDescriptor(d.id(), HostKeyDisplays.standardDisplayName(d), false)));
    }
    DeviceKeyRegistry deviceKeyRegistry = curation.deviceKeyRegistry();
    return buildColumnDescriptors(
        effectiveColumns,
        DeviceKeys.UUID.id(),
        DeviceKeyDisplays.standardDisplayName(DeviceKeys.UUID),
        rawCol ->
            translateDeviceColumnKey(curation, rawCol)
                .flatMap(deviceKeyRegistry::getKey)
                .map(
                    d ->
                        columnDescriptor(d.id(), DeviceKeyDisplays.standardDisplayName(d), false)));
  }

  private static ImmutableList<FleetColumnDescriptor> buildColumnDescriptors(
      List<String> rawColumns,
      String lockedKeyId,
      String lockedDisplayName,
      Function<String, Optional<FleetColumnDescriptor>> columnLookup) {
    Map<String, FleetColumnDescriptor> descriptors = new LinkedHashMap<>();
    descriptors.put(lockedKeyId, columnDescriptor(lockedKeyId, lockedDisplayName, true));
    for (String rawCol : rawColumns) {
      columnLookup
          .apply(rawCol.trim())
          .ifPresent(col -> descriptors.putIfAbsent(col.getKey(), col));
    }
    return ImmutableList.copyOf(descriptors.values());
  }

  private static FleetColumnDescriptor columnDescriptor(
      String key, String displayName, boolean locked) {
    return FleetColumnDescriptor.newBuilder()
        .setKey(key)
        .setDisplayName(displayName)
        .setLocked(locked)
        .build();
  }
}
