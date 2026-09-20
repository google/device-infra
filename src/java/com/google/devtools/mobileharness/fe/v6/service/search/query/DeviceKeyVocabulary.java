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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * The {@link KeyVocabulary} of device search.
 *
 * <p>Device search understands three families of keys, and this class is the one place that knows
 * how each is named, discovered, displayed and ranked:
 *
 * <ul>
 *   <li>Curated keys registered by the deployment's {@link DeviceKeyRegistry}: device fields, core
 *       dimensions, configuration keys, and host fields projected onto devices.
 *   <li>Long-tail dimensions discovered from data: those present in the index, plus those the
 *       {@code DimensionCatalogStore} has seen fleet-wide but which are pulled on demand and so may
 *       be absent from the index. Only device search has this second source.
 *   <li>Long-tail host properties projected onto devices, present in the index.
 * </ul>
 *
 * <p>Entity purity is enforced by construction: every descriptor this class returns comes from
 * {@link DeviceKeyRegistry}, which only recognizes device-search namespaces, so a host-only key
 * such as {@code host_field::device_count} can never come out, whatever the typed text says. The
 * alias table is derived from the registry's own keys, so an alias exists in a deployment exactly
 * when its key does. Methods that take a descriptor look it up by id in the same registry, so even
 * a descriptor from another entity yields this entity's view of that key. This class never forms a
 * key id: bare names typed by the user go through the registry's named lookups.
 *
 * <p>Discovered names are matched on their {@link KeyTokens#normalize normalized} form, because the
 * index and the catalog keep the casing the lab reported ({@code Rack_Slot}) while the user types
 * whatever comes naturally ({@code rack slot}). The descriptor returned carries the id as indexed,
 * so filters built from it match the data. When two discovered names normalize to the same form,
 * the first in index order wins.
 *
 * <p>Instances are bound to one corpus and created by {@link DeviceCorpus}; the alias table they
 * consult is shared across corpora of the same fleet.
 */
final class DeviceKeyVocabulary implements KeyVocabulary {

  /** Group-by keys offered for a bare {@code group by} when no curation is installed. */
  private static final ImmutableList<KeyDescriptor> DEFAULT_GROUP_BY_KEYS =
      ImmutableList.of(DeviceKeys.STATUS, DeviceKeys.MODEL, DeviceKeys.TYPE, DeviceKeys.HOST_NAME);

  private final DeviceKeyRegistry registry;
  private final KeyAliasTable aliases;
  private final FleetIndex index;
  private final ImmutableSet<String> catalogDimensionNames;
  @Nullable private final ScenarioCuration curation;

  /** Built on first use, since a request that never types a bare name never needs it. */
  private final Supplier<DiscoveredNames> discovered =
      Suppliers.memoize(this::indexDiscoveredNames);

  /** Discovered keys by normalized name, one map per long-tail family. */
  private record DiscoveredNames(
      ImmutableMap<String, DeviceKeyDescriptor> dimensions,
      ImmutableMap<String, DeviceKeyDescriptor> hostProperties) {}

  /**
   * @param registry the deployment's device key registry; the sole authority on which keys exist
   * @param aliases the spellings of the registry's built-in keys, built once per fleet
   * @param index the corpus index, used to decide which bare tokens name an indexed key
   * @param catalogDimensionNames dimension names discovered fleet-wide but possibly unindexed
   * @param curation the fleet's curation for ranking and group-by candidates, or null before one is
   *     installed, in which case every key ranks equally and a fixed default set of group-by
   *     candidates is offered
   */
  DeviceKeyVocabulary(
      DeviceKeyRegistry registry,
      KeyAliasTable aliases,
      FleetIndex index,
      ImmutableSet<String> catalogDimensionNames,
      @Nullable ScenarioCuration curation) {
    this.registry = checkNotNull(registry);
    this.aliases = checkNotNull(aliases);
    this.index = checkNotNull(index);
    this.catalogDimensionNames = checkNotNull(catalogDimensionNames);
    this.curation = curation;
  }

  @Override
  public SearchEntity entity() {
    return SearchEntity.SEARCH_ENTITY_DEVICE;
  }

  @Override
  public ImmutableList<KeyDescriptor> resolve(String token) {
    // 1. Explicit namespace: the user named the family, so only that family is consulted. A name
    // the fleet has not discovered is still minted, so a filter on it can be typed ahead of data.
    Optional<String> dimension = KeyTokens.dimensionName(token);
    if (dimension.isPresent()) {
      return asList(
          discoveredDimension(dimension.get()).or(() -> registry.dimensionKey(dimension.get())));
    }
    Optional<String> property = KeyTokens.hostPropertyName(token);
    if (property.isPresent()) {
      return asList(
          discoveredHostProperty(property.get())
              .or(() -> registry.hostPropertyKey(property.get())));
    }

    // 2. Display name or alias of a key this deployment registers, in registry order.
    ImmutableList<KeyDescriptor> aliased = aliases.lookup(token);
    if (!aliased.isEmpty()) {
      return aliased;
    }

    // 3. Bare token naming a discovered dimension, else a discovered host property.
    String bare = KeyTokens.normalize(token);
    return asList(discoveredDimension(bare).or(() -> discoveredHostProperty(bare)));
  }

  @Override
  public ImmutableSet<KeyDescriptor> discoverableKeys() {
    Stream<KeyDescriptor> indexed =
        index.keyIds().stream().flatMap(keyId -> describe(keyId).stream());
    Stream<KeyDescriptor> cataloged =
        catalogDimensionNames.stream().flatMap(name -> registry.dimensionKey(name).stream());
    return Stream.concat(indexed, cataloged).collect(toImmutableSet());
  }

  @Override
  public Optional<KeyDescriptor> describe(String keyId) {
    return registry.getKey(keyId).map(key -> key);
  }

  @Override
  public String titleDisplayName(KeyDescriptor key) {
    return DeviceKeyDisplays.titleDisplayName(own(key));
  }

  @Override
  public String pillKey(KeyDescriptor key) {
    return DeviceKeyDisplays.pillKey(own(key));
  }

  @Override
  public boolean isColdCapable(KeyDescriptor key) {
    DeviceKeyDescriptor owned = own(key);
    return owned.isDimension() || owned.isHostProperty();
  }

  @Override
  public int priority(KeyDescriptor key) {
    return curation == null ? 0 : curation.keyPriority().devicePriority(own(key));
  }

  @Override
  public ImmutableList<KeyDescriptor> groupByCandidates() {
    ImmutableList<? extends KeyDescriptor> candidates =
        curation == null ? DEFAULT_GROUP_BY_KEYS : curation.deviceGroupByCandidates();
    // Resolved through the registry so the class invariant holds even if a curation names a key
    // this deployment does not register.
    return candidates.stream()
        .flatMap(key -> describe(key.id()).stream())
        .collect(toImmutableList());
  }

  @Override
  public Optional<KeyDescriptor> mintLongTailKey(String bareName) {
    if (KeyTokens.isNamespaced(bareName)) {
      return Optional.empty();
    }
    return registry.dimensionKey(KeyTokens.normalize(bareName)).map(key -> key);
  }

  private Optional<DeviceKeyDescriptor> discoveredDimension(String normalizedName) {
    return Optional.ofNullable(discovered.get().dimensions().get(normalizedName));
  }

  private Optional<DeviceKeyDescriptor> discoveredHostProperty(String normalizedName) {
    return Optional.ofNullable(discovered.get().hostProperties().get(normalizedName));
  }

  private DiscoveredNames indexDiscoveredNames() {
    ImmutableList<DeviceKeyDescriptor> indexed =
        index.keyIds().stream()
            .flatMap(keyId -> registry.getKey(keyId).stream())
            .collect(toImmutableList());
    ImmutableMap<String, DeviceKeyDescriptor> dimensions =
        Stream.concat(
                indexed.stream().filter(DeviceKeyDescriptor::isDimension),
                catalogDimensionNames.stream()
                    .flatMap(name -> registry.dimensionKey(name).stream()))
            .collect(
                toImmutableMap(
                    DeviceKeyVocabulary::normalizedName, key -> key, (first, second) -> first));
    ImmutableMap<String, DeviceKeyDescriptor> hostProperties =
        indexed.stream()
            .filter(DeviceKeyDescriptor::isHostProperty)
            .collect(
                toImmutableMap(
                    DeviceKeyVocabulary::normalizedName, key -> key, (first, second) -> first));
    return new DiscoveredNames(dimensions, hostProperties);
  }

  private static String normalizedName(DeviceKeyDescriptor key) {
    return KeyTokens.normalize(key.bareName());
  }

  /** The key as a one-element list if present, otherwise empty. */
  private static ImmutableList<KeyDescriptor> asList(Optional<DeviceKeyDescriptor> key) {
    return key.map(ImmutableList::<KeyDescriptor>of).orElse(ImmutableList.of());
  }

  /**
   * This entity's own descriptor for the key. Looking the key up by id rather than casting means a
   * projected host key is answered with its device-search display, and a key this entity does not
   * know is a programming error rather than a silent fallback.
   */
  private DeviceKeyDescriptor own(KeyDescriptor key) {
    return registry
        .getKey(key.id())
        .orElseThrow(() -> new IllegalArgumentException(key.id() + " is not a device search key"));
  }
}
