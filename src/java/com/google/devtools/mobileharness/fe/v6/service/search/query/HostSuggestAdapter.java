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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.SuggestEntityAdapter.addAliases;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.SuggestEntityAdapter.normalize;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * {@link SuggestEntityAdapter} implementation for Host Search, binding the suggestion engine to
 * {@link HostCorpus} and the Host-only key schema.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Host Search queries lab host records ({@code host_field::*} and {@code host_property::*}).
 * Device fields ({@code device_field::*}), device dimensions ({@code dimension::*}), and long-tail
 * dimension catalogs do not exist on hosts and must never appear in Host Search suggestions. By
 * omitting all device schema and catalog dependencies from this adapter, domain isolation is
 * enforced by construction and verified by the compiler.
 *
 * <h2>How to use it</h2>
 *
 * <ol>
 *   <li>Instantiate once per Host Search suggestion request with the target {@link HostCorpus} and
 *       the fleet's {@link ScenarioCuration} (or {@code null} when unconfigured).
 *   <li>Pass the adapter into {@link FleetSuggester}'s request context so all key resolution,
 *       display formatting, and ranking operate exclusively on the host schema.
 * </ol>
 *
 * <h2>Host key semantics</h2>
 *
 * <ul>
 *   <li><b>Explicit namespace prefixes</b>: {@code host property <name>} resolves to {@code
 *       host_property::<name>}. Any {@code dimension:<name>} or {@code device dimension <name>}
 *       prefix is rejected immediately with an empty result.
 *   <li><b>Alias resolution</b>: Built-in aliases cover host fields including {@code host_name},
 *       {@code connectivity}, {@code lab_server_activity}, {@code daemon_status}, {@code
 *       release_status}, and {@code device_count}. Device fields and dimensions have no entries in
 *       this catalog.
 *   <li><b>Cold key-value fallback</b>: An unrecognized bare key operand in a key-value query falls
 *       back to {@code host_property::<normalized_key>}, whereas any {@code dimension:} prefix
 *       produces no fallback.
 * </ul>
 */
final class HostSuggestAdapter implements SuggestEntityAdapter {

  private static final Pattern NAMESPACE_DIM =
      Pattern.compile("^(?:device[ _])?dimension[ _:]+(.+)$");
  private static final Pattern NAMESPACE_PROP = Pattern.compile("^host[ _]?property[ _:]+(.+)$");

  private static final ImmutableMap<String, ImmutableList<String>> HOST_ALIAS_MAP =
      buildHostAliasMap();

  private final HostCorpus corpus;
  @Nullable private final ScenarioCuration curation;

  HostSuggestAdapter(HostCorpus corpus, @Nullable ScenarioCuration curation) {
    this.corpus = corpus;
    this.curation = curation;
  }

  @Override
  public boolean isKnownKey(String keyId) {
    return corpus.getKey(keyId).isPresent();
  }

  @Override
  public ImmutableList<String> resolveKey(String token, FleetIndex index) {
    String raw = token.trim();
    String low = Ascii.toLowerCase(raw);

    if (NAMESPACE_DIM.matcher(low).matches()) {
      return ImmutableList.of();
    }
    Matcher prop = NAMESPACE_PROP.matcher(low);
    if (prop.matches()) {
      String keyId = HostKeys.hostPropertyKeyId(normalize(prop.group(1)));
      return isKnownKey(keyId) ? ImmutableList.of(keyId) : ImmutableList.of();
    }
    ImmutableList<String> aliased = HOST_ALIAS_MAP.get(normalize(raw));
    if (aliased != null) {
      ImmutableList<String> validAliased =
          aliased.stream().filter(this::isKnownKey).collect(toImmutableList());
      if (!validAliased.isEmpty()) {
        return validAliased;
      }
    }
    String bareProp = HostKeys.hostPropertyKeyId(normalize(raw));
    if (index.keyIds().contains(bareProp) && isKnownKey(bareProp)) {
      return ImmutableList.of(bareProp);
    }
    return ImmutableList.of();
  }

  @Override
  public ImmutableList<String> fallbackKvKeyIds(String keyToken) {
    if (NAMESPACE_DIM.matcher(Ascii.toLowerCase(keyToken.trim())).matches()) {
      return ImmutableList.of();
    }
    String bareName = normalize(keyToken);
    if (bareName.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.of(HostKeys.PREFIX_HOST_PROPERTY + bareName);
  }

  @Override
  public boolean isColdUnindexedKey(String keyId, FleetIndex index) {
    return isKnownKey(keyId) && isHostProperty(keyId) && !index.keyIds().contains(keyId);
  }

  @Override
  public List<KeyMatch> matchKeys(String token, FleetIndex index) {
    List<KeyMatch> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String keyId : resolveKey(token, index)) {
      if (isKnownKey(keyId)
          && (index.keyIds().contains(keyId) || isHostProperty(keyId))
          && seen.add(keyId)) {
        out.add(new KeyMatch(keyId, 3));
      }
    }
    String normTerm = normalize(token);
    if (normTerm.isEmpty()) {
      return out;
    }
    for (String keyId : index.keyIds()) {
      if (seen.contains(keyId) || !isKnownKey(keyId)) {
        continue;
      }
      String display = normalize(titleDisplayName(keyId));
      String bare = normalize(bareName(keyId));
      if (display.startsWith(normTerm) || bare.startsWith(normTerm)) {
        out.add(new KeyMatch(keyId, 2));
        seen.add(keyId);
      }
    }
    for (String keyId : index.keyIds()) {
      if (seen.contains(keyId) || !isKnownKey(keyId)) {
        continue;
      }
      String display = normalize(titleDisplayName(keyId));
      String bare = normalize(bareName(keyId));
      if (display.contains(normTerm) || bare.contains(normTerm)) {
        out.add(new KeyMatch(keyId, 1));
        seen.add(keyId);
      }
    }
    return out;
  }

  @Override
  public String titleDisplayName(String keyId) {
    return corpus.getKey(keyId).map(HostKeyDisplays::titleDisplayName).orElse(keyId);
  }

  @Override
  public String bareName(String keyId) {
    return corpus.getKey(keyId).map(HostKeyDescriptor::bareName).orElse(keyId);
  }

  @Override
  public String pillKey(String keyId) {
    return corpus.getKey(keyId).map(HostKeyDisplays::pillKey).orElse(keyId);
  }

  @Override
  public boolean isPlural(String keyId) {
    return corpus.getKey(keyId).map(k -> k.display().isPlural()).orElse(false);
  }

  @Override
  public int keyPriority(String keyId) {
    if (curation == null || keyId == null) {
      return 0;
    }
    return corpus.getKey(keyId).map(curation.keyPriority()::hostPriority).orElse(0);
  }

  @Override
  public ImmutableList<String> defaultGroupByCandidates() {
    if (curation != null) {
      return curation.hostGroupByCandidates().stream()
          .map(HostKeyDescriptor::id)
          .collect(toImmutableList());
    }
    return ImmutableList.of(
        HostKeys.HOST_NAME.id(), HostKeys.CONNECTIVITY.id(), HostKeys.DEVICE_COUNT.id());
  }

  private boolean isHostProperty(String keyId) {
    return corpus.getKey(keyId).map(HostKeyDescriptor::isHostProperty).orElse(false);
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
}
