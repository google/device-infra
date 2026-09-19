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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
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
 * {@link SuggestEntityAdapter} implementation for Device Search, binding the suggestion engine to
 * {@link DeviceCorpus}, the Device alias catalog, and discovered long-tail dimensions from {@code
 * DimensionCatalogStore}.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Device Search spans four key namespaces: core device fields ({@code device_field::*}), device
 * dimensions ({@code dimension::*}), ATS device configuration keys ({@code device_config::*}), and
 * projected host attributes ({@code host_field::*} and {@code host_property::*}). In addition,
 * long-tail device dimensions discovered asynchronously by {@code DimensionCatalogStore} must be
 * searchable by name and filterable as cold conditions even before their posting overlays are
 * loaded into the active {@link FleetIndex}. This class isolates all device-specific key
 * resolution, catalog discovery, and display rules from both {@link FleetSuggester} and Host
 * Search.
 *
 * <h2>How to use it</h2>
 *
 * <ol>
 *   <li>Instantiate once per Device Search suggestion request with the target {@link DeviceCorpus},
 *       the fleet's {@link ScenarioCuration} (or {@code null} when unconfigured), and the
 *       discovered dimension names from {@code DimensionCatalogStore}.
 *   <li>Pass the adapter into {@link FleetSuggester}'s request context so all key-domain operations
 *       resolve strictly against the device schema.
 * </ol>
 *
 * <h2>Device key semantics</h2>
 *
 * <ul>
 *   <li><b>Explicit namespace prefixes</b>: Both {@code dimension:<name>} (or {@code device
 *       dimension <name>}) and {@code host property <name>} are recognized and resolved to {@code
 *       dimension::<name>} and {@code host_property::<name>} respectively.
 *   <li><b>Alias resolution</b>: Built-in aliases cover core device fields, promoted 1P dimensions
 *       ({@code pool}, {@code lab_location}), ATS Wi-Fi SSID, and projected host fields. Host-only
 *       metrics such as {@code host_field::device_count} are absent from this catalog.
 *   <li><b>Discovered long-tail dimensions</b>: {@link #matchKeys(String, FleetIndex)} scans both
 *       the live {@link FleetIndex#keyIds()} and the {@code catalogDimensions} set so unindexed
 *       dimensions appear in key-name suggestions at prefix (tier 2) and substring (tier 1) ranks.
 *   <li><b>Cold key-value fallback</b>: An unrecognized key operand in a key-value query falls back
 *       to {@code dimension::<normalized_key>} so users can filter on arbitrary long-tail
 *       dimensions immediately.
 * </ul>
 */
final class DeviceSuggestAdapter implements SuggestEntityAdapter {

  private static final Pattern NAMESPACE_DIM =
      Pattern.compile("^(?:device[ _])?dimension[ _:]+(.+)$");
  private static final Pattern NAMESPACE_PROP = Pattern.compile("^host[ _]?property[ _:]+(.+)$");

  private static final ImmutableMap<String, ImmutableList<String>> DEVICE_ALIAS_MAP =
      buildDeviceAliasMap();

  private final DeviceCorpus corpus;
  @Nullable private final ScenarioCuration curation;
  private final ImmutableSet<String> catalogDimensions;

  DeviceSuggestAdapter(
      DeviceCorpus corpus,
      @Nullable ScenarioCuration curation,
      ImmutableSet<String> catalogDimensions) {
    this.corpus = corpus;
    this.curation = curation;
    this.catalogDimensions = catalogDimensions;
  }

  @Override
  public boolean isKnownKey(String keyId) {
    return corpus.getKey(keyId).isPresent();
  }

  @Override
  public ImmutableList<String> resolveKey(String token, FleetIndex index) {
    String raw = token.trim();
    String low = Ascii.toLowerCase(raw);

    Matcher dim = NAMESPACE_DIM.matcher(low);
    if (dim.matches()) {
      String dimName = normalize(dim.group(1));
      String keyId = DeviceKeys.dimensionKeyId(dimName);
      return isKnownKey(keyId) ? ImmutableList.of(keyId) : ImmutableList.of();
    }
    Matcher prop = NAMESPACE_PROP.matcher(low);
    if (prop.matches()) {
      String keyId = HostKeys.hostPropertyKeyId(normalize(prop.group(1)));
      return isKnownKey(keyId) ? ImmutableList.of(keyId) : ImmutableList.of();
    }
    ImmutableList<String> aliased = DEVICE_ALIAS_MAP.get(normalize(raw));
    if (aliased != null) {
      ImmutableList<String> validAliased =
          aliased.stream().filter(this::isKnownKey).collect(toImmutableList());
      if (!validAliased.isEmpty()) {
        return validAliased;
      }
    }
    String bareDimName = normalize(raw);
    String bareDim = DeviceKeys.dimensionKeyId(bareDimName);
    if ((index.keyIds().contains(bareDim) || catalogDimensions.contains(bareDimName))
        && isKnownKey(bareDim)) {
      return ImmutableList.of(bareDim);
    }
    String bareProp = HostKeys.hostPropertyKeyId(normalize(raw));
    if (index.keyIds().contains(bareProp) && isKnownKey(bareProp)) {
      return ImmutableList.of(bareProp);
    }
    return ImmutableList.of();
  }

  @Override
  public ImmutableList<String> fallbackKvKeyIds(String keyToken) {
    String bareName = normalize(keyToken);
    if (bareName.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.of(DeviceKeys.PREFIX_DIMENSION + bareName);
  }

  @Override
  public boolean isColdUnindexedKey(String keyId, FleetIndex index) {
    return isKnownKey(keyId) && isDimensionOrProperty(keyId) && !index.keyIds().contains(keyId);
  }

  @Override
  public List<KeyMatch> matchKeys(String token, FleetIndex index) {
    List<KeyMatch> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String keyId : resolveKey(token, index)) {
      if (isKnownKey(keyId)
          && (index.keyIds().contains(keyId)
              || isDiscoveredDimension(keyId)
              || isDimensionOrProperty(keyId))
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
    for (String dimName : catalogDimensions) {
      String keyId = DeviceKeys.dimensionKeyId(dimName);
      if (seen.contains(keyId) || !isKnownKey(keyId)) {
        continue;
      }
      String display = normalize(titleDisplayName(keyId));
      String bare = normalize(dimName);
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
    for (String dimName : catalogDimensions) {
      String keyId = DeviceKeys.dimensionKeyId(dimName);
      if (seen.contains(keyId) || !isKnownKey(keyId)) {
        continue;
      }
      String display = normalize(titleDisplayName(keyId));
      String bare = normalize(dimName);
      if (display.contains(normTerm) || bare.contains(normTerm)) {
        out.add(new KeyMatch(keyId, 1));
        seen.add(keyId);
      }
    }
    return out;
  }

  @Override
  public String titleDisplayName(String keyId) {
    return corpus.getKey(keyId).map(DeviceKeyDisplays::titleDisplayName).orElse(keyId);
  }

  @Override
  public String bareName(String keyId) {
    return corpus.getKey(keyId).map(DeviceKeyDescriptor::bareName).orElse(keyId);
  }

  @Override
  public String pillKey(String keyId) {
    return corpus.getKey(keyId).map(DeviceKeyDisplays::pillKey).orElse(keyId);
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
    return corpus.getKey(keyId).map(curation.keyPriority()::devicePriority).orElse(0);
  }

  @Override
  public ImmutableList<String> defaultGroupByCandidates() {
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

  private boolean isDimensionOrProperty(String keyId) {
    return corpus.getKey(keyId).map(k -> k.isDimension() || k.isHostProperty()).orElse(false);
  }

  private boolean isDiscoveredDimension(String keyId) {
    return corpus
        .getKey(keyId)
        .filter(DeviceKeyDescriptor::isDimension)
        .map(d -> catalogDimensions.contains(d.bareName()))
        .orElse(false);
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
    // Projected host attributes available on every device record.
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

    ImmutableMap.Builder<String, ImmutableList<String>> built = ImmutableMap.builder();
    for (Map.Entry<String, Collection<String>> entry : map.asMap().entrySet()) {
      built.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
    }
    return built.buildOrThrow();
  }
}
