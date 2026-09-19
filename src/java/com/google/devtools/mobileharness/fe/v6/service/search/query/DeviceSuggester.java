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
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Dedicated search suggestion provider for device search queries over {@link DeviceCorpus}.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Device search features a complex key space spanning built-in typed fields ({@code
 * device_field::uuid}, {@code device_field::status}), common composite dimensions ({@code
 * dimension::model}, {@code dimension::os}), configuration parameters ({@code
 * device_config::wifi_ssid}), projected host attributes ({@code host_field::host_name}), and
 * dynamically discovered long-tail dimensions from {@link DimensionCatalogStore}.
 *
 * <p>Isolating this key domain inside {@link DeviceSuggester} guarantees that device dimension
 * catalogs and namespace syntax ({@code dimension:<name>}) are physically confined to device search
 * and can never leak into host search suggestions.
 *
 * <h2>How to use it</h2>
 *
 * <p>Injected as a singleton and called by {@link FleetSuggester} when the target entity is {@code
 * SEARCH_ENTITY_DEVICE}:
 *
 * <pre>{@code
 * deviceSuggester.suggest(deviceCorpus, request);
 * }</pre>
 *
 * <h2>Device key domain and grammar</h2>
 *
 * <ul>
 *   <li><b>Aliases</b>: Maps natural synonyms (e.g. {@code status}, {@code model}, {@code pool},
 *       {@code lab location}, {@code wifi}, {@code uuid}) to their device key IDs.
 *   <li><b>Explicit namespace prefixes</b>: Recognizes {@code dimension:<name>} and {@code device
 *       dimension:<name>}.
 *   <li><b>Discovered dimensions</b>: Matches unindexed dimensions discovered across fleets from
 *       {@link DimensionCatalogStore}.
 *   <li><b>Dynamic key minting</b>: Synthesizes {@code dimension::<name>} for unrecognized tokens
 *       in key-value filter conditions.
 * </ul>
 */
@Singleton
public final class DeviceSuggester implements EntityKeyStrategy {

  private static final Pattern NAMESPACE_DIM =
      Pattern.compile("^(?:device[ _])?dimension[ _:]+(.+)$");

  private static final ImmutableMap<String, ImmutableList<String>> DEVICE_ALIASES =
      buildDeviceAliasMap();

  private final SuggesterEngine engine;
  private final Map<Fleet, ScenarioCuration> curations;
  private final DimensionCatalogStore dimensionCatalogStore;

  @Inject
  DeviceSuggester(
      SuggesterEngine engine,
      Map<Fleet, ScenarioCuration> curations,
      DimensionCatalogStore dimensionCatalogStore) {
    this.engine = checkNotNull(engine);
    this.curations = checkNotNull(curations);
    this.dimensionCatalogStore = checkNotNull(dimensionCatalogStore);
  }

  public DeviceSuggester(SuggesterEngine engine, Map<Fleet, ScenarioCuration> curations) {
    this(engine, curations, new DimensionCatalogStore());
  }

  public DeviceSuggester(SuggesterEngine engine, DimensionCatalogStore dimensionCatalogStore) {
    this(engine, ImmutableMap.of(), dimensionCatalogStore);
  }

  public DeviceSuggester(SuggesterEngine engine) {
    this(engine, ImmutableMap.of(), new DimensionCatalogStore());
  }

  /** Generates ranked device search suggestions for the given corpus and request. */
  public FleetSuggestionResponse suggest(DeviceCorpus corpus, FleetSuggestionRequest request) {
    return engine.suggest(corpus, request, this);
  }

  @Override
  public ImmutableList<String> resolveKey(SearchCorpus corpus, String token) {
    String raw = token.trim();
    String low = Ascii.toLowerCase(raw);

    Matcher dim = NAMESPACE_DIM.matcher(low);
    if (dim.matches()) {
      String dimName = SuggesterEngine.normalize(dim.group(1));
      String keyId = DeviceKeys.dimensionKeyId(dimName);
      return isKeyKnown(corpus, keyId) ? ImmutableList.of(keyId) : ImmutableList.of();
    }

    ImmutableList<String> aliased = DEVICE_ALIASES.get(SuggesterEngine.normalize(raw));
    if (aliased != null) {
      ImmutableList<String> valid =
          aliased.stream().filter(keyId -> isKeyKnown(corpus, keyId)).collect(toImmutableList());
      if (!valid.isEmpty()) {
        return valid;
      }
    }

    String bareDimName = SuggesterEngine.normalize(raw);
    String bareDim = DeviceKeys.dimensionKeyId(bareDimName);
    boolean inCatalog = dimensionCatalogStore.hasDimension(Fleet.FLEET_SELF, bareDimName);
    if ((corpus.index().keyIds().contains(bareDim) || inCatalog) && isKeyKnown(corpus, bareDim)) {
      return ImmutableList.of(bareDim);
    }

    return ImmutableList.of();
  }

  @Override
  public Optional<String> synthesizeDynamicKeyId(String bareToken) {
    String bareName = SuggesterEngine.normalize(bareToken);
    return bareName.isEmpty()
        ? Optional.empty()
        : Optional.of(DeviceKeys.PREFIX_DIMENSION + bareName);
  }

  @Override
  public ImmutableList<String> matchDiscoveredPrefixKeys(Fleet fleet, String normalizedTerm) {
    ImmutableSet<String> catalogDimensions = dimensionCatalogStore.getDimensionNames(fleet);
    ImmutableList.Builder<String> matches = ImmutableList.builder();
    for (String dimName : catalogDimensions) {
      String keyId = DeviceKeys.dimensionKeyId(dimName);
      String bare = SuggesterEngine.normalize(dimName);
      String display = SuggesterEngine.normalize("Dimension " + dimName);
      if (display.startsWith(normalizedTerm) || bare.startsWith(normalizedTerm)) {
        matches.add(keyId);
      }
    }
    return matches.build();
  }

  @Override
  public ImmutableList<String> matchDiscoveredContainsKeys(Fleet fleet, String normalizedTerm) {
    ImmutableSet<String> catalogDimensions = dimensionCatalogStore.getDimensionNames(fleet);
    ImmutableList.Builder<String> matches = ImmutableList.builder();
    for (String dimName : catalogDimensions) {
      String keyId = DeviceKeys.dimensionKeyId(dimName);
      String bare = SuggesterEngine.normalize(dimName);
      String display = SuggesterEngine.normalize("Dimension " + dimName);
      if (display.contains(normalizedTerm) || bare.contains(normalizedTerm)) {
        matches.add(keyId);
      }
    }
    return matches.build();
  }

  @Override
  public boolean isColdLongTailKey(SearchCorpus corpus, String keyId) {
    if (corpus instanceof DeviceCorpus deviceCorpus) {
      return deviceCorpus.getKey(keyId).map(DeviceKeyDescriptor::isOverlay).orElse(false)
          && !corpus.index().keyIds().contains(keyId);
    }
    return false;
  }

  @Override
  public boolean isKeyKnown(SearchCorpus corpus, String keyId) {
    if (corpus instanceof DeviceCorpus deviceCorpus) {
      return deviceCorpus.getKey(keyId).isPresent();
    }
    return false;
  }

  @Override
  public boolean isPlural(SearchCorpus corpus, String keyId) {
    if (corpus instanceof DeviceCorpus deviceCorpus) {
      return deviceCorpus.getKey(keyId).map(k -> k.display().isPlural()).orElse(false);
    }
    return false;
  }

  @Override
  public String displayName(SearchCorpus corpus, String keyId) {
    if (corpus instanceof DeviceCorpus deviceCorpus) {
      return deviceCorpus.getKey(keyId).map(DeviceKeyDisplays::titleDisplayName).orElse(keyId);
    }
    return keyId;
  }

  @Override
  public String bareName(SearchCorpus corpus, String keyId) {
    if (corpus instanceof DeviceCorpus deviceCorpus) {
      return deviceCorpus.getKey(keyId).map(DeviceKeyDescriptor::bareName).orElse(keyId);
    }
    return keyId;
  }

  @Override
  public String pillKey(SearchCorpus corpus, String keyId) {
    if (corpus instanceof DeviceCorpus deviceCorpus) {
      return deviceCorpus.getKey(keyId).map(DeviceKeyDisplays::pillKey).orElse(keyId);
    }
    return keyId;
  }

  @Override
  public int keyPriority(SearchCorpus corpus, Fleet fleet, String keyId) {
    ScenarioCuration curation =
        curations.get(fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet);
    if (curation == null && corpus instanceof DeviceCorpus deviceCorpus) {
      curation = deviceCorpus.curation();
    }
    if (curation != null && corpus instanceof DeviceCorpus deviceCorpus) {
      return deviceCorpus.getKey(keyId).map(curation.keyPriority()::devicePriority).orElse(0);
    }
    return 0;
  }

  @Override
  public ImmutableList<String> groupByCandidates(SearchCorpus corpus, Fleet fleet) {
    ScenarioCuration curation =
        curations.get(fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet);
    if (curation == null && corpus instanceof DeviceCorpus deviceCorpus) {
      curation = deviceCorpus.curation();
    }
    if (curation != null) {
      return curation.deviceGroupByCandidates().stream()
          .map(DeviceKeyDescriptor::id)
          .collect(toImmutableList());
    }
    return ImmutableList.of(
        DeviceKeys.STATUS.id(),
        DeviceKeys.MODEL.id(),
        DeviceKeys.TYPE.id(),
        DeviceKeys.HOST_NAME.id());
  }

  private static ImmutableMap<String, ImmutableList<String>> buildDeviceAliasMap() {
    SetMultimap<String, String> map =
        MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
    addAliases(map, DeviceKeys.UUID.id(), "uuid", "id", "device id", "device uuid");
    addAliases(map, DeviceKeys.TYPE.id(), "type(s)", "device type(s)");
    addAliases(map, DeviceKeys.STATUS.id(), "status", "device status");
    addAliases(map, DeviceKeys.PREFIX_DEVICE_FIELD + "owner", "owner(s)", "device owner(s)");
    addAliases(
        map,
        DeviceKeys.DRIVER.id(),
        "driver(s)",
        "supported driver(s)",
        "device supported driver(s)");
    addAliases(
        map,
        DeviceKeys.DECORATOR.id(),
        "decorator(s)",
        "supported decorator(s)",
        "device supported decorator(s)");
    addAliases(
        map, DeviceKeys.PREFIX_DEVICE_FIELD + "executor", "executor(s)", "device executor(s)");
    addAliases(map, DeviceKeys.OS.id(), "os", "device os");
    addAliases(map, DeviceKeys.MODEL.id(), "model", "device model");
    addAliases(map, DeviceKeys.SDK_VERSION.id(), "sdk version", "version");
    addAliases(map, DeviceKeys.SOFTWARE_VERSION.id(), "software version", "version");
    addAliases(map, DeviceKeys.DEVICE_FORM.id(), "form", "device form");
    addAliases(map, DeviceKeys.PREFIX_DEVICE_FIELD + "quarantined", "quarantine", "quarantined");
    addAliases(
        map, DeviceKeys.DEVICE_CLASS_NAME.id(), "device class", "class", "device class name");
    addAliases(map, DeviceKeys.MANUFACTURER.id(), "manufacturer", "make", "brand");
    addAliases(map, DeviceKeys.PREFIX_DIMENSION + "pool", "pool", "device pool");
    addAliases(
        map,
        DeviceKeys.PREFIX_DIMENSION + "lab_location",
        "lab location",
        "device lab location",
        "location");
    addAliases(
        map,
        AtsDeviceKeys.WIFI_SSID.id(),
        "wifi",
        "wi-fi",
        "ssid",
        "wifi ssid",
        "wi-fi ssid",
        "network");
    addAliases(map, DeviceKeys.HOST_NAME.id(), "host name", "hostname", "host");

    ImmutableMap.Builder<String, ImmutableList<String>> built = ImmutableMap.builder();
    for (Map.Entry<String, Collection<String>> entry : map.asMap().entrySet()) {
      built.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
    }
    return built.buildOrThrow();
  }

  private static void addAliases(SetMultimap<String, String> map, String keyId, String... aliases) {
    for (String alias : aliases) {
      for (String expanded : expandPlural(alias)) {
        map.put(SuggesterEngine.normalize(expanded), keyId);
      }
    }
  }

  private static ImmutableList<String> expandPlural(String alias) {
    if (alias.contains("(s)")) {
      String base = alias.replace("(s)", "");
      return ImmutableList.of(base, base + "s");
    }
    return ImmutableList.of(alias);
  }
}
