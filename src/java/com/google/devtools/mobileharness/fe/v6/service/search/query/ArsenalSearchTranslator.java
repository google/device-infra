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
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
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
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private static final String ATS_CONTROLLER_ID_KEY =
      HostKeys.PREFIX_HOST_FIELD + "ats_controller_id";
  private static final String ATS_LAB_DISPLAY_NAME_KEY =
      HostKeys.PREFIX_HOST_FIELD + "ats_lab_display_name";
  private static final String DEVICE_FIELD_OWNER = DeviceKeys.PREFIX_DEVICE_FIELD + "owner";
  private static final String DEVICE_FIELD_EXECUTOR = DeviceKeys.PREFIX_DEVICE_FIELD + "executor";
  private static final String DEVICE_FIELD_QUARANTINED =
      DeviceKeys.PREFIX_DEVICE_FIELD + "quarantined";
  private static final String HOST_FIELD_RELEASE_STATUS =
      HostKeys.PREFIX_HOST_FIELD + "release_status";
  private static final String HOST_FIELD_RELEASE_TYPE = HostKeys.PREFIX_HOST_FIELD + "release_type";
  private static final String HOST_FIELD_DAEMON_SERVER_VERSION =
      HostKeys.PREFIX_HOST_FIELD + "daemon_server_version";

  private final Map<Fleet, ScenarioCuration> curations;
  private final FleetChipResolver chipResolver;

  @Inject
  ArsenalSearchTranslator(Map<Fleet, ScenarioCuration> curations, FleetChipResolver chipResolver) {
    this.curations = checkNotNull(curations);
    this.chipResolver = checkNotNull(chipResolver);
  }

  public ArsenalSearchTranslator() {
    this(ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()), new FleetChipResolver());
  }

  /** Translates unpacked Arsenal search parameters into canonical FE v6 Fleet search state. */
  public TranslateArsenalSearchResponse translate(TranslateArsenalSearchRequest request) {
    SearchEntity entity =
        request.getEntity() == SearchEntity.SEARCH_ENTITY_HOST
            ? SearchEntity.SEARCH_ENTITY_HOST
            : SearchEntity.SEARCH_ENTITY_DEVICE;

    UniverseTranslation universeResult = resolveUniverse(request.getFiltersList());
    Fleet targetFleet =
        curations.containsKey(universeResult.fleet()) ? universeResult.fleet() : Fleet.FLEET_SELF;

    ScenarioCuration curation = curations.get(targetFleet);
    DeviceKeyRegistry deviceKeyRegistry = curation.deviceKeyRegistry();
    HostKeyRegistry hostKeyRegistry = curation.hostKeyRegistry();

    Map<String, Filter> filterMap = new LinkedHashMap<>();
    universeResult.atsControllerFilter().ifPresent(f -> filterMap.put(f.getKey(), f));

    for (ArsenalFilterParam param : request.getFiltersList()) {
      String rawKey = param.getKey().trim();
      if (Ascii.equalsIgnoreCase(rawKey, "universe")) {
        continue;
      }
      Optional<String> v6Key = translateKey(entity, targetFleet, rawKey);
      if (v6Key.isEmpty()) {
        continue;
      }
      translateCondition(v6Key.get(), param).ifPresent(f -> filterMap.put(f.getKey(), f));
    }
    ImmutableList<Filter> filters = ImmutableList.copyOf(filterMap.values());

    Set<String> groupBySet = new LinkedHashSet<>();
    for (String rawGb : request.getGroupByKeysList()) {
      translateKey(entity, targetFleet, rawGb.trim()).ifPresent(groupBySet::add);
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
        translateColumns(
            entity, targetFleet, deviceKeyRegistry, hostKeyRegistry, request.getColumnsList());

    return TranslateArsenalSearchResponse.newBuilder()
        .setFleet(targetFleet)
        .addAllFilters(filters)
        .addAllFilterChips(resolvedChips.getFilterChipsList())
        .addAllGroupByKeys(groupByKeys)
        .addAllGroupByChips(resolvedChips.getGroupByChipsList())
        .addAllColumns(columns)
        .build();
  }

  private record UniverseTranslation(Fleet fleet, Optional<Filter> atsControllerFilter) {}

  /**
   * Applies the universe filter migration rules:
   *
   * <ul>
   *   <li>If no universe filter is present, or if a positive match includes {@code google_1p},
   *       routes to {@link Fleet#FLEET_SELF} and drops the universe filter chip.
   *   <li>If the universe filter only matches ATS controllers (without {@code google_1p}), routes
   *       to {@link Fleet#FLEET_ATS} and translates the filter to {@code
   *       host_field::ats_controller_id}.
   *   <li>If the universe filter excludes {@code google_1p} ({@code not_match} / {@code
   *       none_match}), routes to {@link Fleet#FLEET_ATS} and emits a negated {@code
   *       host_field::ats_controller_id} filter only if additional ATS controller IDs were also
   *       excluded.
   * </ul>
   */
  private static UniverseTranslation resolveUniverse(List<ArsenalFilterParam> filters) {
    Optional<ArsenalFilterParam> universeParamOpt =
        Streams.findLast(
            filters.stream().filter(p -> Ascii.equalsIgnoreCase(p.getKey().trim(), "universe")));
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
      return new UniverseTranslation(
          Fleet.FLEET_ATS, translateCondition(ATS_CONTROLLER_ID_KEY, atsParam));
    }

    if (values.isEmpty() || containsGoogle1p) {
      return new UniverseTranslation(Fleet.FLEET_SELF, Optional.empty());
    }

    return new UniverseTranslation(
        Fleet.FLEET_ATS, translateCondition(ATS_CONTROLLER_ID_KEY, param));
  }

  private static Optional<String> translateKey(
      SearchEntity entity, Fleet fleet, String rawArsenalKey) {
    if (rawArsenalKey.isEmpty()) {
      return Optional.empty();
    }
    if (entity == SearchEntity.SEARCH_ENTITY_HOST) {
      return translateHostKey(fleet, rawArsenalKey);
    }
    return translateDeviceKey(fleet, rawArsenalKey);
  }

  private static Optional<String> translateDeviceKey(Fleet fleet, String rawKey) {
    if (rawKey.startsWith("dimension_")) {
      String dimName = rawKey.substring("dimension_".length()).trim();
      if (dimName.isEmpty()) {
        return Optional.empty();
      }
      if (Ascii.equalsIgnoreCase(dimName, "quarantined")) {
        return Optional.of(DEVICE_FIELD_QUARANTINED);
      }
      return Optional.of(DeviceKeys.PREFIX_DIMENSION + dimName);
    }
    if (rawKey.startsWith("host_property_")) {
      String propKey = rawKey.substring("host_property_".length()).trim();
      return propKey.isEmpty()
          ? Optional.empty()
          : Optional.of(HostKeys.PREFIX_HOST_PROPERTY + propKey);
    }
    return switch (Ascii.toLowerCase(rawKey)) {
      case "uuid" -> Optional.of(DeviceKeys.UUID.id());
      case "status" -> Optional.of(DeviceKeys.STATUS.id());
      case "type" -> Optional.of(DeviceKeys.TYPE.id());
      case "driver" -> Optional.of(DeviceKeys.DRIVER.id());
      case "decorator" -> Optional.of(DeviceKeys.DECORATOR.id());
      case "owner" -> Optional.of(DEVICE_FIELD_OWNER);
      case "executor" -> Optional.of(DEVICE_FIELD_EXECUTOR);
      case "host_name" -> Optional.of(DeviceKeys.HOST_NAME.id());
      case "model" -> Optional.of(DeviceKeys.MODEL.id());
      case "label" -> Optional.of(DeviceKeys.PREFIX_DIMENSION + "label");
      case "version" -> Optional.of(DeviceKeys.SDK_OR_SOFTWARE_VERSION.id());
      case "universe" ->
          fleet == Fleet.FLEET_ATS ? Optional.of(ATS_LAB_DISPLAY_NAME_KEY) : Optional.empty();
      case "required_dimensions" -> Optional.empty();
      default -> Optional.of(rawKey);
    };
  }

  private static Optional<String> translateHostKey(Fleet fleet, String rawKey) {
    if (rawKey.startsWith("host_property_")) {
      String propKey = rawKey.substring("host_property_".length()).trim();
      return propKey.isEmpty()
          ? Optional.empty()
          : Optional.of(HostKeys.PREFIX_HOST_PROPERTY + propKey);
    }
    return switch (Ascii.toLowerCase(rawKey)) {
      case "host_name" -> Optional.of(HostKeys.HOST_NAME.id());
      case "host_ip" -> Optional.of(HostKeys.HOST_IP.id());
      case "host_os" -> Optional.of(HostKeys.HOST_OS.id());
      case "device_count" -> Optional.of(HostKeys.DEVICE_COUNT.id());
      case "lab_server_version" -> Optional.of(HostKeys.LAB_SERVER_VERSION.id());
      case "daemon_server_version" -> Optional.of(HOST_FIELD_DAEMON_SERVER_VERSION);
      case "release_status", "lab_server_release_status" -> Optional.of(HOST_FIELD_RELEASE_STATUS);
      case "release_type" -> Optional.of(HOST_FIELD_RELEASE_TYPE);
      case "universe" ->
          fleet == Fleet.FLEET_ATS ? Optional.of(ATS_LAB_DISPLAY_NAME_KEY) : Optional.empty();
      default -> Optional.of(rawKey);
    };
  }

  private static Optional<Filter> translateCondition(String v6Key, ArsenalFilterParam param) {
    String matchType = Ascii.toLowerCase(param.getMatchType().trim());
    String valueType = Ascii.toLowerCase(param.getValueType().trim());
    ImmutableList<String> values =
        param.getValuesList().stream()
            .map(String::trim)
            .filter(v -> !v.isEmpty())
            .collect(toImmutableList());

    if (matchType.equals("empty")) {
      return Optional.of(
          Filter.newBuilder()
              .setKey(v6Key)
              .setSimple(
                  SimpleMatch.newBuilder()
                      .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance()))
                      .setNegated(false))
              .build());
    }
    if (matchType.equals("non_empty")) {
      return Optional.of(
          Filter.newBuilder()
              .setKey(v6Key)
              .setSimple(
                  SimpleMatch.newBuilder()
                      .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance()))
                      .setNegated(true))
              .build());
    }
    if (matchType.equals("exact_match")) {
      return Optional.of(
          Filter.newBuilder()
              .setKey(v6Key)
              .setComplex(
                  ComplexMatch.newBuilder()
                      .setMatchesExactly(MatchesExactly.newBuilder().addAllValues(values)))
              .build());
    }
    if (matchType.equals("match_at_least")) {
      return Optional.of(
          Filter.newBuilder()
              .setKey(v6Key)
              .setComplex(
                  ComplexMatch.newBuilder()
                      .setMatchesAtLeast(MatchesAtLeast.newBuilder().addAllValues(values)))
              .build());
    }

    boolean negated = matchType.equals("not_match") || matchType.equals("none_match");
    String pattern = Iterables.getFirst(values, "");

    if (valueType.equals("substring")) {
      return Optional.of(
          Filter.newBuilder()
              .setKey(v6Key)
              .setComplex(
                  ComplexMatch.newBuilder()
                      .setContainsSubstring(
                          ContainsSubstring.newBuilder().setValue(pattern).setNegated(negated)))
              .build());
    }
    if (valueType.equals("regex")) {
      return Optional.of(
          Filter.newBuilder()
              .setKey(v6Key)
              .setComplex(
                  ComplexMatch.newBuilder()
                      .setMatchesRegex(
                          MatchesRegex.newBuilder().setValue(pattern).setNegated(negated)))
              .build());
    }

    SimpleMatch.Builder simple = SimpleMatch.newBuilder().setNegated(negated);
    for (String v : values) {
      simple.addValues(FilterValue.newBuilder().setValue(v));
    }
    return Optional.of(Filter.newBuilder().setKey(v6Key).setSimple(simple).build());
  }

  private static ImmutableList<FleetColumnDescriptor> translateColumns(
      SearchEntity entity,
      Fleet fleet,
      DeviceKeyRegistry deviceKeyRegistry,
      HostKeyRegistry hostKeyRegistry,
      List<String> rawColumns) {
    if (rawColumns.isEmpty()) {
      return ImmutableList.of();
    }

    Map<String, FleetColumnDescriptor> descriptors = new LinkedHashMap<>();
    if (entity == SearchEntity.SEARCH_ENTITY_HOST) {
      String lockedId = HostKeys.HOST_NAME.id();
      descriptors.put(
          lockedId,
          FleetColumnDescriptor.newBuilder()
              .setKey(lockedId)
              .setDisplayName(HostKeyDisplays.standardDisplayName(HostKeys.HOST_NAME))
              .setLocked(true)
              .build());
      for (String rawCol : rawColumns) {
        Optional<String> v6KeyOpt = translateKey(entity, fleet, rawCol.trim());
        if (v6KeyOpt.isEmpty() || descriptors.containsKey(v6KeyOpt.get())) {
          continue;
        }
        Optional<HostKeyDescriptor> descriptorOpt = hostKeyRegistry.getKey(v6KeyOpt.get());
        if (descriptorOpt.isPresent()) {
          HostKeyDescriptor descriptor = descriptorOpt.get();
          descriptors.put(
              descriptor.id(),
              FleetColumnDescriptor.newBuilder()
                  .setKey(descriptor.id())
                  .setDisplayName(HostKeyDisplays.standardDisplayName(descriptor))
                  .setLocked(false)
                  .build());
        }
      }
    } else {
      String lockedId = DeviceKeys.UUID.id();
      descriptors.put(
          lockedId,
          FleetColumnDescriptor.newBuilder()
              .setKey(lockedId)
              .setDisplayName(DeviceKeyDisplays.standardDisplayName(DeviceKeys.UUID))
              .setLocked(true)
              .build());
      for (String rawCol : rawColumns) {
        Optional<String> v6KeyOpt = translateKey(entity, fleet, rawCol.trim());
        if (v6KeyOpt.isEmpty() || descriptors.containsKey(v6KeyOpt.get())) {
          continue;
        }
        Optional<DeviceKeyDescriptor> descriptorOpt = deviceKeyRegistry.getKey(v6KeyOpt.get());
        if (descriptorOpt.isPresent()) {
          DeviceKeyDescriptor descriptor = descriptorOpt.get();
          descriptors.put(
              descriptor.id(),
              FleetColumnDescriptor.newBuilder()
                  .setKey(descriptor.id())
                  .setDisplayName(DeviceKeyDisplays.standardDisplayName(descriptor))
                  .setLocked(false)
                  .build());
        }
      }
    }

    return ImmutableList.copyOf(descriptors.values());
  }
}
