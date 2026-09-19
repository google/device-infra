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
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Dedicated search suggestion provider for host search queries over {@link HostCorpus}.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Host search operates strictly over host attributes ({@code host_field::}) and host properties
 * ({@code host_property::}). Hosts do not possess composite dimensions, and device dimensions
 * ({@code dimension::*}) must never appear in host search suggestions or filter pickers.
 *
 * <p>By physically omitting {@code DimensionCatalogStore} from {@link HostSuggester}'s constructor
 * and dependencies, this class guarantees at compile time that device dimension catalogs cannot
 * leak into host search.
 *
 * <h2>How to use it</h2>
 *
 * <p>Injected as a singleton and called by {@link FleetSuggester} when the target entity is {@code
 * SEARCH_ENTITY_HOST}:
 *
 * <pre>{@code
 * hostSuggester.suggest(hostCorpus, request);
 * }</pre>
 *
 * <h2>Host key domain and grammar</h2>
 *
 * <ul>
 *   <li><b>Aliases</b>: Maps host-specific synonyms (e.g. {@code hostname}, {@code connectivity},
 *       {@code device count}, {@code devices}, {@code release status}, {@code daemon status}) to
 *       their canonical host key IDs.
 *   <li><b>Explicit namespace prefixes</b>: Recognizes {@code host property:<name>}. Explicitly
 *       rejects {@code dimension:<name>} syntax.
 *   <li><b>Dynamic key minting</b>: Synthesizes {@code host_property::<name>} for unrecognized
 *       tokens in key-value filter conditions. Never synthesizes {@code dimension::*}.
 * </ul>
 */
@Singleton
public final class HostSuggester implements EntityKeyStrategy {

  private static final Pattern NAMESPACE_PROP = Pattern.compile("^host[ _]?property[ _:]+(.+)$");
  private static final Pattern NAMESPACE_DIM =
      Pattern.compile("^(?:device[ _])?dimension[ _:]+(.+)$");

  private static final ImmutableMap<String, ImmutableList<String>> HOST_ALIASES =
      buildHostAliasMap();

  private final SuggesterEngine engine;
  private final Map<Fleet, ScenarioCuration> curations;

  @Inject
  HostSuggester(SuggesterEngine engine, Map<Fleet, ScenarioCuration> curations) {
    this.engine = checkNotNull(engine);
    this.curations = checkNotNull(curations);
  }

  public HostSuggester(SuggesterEngine engine) {
    this(engine, ImmutableMap.of());
  }

  /** Generates ranked host search suggestions for the given corpus and request. */
  public FleetSuggestionResponse suggest(HostCorpus corpus, FleetSuggestionRequest request) {
    return engine.suggest(corpus, request, this);
  }

  @Override
  public ImmutableList<String> resolveKey(SearchCorpus corpus, String token) {
    String raw = token.trim();
    String low = Ascii.toLowerCase(raw);

    // Dimension namespace syntax is invalid in host search.
    if (NAMESPACE_DIM.matcher(low).matches()) {
      return ImmutableList.of();
    }

    Matcher prop = NAMESPACE_PROP.matcher(low);
    if (prop.matches()) {
      String keyId = HostKeys.hostPropertyKeyId(SuggesterEngine.normalize(prop.group(1)));
      return isKeyKnown(corpus, keyId) ? ImmutableList.of(keyId) : ImmutableList.of();
    }

    ImmutableList<String> aliased = HOST_ALIASES.get(SuggesterEngine.normalize(raw));
    if (aliased != null) {
      ImmutableList<String> valid =
          aliased.stream().filter(keyId -> isKeyKnown(corpus, keyId)).collect(toImmutableList());
      if (!valid.isEmpty()) {
        return valid;
      }
    }

    String bareProp = HostKeys.hostPropertyKeyId(SuggesterEngine.normalize(raw));
    if (corpus.index().keyIds().contains(bareProp) && isKeyKnown(corpus, bareProp)) {
      return ImmutableList.of(bareProp);
    }

    return ImmutableList.of();
  }

  @Override
  public Optional<String> synthesizeDynamicKeyId(String bareToken) {
    if (NAMESPACE_DIM.matcher(Ascii.toLowerCase(bareToken.trim())).matches()) {
      return Optional.empty();
    }
    String bareName = SuggesterEngine.normalize(bareToken);
    return bareName.isEmpty()
        ? Optional.empty()
        : Optional.of(HostKeys.PREFIX_HOST_PROPERTY + bareName);
  }

  @Override
  public ImmutableList<String> matchDiscoveredPrefixKeys(Fleet fleet, String normalizedTerm) {
    // Hosts do not have an external dimension catalog.
    return ImmutableList.of();
  }

  @Override
  public ImmutableList<String> matchDiscoveredContainsKeys(Fleet fleet, String normalizedTerm) {
    // Hosts do not have an external dimension catalog.
    return ImmutableList.of();
  }

  @Override
  public boolean isColdLongTailKey(SearchCorpus corpus, String keyId) {
    if (corpus instanceof HostCorpus hostCorpus) {
      return hostCorpus.getKey(keyId).map(HostKeyDescriptor::isHostProperty).orElse(false)
          && !corpus.index().keyIds().contains(keyId);
    }
    return false;
  }

  @Override
  public boolean isKeyKnown(SearchCorpus corpus, String keyId) {
    if (corpus instanceof HostCorpus hostCorpus) {
      return hostCorpus.getKey(keyId).isPresent();
    }
    return false;
  }

  @Override
  public boolean isPlural(SearchCorpus corpus, String keyId) {
    if (corpus instanceof HostCorpus hostCorpus) {
      return hostCorpus.getKey(keyId).map(k -> k.display().isPlural()).orElse(false);
    }
    return false;
  }

  @Override
  public String displayName(SearchCorpus corpus, String keyId) {
    if (corpus instanceof HostCorpus hostCorpus) {
      return hostCorpus.getKey(keyId).map(HostKeyDisplays::titleDisplayName).orElse(keyId);
    }
    return keyId;
  }

  @Override
  public String bareName(SearchCorpus corpus, String keyId) {
    if (corpus instanceof HostCorpus hostCorpus) {
      return hostCorpus.getKey(keyId).map(HostKeyDescriptor::bareName).orElse(keyId);
    }
    return keyId;
  }

  @Override
  public String pillKey(SearchCorpus corpus, String keyId) {
    if (corpus instanceof HostCorpus hostCorpus) {
      return hostCorpus.getKey(keyId).map(HostKeyDisplays::pillKey).orElse(keyId);
    }
    return keyId;
  }

  @Override
  public int keyPriority(SearchCorpus corpus, Fleet fleet, String keyId) {
    ScenarioCuration curation =
        curations.get(fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet);
    if (curation == null && corpus instanceof HostCorpus hostCorpus) {
      curation = hostCorpus.curation();
    }
    if (curation != null && corpus instanceof HostCorpus hostCorpus) {
      return hostCorpus.getKey(keyId).map(curation.keyPriority()::hostPriority).orElse(0);
    }
    return 0;
  }

  @Override
  public ImmutableList<String> groupByCandidates(SearchCorpus corpus, Fleet fleet) {
    ScenarioCuration curation =
        curations.get(fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet);
    if (curation == null && corpus instanceof HostCorpus hostCorpus) {
      curation = hostCorpus.curation();
    }
    if (curation != null) {
      return curation.hostGroupByCandidates().stream()
          .map(HostKeyDescriptor::id)
          .collect(toImmutableList());
    }
    return ImmutableList.of(
        HostKeys.HOST_NAME.id(), HostKeys.CONNECTIVITY.id(), HostKeys.DEVICE_COUNT.id());
  }

  private static ImmutableMap<String, ImmutableList<String>> buildHostAliasMap() {
    SetMultimap<String, String> map =
        MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
    addAliases(map, HostKeys.HOST_NAME.id(), "host name", "hostname", "host");
    addAliases(map, HostKeys.HOST_IP.id(), "host ip", "ip");
    addAliases(map, HostKeys.HOST_OS.id(), "host os");
    addAliases(map, HostKeys.PREFIX_HOST_FIELD + "lab_type", "lab type");
    addAliases(map, HostKeys.CONNECTIVITY.id(), "connectivity", "lab server connectivity");
    addAliases(map, "host_field::lab_server_activity", "activity", "lab server activity");
    addAliases(
        map,
        HostKeys.PREFIX_HOST_FIELD + "daemon_status",
        "daemon",
        "daemon status",
        "daemon server status");
    addAliases(
        map,
        HostKeys.PREFIX_HOST_FIELD + "daemon_server_version",
        "daemon version",
        "daemon server version",
        "host daemon server version");
    addAliases(map, HostKeys.PREFIX_HOST_FIELD + "release_status", "release status", "release");
    addAliases(map, HostKeys.LAB_SERVER_VERSION.id(), "lab server version");
    addAliases(
        map, HostKeys.PREFIX_HOST_FIELD + "release_type", "release type", "host release type");
    addAliases(
        map,
        HostKeys.PREFIX_HOST_FIELD + "ats_lab_display_name",
        "ats lab",
        "lab",
        "lab name",
        "ats lab name");
    addAliases(
        map,
        HostKeys.PREFIX_HOST_FIELD + "ats_controller_id",
        "controller",
        "controller id",
        "ats controller",
        "ats controller id");
    addAliases(map, HostKeys.DEVICE_COUNT.id(), "device count", "device_count", "devices");

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
