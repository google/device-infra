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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostConnectivityStatuses;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Builds an immutable {@link FleetSnapshot} (forward store with unified values map plus {@link
 * FleetIndex}) from {@link CoreFleetRawData}.
 *
 * <p>This is a pure function: raw data in, snapshot out, no RPC. Keyed by canonical namespaced key
 * IDs (e.g. {@code device_field::*}, {@code dimension::*}, {@code host_field::*}, {@code
 * host_property::*}, {@code device_config::*}).
 */
public final class FleetIndexBuilder {

  /** Host property that carries the lab server version, when present. */
  private static final String HOST_VERSION_PROPERTY = "host_version";

  // Standard 1P canonical key IDs used during forward values synthesis.
  public static final String DEVICE_FIELD_OWNER = DeviceKeys.PREFIX_DEVICE_FIELD + "owner";
  public static final String DEVICE_FIELD_EXECUTOR = DeviceKeys.PREFIX_DEVICE_FIELD + "executor";
  public static final String DEVICE_FIELD_QUARANTINED =
      DeviceKeys.PREFIX_DEVICE_FIELD + "quarantined";
  public static final String HOST_FIELD_DAEMON_STATUS =
      HostKeys.PREFIX_HOST_FIELD + "daemon_status";
  public static final String HOST_FIELD_DAEMON_SERVER_VERSION =
      HostKeys.PREFIX_HOST_FIELD + "daemon_server_version";
  public static final String HOST_FIELD_RELEASE_STATUS =
      HostKeys.PREFIX_HOST_FIELD + "release_status";
  public static final String HOST_FIELD_RELEASE_TYPE = HostKeys.PREFIX_HOST_FIELD + "release_type";
  public static final String HOST_FIELD_ATS_LAB_DISPLAY_NAME =
      HostKeys.PREFIX_HOST_FIELD + "ats_lab_display_name";
  public static final String HOST_FIELD_ATS_CONTROLLER_ID =
      HostKeys.PREFIX_HOST_FIELD + "ats_controller_id";

  /**
   * Dimension names excluded from forward values and indexing. These dimensions carry non-textual
   * data (e.g. serialized binary protos, base64 blobs) that have no search, filter, column, or
   * group-by utility and would needlessly bloat the in-memory index.
   */
  private static final ImmutableSet<String> EXCLUDED_DIMENSIONS =
      ImmutableSet.of("subdevice_dimensions");

  private static final ImmutableSet<String> IDENTIFIER_KEYS =
      ImmutableSet.of(DeviceKeys.UUID.id(), HostKeys.HOST_NAME.id(), HostKeys.HOST_IP.id());

  @Inject
  FleetIndexBuilder() {}

  /** Builds a snapshot from lab data alone, with no host or device enrichment. */
  public FleetSnapshot build(LabQueryResult labData, Instant buildTime) {
    return build(CoreFleetRawData.ofLabData(labData), buildTime);
  }

  /** Builds a snapshot from the raw data, stamping the given build time. */
  public FleetSnapshot build(CoreFleetRawData raw, Instant buildTime) {
    LabQueryResult labResult = raw.labData();

    ImmutableList<LabData> labDataList =
        labResult.hasLabView()
            ? ImmutableList.copyOf(labResult.getLabView().getLabDataList())
            : ImmutableList.of();

    record HostDevices(HostRecord host, List<DeviceRecord> devices) {}

    ConcurrentMap<Thread, Accumulator> accumulatorsByThread = new ConcurrentHashMap<>();
    ConcurrentMap<Thread, Accumulator> hostAccumulatorsByThread = new ConcurrentHashMap<>();

    List<HostDevices> hostDevices =
        labDataList.parallelStream()
            .map(
                labData -> {
                  LabInfo labInfo = labData.getLabInfo();
                  LabLocator locator = labInfo.getLabLocator();
                  String hostName = locator.getHostName();
                  String hostIp = locator.getIp();
                  if (hostIp.isEmpty() && locator.hasMasterDetectedIp()) {
                    hostIp = locator.getMasterDetectedIp();
                  }
                  ImmutableMap<String, String> hostProperties = extractHostProperties(labInfo);
                  Optional<HostEnrichment> hostEnrichment =
                      Optional.ofNullable(raw.hostEnrichments().get(hostName));

                  String hostOs = hostProperties.getOrDefault("host_os", "Unknown");
                  String hostConnectivity =
                      HostConnectivityStatuses.create(Optional.of(labInfo)).getTitle();

                  DeviceList deviceList = labData.getDeviceList();
                  HostRecord hostRecord =
                      buildHostRecord(
                          hostName,
                          hostIp,
                          hostProperties,
                          deviceList,
                          hostOs,
                          hostConnectivity,
                          hostEnrichment);
                  Accumulator hostAccum =
                      hostAccumulatorsByThread.computeIfAbsent(
                          Thread.currentThread(), t -> new Accumulator());
                  indexHost(hostAccum, hostRecord);

                  Accumulator accum =
                      accumulatorsByThread.computeIfAbsent(
                          Thread.currentThread(), t -> new Accumulator());
                  List<DeviceRecord> devices = new ArrayList<>();
                  for (DeviceInfo deviceInfo : deviceList.getDeviceInfoList()) {
                    String deviceId = deviceInfo.getDeviceLocator().getId();
                    Optional<DeviceEnrichment> deviceEnrichment =
                        Optional.ofNullable(raw.deviceEnrichments().get(deviceId));
                    DeviceRecord record =
                        buildDeviceRecord(deviceInfo, hostRecord, deviceEnrichment);
                    devices.add(record);
                    indexDevice(accum, record);
                  }
                  return new HostDevices(hostRecord, devices);
                })
            .toList();

    List<DeviceRecord> allDevices = new ArrayList<>();
    ImmutableList.Builder<HostRecord> allHosts = ImmutableList.builder();
    ImmutableMap.Builder<String, Integer> uuidToIndex = ImmutableMap.builder();
    int devIdx = 0;
    for (HostDevices hd : hostDevices) {
      allHosts.add(hd.host());
      for (DeviceRecord device : hd.devices()) {
        allDevices.add(device);
        if (!device.deviceId().isEmpty()) {
          uuidToIndex.put(device.deviceId(), devIdx);
        }
        devIdx++;
      }
    }

    Accumulator merged = new Accumulator();
    for (Accumulator a : accumulatorsByThread.values()) {
      merged.mergeFrom(a);
    }

    Accumulator hostMerged = new Accumulator();
    for (Accumulator a : hostAccumulatorsByThread.values()) {
      hostMerged.mergeFrom(a);
    }

    return FleetSnapshot.builder()
        .setBuildTime(buildTime)
        .setDevices(ImmutableList.copyOf(allDevices))
        .setHosts(allHosts.build())
        .setIndex(merged.toIndex())
        .setHostIndex(hostMerged.toIndex())
        .setUuidToIndex(uuidToIndex.buildKeepingLast())
        .build();
  }

  private static HostRecord buildHostRecord(
      String hostName,
      String hostIp,
      ImmutableMap<String, String> hostProperties,
      DeviceList deviceList,
      String hostOs,
      String hostConnectivity,
      Optional<HostEnrichment> enrichment) {
    ImmutableMap.Builder<String, ImmutableList<String>> values = ImmutableMap.builder();
    // Added before core host keys so core keys take precedence under buildKeepingLast().
    enrichment.map(HostEnrichment::hostFields).ifPresent(values::putAll);

    if (!hostName.isEmpty()) {
      values.put(HostKeys.HOST_NAME.id(), ImmutableList.of(hostName));
    }
    if (!hostIp.isEmpty()) {
      values.put(HostKeys.HOST_IP.id(), ImmutableList.of(hostIp));
    }
    values
        .put(HostKeys.HOST_OS.id(), ImmutableList.of(hostOs))
        .put(HostKeys.CONNECTIVITY.id(), ImmutableList.of(hostConnectivity));
    enrichment
        .flatMap(HostEnrichment::daemonStatus)
        .filter(s -> !s.isEmpty())
        .ifPresent(s -> values.put(HOST_FIELD_DAEMON_STATUS, ImmutableList.of(s)));
    enrichment
        .flatMap(HostEnrichment::daemonServerVersion)
        .filter(v -> !v.isEmpty())
        .ifPresent(v -> values.put(HOST_FIELD_DAEMON_SERVER_VERSION, ImmutableList.of(v)));
    enrichment
        .flatMap(HostEnrichment::releaseStatus)
        .filter(s -> !s.isEmpty())
        .ifPresent(s -> values.put(HOST_FIELD_RELEASE_STATUS, ImmutableList.of(s)));
    enrichment
        .flatMap(HostEnrichment::releaseType)
        .filter(s -> !s.isEmpty())
        .ifPresent(s -> values.put(HOST_FIELD_RELEASE_TYPE, ImmutableList.of(s)));
    Optional<String> labServerVersion =
        enrichment.flatMap(HostEnrichment::labServerVersion).or(() -> hostVersion(hostProperties));
    labServerVersion
        .filter(v -> !v.isEmpty())
        .ifPresent(v -> values.put(HostKeys.LAB_SERVER_VERSION.id(), ImmutableList.of(v)));
    enrichment
        .flatMap(HostEnrichment::atsLabDisplayName)
        .filter(c -> !c.isEmpty())
        .ifPresent(c -> values.put(HOST_FIELD_ATS_LAB_DISPLAY_NAME, ImmutableList.of(c)));
    enrichment
        .flatMap(HostEnrichment::atsControllerId)
        .filter(c -> !c.isEmpty())
        .ifPresent(c -> values.put(HOST_FIELD_ATS_CONTROLLER_ID, ImmutableList.of(c)));
    values.put(
        HostKeys.DEVICE_COUNT.id(),
        ImmutableList.of(String.valueOf(deviceList.getDeviceInfoCount())));

    for (Map.Entry<String, String> entry : hostProperties.entrySet()) {
      if (entry.getKey().equals("host_os")) {
        continue;
      }
      values.put(
          HostKeys.PREFIX_HOST_PROPERTY + entry.getKey(), ImmutableList.of(entry.getValue()));
    }

    return HostRecord.create(hostName, values.buildKeepingLast(), deviceList.getDeviceInfoCount());
  }

  private static DeviceRecord buildDeviceRecord(
      DeviceInfo deviceInfo, HostRecord host, Optional<DeviceEnrichment> enrichment) {
    DeviceFeature feature = deviceInfo.getDeviceFeature();
    DeviceCondition condition = deviceInfo.getDeviceCondition();
    String deviceId = deviceInfo.getDeviceLocator().getId();

    ImmutableMap.Builder<String, ImmutableList<String>> values = ImmutableMap.builder();

    if (!deviceId.isEmpty()) {
      values.put(DeviceKeys.UUID.id(), ImmutableList.of(deviceId));
    }
    String status = deviceInfo.getDeviceStatus().name();
    if (!status.isEmpty()) {
      values.put(DeviceKeys.STATUS.id(), ImmutableList.of(status));
    }
    ImmutableList<String> types = nonEmptyList(feature.getTypeList());
    if (!types.isEmpty()) {
      values.put(DeviceKeys.TYPE.id(), types);
    }
    ImmutableList<String> owners = nonEmptyList(feature.getOwnerList());
    if (!owners.isEmpty()) {
      values.put(DEVICE_FIELD_OWNER, owners);
    }
    ImmutableList<String> drivers = nonEmptyList(feature.getDriverList());
    if (!drivers.isEmpty()) {
      values.put(DeviceKeys.DRIVER.id(), drivers);
    }
    ImmutableList<String> decorators = nonEmptyList(feature.getDecoratorList());
    if (!decorators.isEmpty()) {
      values.put(DeviceKeys.DECORATOR.id(), decorators);
    }
    ImmutableList<String> executors = nonEmptyList(feature.getExecutorList());
    if (!executors.isEmpty()) {
      values.put(DEVICE_FIELD_EXECUTOR, executors);
    }

    values.put(DEVICE_FIELD_QUARANTINED, ImmutableList.of(isQuarantined(condition) ? "Yes" : "No"));
    addSdkOrSoftwareVersion(values, deviceInfo);

    // Dimensions: append supported and required directly without deduplicating.
    addDimensions(values, feature.getCompositeDimension());

    // Host attributes projected onto device.
    for (Map.Entry<String, ImmutableList<String>> entry : host.values().entrySet()) {
      String keyId = entry.getKey();
      // device_count is host-only and deliberately not projected to device.
      if (keyId.equals(HostKeys.DEVICE_COUNT.id())) {
        continue;
      }
      values.put(keyId, entry.getValue());
    }

    enrichment
        .flatMap(DeviceEnrichment::wifiSsid)
        .filter(s -> !s.isEmpty())
        .ifPresent(s -> values.put(AtsDeviceKeys.WIFI_SSID.id(), ImmutableList.of(s)));

    return DeviceRecord.create(deviceId, values.buildOrThrow());
  }

  /** Combines and deduplicates SDK and software version values for the unified version field. */
  private static void addSdkOrSoftwareVersion(
      ImmutableMap.Builder<String, ImmutableList<String>> valuesBuilder, DeviceInfo deviceInfo) {
    ImmutableList<String> versions =
        DeviceKeys.SDK_OR_SOFTWARE_VERSION.deviceInfoSources().stream()
            .flatMap(source -> source.extract(deviceInfo).stream())
            .filter(v -> !v.isEmpty())
            .distinct()
            .collect(toImmutableList());
    if (!versions.isEmpty()) {
      valuesBuilder.put(DeviceKeys.SDK_OR_SOFTWARE_VERSION.id(), versions);
    }
  }

  /** Appends all supported and required dimensions without deduplication. */
  private static void addDimensions(
      ImmutableMap.Builder<String, ImmutableList<String>> valuesBuilder,
      DeviceCompositeDimension composite) {
    Map<String, List<String>> dimMap = new LinkedHashMap<>();
    for (DeviceDimension dim : composite.getSupportedDimensionList()) {
      String name = dim.getName();
      String val = dim.getValue();
      if (!name.isEmpty() && !val.isEmpty() && !EXCLUDED_DIMENSIONS.contains(name)) {
        dimMap.computeIfAbsent(DeviceKeys.PREFIX_DIMENSION + name, k -> new ArrayList<>()).add(val);
      }
    }
    for (DeviceDimension dim : composite.getRequiredDimensionList()) {
      String name = dim.getName();
      String val = dim.getValue();
      if (!name.isEmpty() && !val.isEmpty() && !EXCLUDED_DIMENSIONS.contains(name)) {
        dimMap.computeIfAbsent(DeviceKeys.PREFIX_DIMENSION + name, k -> new ArrayList<>()).add(val);
      }
    }
    for (Map.Entry<String, List<String>> entry : dimMap.entrySet()) {
      valuesBuilder.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
    }
  }

  private static ImmutableList<String> nonEmptyList(List<String> list) {
    return list.stream().filter(s -> !s.isEmpty()).collect(toImmutableList());
  }

  /** Quarantine check: temp dimension named "quarantined" with value "true" (case-insensitive). */
  private static boolean isQuarantined(DeviceCondition condition) {
    for (TempDimension tempDimension : condition.getTempDimensionList()) {
      DeviceDimension dimension = tempDimension.getDimension();
      if (dimension.getName().equals("quarantined")
          && Ascii.equalsIgnoreCase(dimension.getValue(), "true")) {
        return true;
      }
    }
    return false;
  }

  private static ImmutableMap<String, String> extractHostProperties(LabInfo labInfo) {
    ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();
    for (HostProperty property :
        labInfo.getLabServerFeature().getHostProperties().getHostPropertyList()) {
      properties.put(property.getKey(), property.getValue());
    }
    return properties.buildKeepingLast();
  }

  private static Optional<String> hostVersion(ImmutableMap<String, String> hostProperties) {
    String version = hostProperties.get(HOST_VERSION_PROPERTY);
    return isNullOrEmpty(version) ? Optional.empty() : Optional.of(version);
  }

  /** Adds all index terms for one device to the accumulator. */
  private static void indexDevice(Accumulator accumulator, DeviceRecord record) {
    indexRecord(accumulator, record.values());
  }

  /** Adds all index terms for one host to the host accumulator. */
  private static void indexHost(Accumulator accumulator, HostRecord host) {
    indexRecord(accumulator, host.values());
  }

  private static void indexRecord(
      Accumulator accumulator, ImmutableMap<String, ImmutableList<String>> values) {
    for (Map.Entry<String, ImmutableList<String>> entry : values.entrySet()) {
      accumulator.addKeyValues(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Mutable scratch structures for one build pass. Converted to an immutable {@link FleetIndex} by
   * {@link #toIndex}.
   *
   * <p>Uses HashMap of HashSets for distinct values (O(1) insert) and sorts once at the end,
   * replacing the previous TreeMultimap (O(log V) per insert). Posting lists are not built here;
   * they are constructed lazily by {@link LazyPostings} on first access.
   */
  static final class Accumulator {
    // Distinct normalized values per key id, collected into HashSets for O(1) insertion.
    private final Map<String, Set<String>> distinctValues = new HashMap<>();
    // Nested display map: key -> (value -> first-seen display).
    private final Map<String, Map<String, String>> valueDisplays = new HashMap<>();
    // Nested count map: key -> (value -> distinct-device count).
    private final Map<String, Map<String, Integer>> valueCounts = new HashMap<>();
    // Number of records carrying at least one non-empty value for each key.
    private final Map<String, Integer> entityCounts = new HashMap<>();
    // Distinct single-value combinations per key, populated only for keys that have at least one
    // multi-value combination (for purely single-valued keys, distinctValues is the exact set of
    // single-value combinations).
    private final Map<String, Set<String>> singleCombinations = new HashMap<>();
    // Distinct multi-value combinations per key (allocated only for multi-valued keys).
    private final Map<String, Set<ImmutableSet<String>>> multiCombinations = new HashMap<>();
    private final Set<String> keyIds = new LinkedHashSet<>();

    /** Records all values of one key carried by a single record. */
    void addKeyValues(String keyId, ImmutableList<String> rawValues) {
      if (rawValues.isEmpty()) {
        return;
      }
      if (rawValues.size() == 1) {
        String raw = rawValues.get(0);
        if (raw.isEmpty()) {
          return;
        }
        String value = Ascii.toLowerCase(raw);
        recordTerm(keyId, value, raw);
        distinctValues.computeIfAbsent(keyId, k -> new HashSet<>()).add(value);
        entityCounts.merge(keyId, 1, Integer::sum);
        if (multiCombinations.containsKey(keyId)) {
          singleCombinations.get(keyId).add(value);
        }
        return;
      }
      ImmutableSet.Builder<String> comboBuilder = ImmutableSet.builder();
      Set<String> seen = new HashSet<>();
      for (String raw : rawValues) {
        if (raw.isEmpty()) {
          continue;
        }
        String value = Ascii.toLowerCase(raw);
        if (seen.add(value)) {
          recordTerm(keyId, value, raw);
          comboBuilder.add(value);
        }
      }
      if (seen.isEmpty()) {
        return;
      }
      entityCounts.merge(keyId, 1, Integer::sum);
      ImmutableSet<String> combo = comboBuilder.build();
      if (combo.size() == 1) {
        if (multiCombinations.containsKey(keyId)) {
          singleCombinations.get(keyId).add(combo.iterator().next());
        }
      } else {
        if (!multiCombinations.containsKey(keyId)) {
          singleCombinations.put(
              keyId, new HashSet<>(distinctValues.getOrDefault(keyId, ImmutableSet.of())));
        }
        multiCombinations.computeIfAbsent(keyId, k -> new HashSet<>()).add(combo);
      }
      distinctValues.computeIfAbsent(keyId, k -> new HashSet<>()).addAll(combo);
    }

    private void recordTerm(String keyId, String value, String display) {
      keyIds.add(keyId);
      valueCounts.computeIfAbsent(keyId, k -> new HashMap<>()).merge(value, 1, Integer::sum);
      valueDisplays.computeIfAbsent(keyId, k -> new HashMap<>()).putIfAbsent(value, display);
    }

    /** Merges another accumulator into this one. Counts are summed, distinct values unioned. */
    void mergeFrom(Accumulator other) {
      for (String keyId : other.keyIds) {
        boolean thisMulti = multiCombinations.containsKey(keyId);
        boolean otherMulti = other.multiCombinations.containsKey(keyId);
        if (thisMulti || otherMulti) {
          Set<String> targetSingles =
              thisMulti
                  ? singleCombinations.computeIfAbsent(keyId, k -> new HashSet<>())
                  : new HashSet<>(distinctValues.getOrDefault(keyId, ImmutableSet.of()));
          singleCombinations.put(keyId, targetSingles);
          Set<String> otherSingles =
              otherMulti ? other.singleCombinations.get(keyId) : other.distinctValues.get(keyId);
          if (otherSingles != null) {
            targetSingles.addAll(otherSingles);
          }
        }
      }
      for (Map.Entry<String, Set<ImmutableSet<String>>> entry :
          other.multiCombinations.entrySet()) {
        multiCombinations
            .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
            .addAll(entry.getValue());
      }
      keyIds.addAll(other.keyIds);
      for (Map.Entry<String, Map<String, Integer>> entry : other.valueCounts.entrySet()) {
        Map<String, Integer> target =
            valueCounts.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
        for (Map.Entry<String, Integer> vc : entry.getValue().entrySet()) {
          target.merge(vc.getKey(), vc.getValue(), Integer::sum);
        }
      }
      for (Map.Entry<String, Set<String>> entry : other.distinctValues.entrySet()) {
        distinctValues
            .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
            .addAll(entry.getValue());
      }
      for (Map.Entry<String, Map<String, String>> entry : other.valueDisplays.entrySet()) {
        Map<String, String> target =
            valueDisplays.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
        for (Map.Entry<String, String> vd : entry.getValue().entrySet()) {
          target.putIfAbsent(vd.getKey(), vd.getValue());
        }
      }
      for (Map.Entry<String, Integer> entry : other.entityCounts.entrySet()) {
        entityCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }
    }

    FleetIndex toIndex() {
      List<String> keyList = new ArrayList<>(keyIds);

      // Parallel per-key: sort values + freeze counts + freeze displays + freeze key stats.
      // Uses ConcurrentHashMap to collect results from parallel stream.
      ConcurrentHashMap<String, ImmutableList<String>> sortedMap = new ConcurrentHashMap<>();
      ConcurrentHashMap<String, ImmutableMap<String, Integer>> countsMap =
          new ConcurrentHashMap<>();
      ConcurrentHashMap<String, ImmutableMap<String, String>> displaysMap =
          new ConcurrentHashMap<>();
      ConcurrentHashMap<String, KeyStats> statsMap = new ConcurrentHashMap<>();

      // TODO: Consider switching to a sequential loop over keyList if the number
      // of indexed keys remains small.
      keyList.parallelStream()
          .forEach(
              keyId -> {
                Set<String> values = distinctValues.get(keyId);
                if (values != null) {
                  List<String> valueList = new ArrayList<>(values);
                  Collections.sort(valueList);
                  sortedMap.put(keyId, ImmutableList.copyOf(valueList));
                }
                Map<String, Integer> keyCounts = valueCounts.get(keyId);
                if (keyCounts != null) {
                  countsMap.put(keyId, ImmutableMap.copyOf(keyCounts));
                }
                Map<String, String> keyDisplays = valueDisplays.get(keyId);
                if (keyDisplays != null) {
                  displaysMap.put(keyId, ImmutableMap.copyOf(keyDisplays));
                }
                int entities = entityCounts.getOrDefault(keyId, 0);
                if (entities > 0) {
                  Set<ImmutableSet<String>> multis = multiCombinations.get(keyId);
                  int combinations =
                      multis == null
                          ? (values != null ? values.size() : 0)
                          : singleCombinations.getOrDefault(keyId, ImmutableSet.of()).size()
                              + multis.size();
                  statsMap.put(keyId, new KeyStats(entities, combinations, multis != null));
                }
              });

      // Build semanticGlobalSorted (parallel collect + sort).
      List<ValueKeyPair> semanticPairs =
          keyList.parallelStream()
              .filter(keyId -> !IDENTIFIER_KEYS.contains(keyId))
              .flatMap(
                  keyId -> {
                    Set<String> values = distinctValues.get(keyId);
                    if (values == null) {
                      return Stream.empty();
                    }
                    return values.stream().map(v -> new ValueKeyPair(v, keyId));
                  })
              .sorted()
              .toList();

      // Build globalExact: value -> [(key, count)].
      Map<String, List<KeyCount>> globalExactMap = new HashMap<>();
      for (String keyId : keyList) {
        Map<String, Integer> keyCounts = valueCounts.get(keyId);
        if (keyCounts != null) {
          for (Map.Entry<String, Integer> entry : keyCounts.entrySet()) {
            globalExactMap
                .computeIfAbsent(entry.getKey(), v -> new ArrayList<>())
                .add(new KeyCount(keyId, entry.getValue()));
          }
        }
      }
      ImmutableMap.Builder<String, ImmutableList<KeyCount>> frozenGlobalExact =
          ImmutableMap.builder();
      for (Map.Entry<String, List<KeyCount>> entry : globalExactMap.entrySet()) {
        frozenGlobalExact.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
      }

      return CoreFleetIndex.builder()
          .setValueCountsMap(ImmutableMap.copyOf(countsMap))
          .setSortedValuesMap(ImmutableMap.copyOf(sortedMap))
          .setValueDisplaysMap(ImmutableMap.copyOf(displaysMap))
          .setKeyStatsMap(ImmutableMap.copyOf(statsMap))
          .setKeyIds(ImmutableSet.copyOf(keyIds))
          .setSemanticGlobalSorted(ImmutableList.copyOf(semanticPairs))
          .setGlobalExact(frozenGlobalExact.buildOrThrow())
          .build();
    }
  }
}
