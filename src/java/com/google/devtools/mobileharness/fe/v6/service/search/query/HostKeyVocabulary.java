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
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * The {@link KeyVocabulary} of host search.
 *
 * <p>Host search understands two families of keys, and this class is the one place that knows how
 * each is named, discovered, displayed and ranked:
 *
 * <ul>
 *   <li>Curated host fields and properties registered by the deployment's {@link HostKeyRegistry}.
 *   <li>Long-tail host properties discovered from data and present in the host index.
 * </ul>
 *
 * <p>There is no dimension family: dimensions belong to devices. A token spelled {@code
 * dimension:<x>} resolves to nothing here, and the fleet-wide dimension catalog is never consulted.
 * Entity purity is enforced by construction: every descriptor this class returns comes from {@link
 * HostKeyRegistry}, which only recognizes {@code host_field::} and {@code host_property::}, so a
 * device key can never come out, whatever the typed text says. The alias table is derived from the
 * registry's own keys, so an alias exists in a deployment exactly when its key does. Methods that
 * take a descriptor look it up by id in the same registry. This class never forms a key id: bare
 * names typed by the user go through the registry's named lookup.
 *
 * <p>Discovered property names are matched on their {@link KeyTokens#normalize normalized} form,
 * because the index keeps the casing the lab reported while the user types whatever comes
 * naturally. The descriptor returned carries the id as indexed, so filters built from it match the
 * data. When two discovered names normalize to the same form, the first in index order wins.
 *
 * <p>Instances are bound to one corpus and created by {@link HostCorpus}; the alias table they
 * consult is shared across corpora of the same fleet.
 */
final class HostKeyVocabulary implements KeyVocabulary {

  /** Group-by keys offered for a bare {@code group by} when no curation is installed. */
  private static final ImmutableList<KeyDescriptor> DEFAULT_GROUP_BY_KEYS =
      ImmutableList.of(HostKeys.HOST_NAME, HostKeys.CONNECTIVITY, HostKeys.DEVICE_COUNT);

  private final HostKeyRegistry registry;
  private final KeyAliasTable aliases;
  private final FleetIndex index;
  @Nullable private final ScenarioCuration curation;

  /** Built on first use, since a request that never types a bare name never needs it. */
  private final Supplier<ImmutableMap<String, HostKeyDescriptor>> discoveredProperties =
      Suppliers.memoize(this::indexDiscoveredProperties);

  /**
   * @param registry the deployment's host key registry; the sole authority on which keys exist
   * @param aliases the spellings of the registry's built-in keys, built once per fleet
   * @param index the host corpus index, used to decide which bare tokens name an indexed key
   * @param curation the fleet's curation for ranking and group-by candidates, or null before one is
   *     installed, in which case every key ranks equally and a fixed default set of group-by
   *     candidates is offered
   */
  HostKeyVocabulary(
      HostKeyRegistry registry,
      KeyAliasTable aliases,
      FleetIndex index,
      @Nullable ScenarioCuration curation) {
    this.registry = checkNotNull(registry);
    this.aliases = checkNotNull(aliases);
    this.index = checkNotNull(index);
    this.curation = curation;
  }

  @Override
  public SearchEntity entity() {
    return SearchEntity.SEARCH_ENTITY_HOST;
  }

  @Override
  public ImmutableList<KeyDescriptor> resolve(String token) {
    // 1. Explicit namespace: only the host-property namespace belongs to host search. A name the
    // fleet has not discovered is still minted, so a filter on it can be typed ahead of data.
    Optional<String> property = KeyTokens.hostPropertyName(token);
    if (property.isPresent()) {
      return asList(
          discoveredProperty(property.get()).or(() -> registry.hostPropertyKey(property.get())));
    }

    // 2. Display name or alias of a key this deployment registers, in registry order.
    ImmutableList<KeyDescriptor> aliased = aliases.lookup(token);
    if (!aliased.isEmpty()) {
      return aliased;
    }

    // 3. Bare token naming a discovered host property.
    return asList(discoveredProperty(KeyTokens.normalize(token)));
  }

  @Override
  public ImmutableSet<KeyDescriptor> discoverableKeys() {
    return index.keyIds().stream()
        .flatMap(keyId -> describe(keyId).stream())
        .collect(toImmutableSet());
  }

  @Override
  public Optional<KeyDescriptor> describe(String keyId) {
    return registry.getKey(keyId).map(key -> key);
  }

  @Override
  public String titleDisplayName(KeyDescriptor key) {
    return HostKeyDisplays.titleDisplayName(own(key));
  }

  @Override
  public String pillKey(KeyDescriptor key) {
    return HostKeyDisplays.pillKey(own(key));
  }

  @Override
  public boolean isColdCapable(KeyDescriptor key) {
    return own(key).isHostProperty();
  }

  @Override
  public int priority(KeyDescriptor key) {
    return curation == null ? 0 : curation.keyPriority().hostPriority(own(key));
  }

  @Override
  public ImmutableList<KeyDescriptor> groupByCandidates() {
    ImmutableList<? extends KeyDescriptor> candidates =
        curation == null ? DEFAULT_GROUP_BY_KEYS : curation.hostGroupByCandidates();
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
    return registry.hostPropertyKey(KeyTokens.normalize(bareName)).map(key -> key);
  }

  private Optional<HostKeyDescriptor> discoveredProperty(String normalizedName) {
    return Optional.ofNullable(discoveredProperties.get().get(normalizedName));
  }

  private ImmutableMap<String, HostKeyDescriptor> indexDiscoveredProperties() {
    return index.keyIds().stream()
        .flatMap(keyId -> registry.getKey(keyId).stream())
        .filter(HostKeyDescriptor::isHostProperty)
        .collect(
            toImmutableMap(
                key -> KeyTokens.normalize(key.bareName()), key -> key, (first, second) -> first));
  }

  /** The key as a one-element list if present, otherwise empty. */
  private static ImmutableList<KeyDescriptor> asList(Optional<HostKeyDescriptor> key) {
    return key.map(ImmutableList::<KeyDescriptor>of).orElse(ImmutableList.of());
  }

  /**
   * This entity's own descriptor for the key; a key host search does not know is a programming
   * error.
   */
  private HostKeyDescriptor own(KeyDescriptor key) {
    return registry
        .getKey(key.id())
        .orElseThrow(() -> new IllegalArgumentException(key.id() + " is not a host search key"));
  }
}
