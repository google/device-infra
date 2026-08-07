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
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.CONFIG_WIFI_SSID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_PREFIX;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_QUARANTINED;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DECORATOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DRIVER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_EXECUTOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_OWNER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_UUID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_ATS_CONTROLLER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_IP;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PROP_PREFIX;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.TreeMultimap;
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
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/**
 * Builds an immutable {@link FleetSnapshot} (forward store plus {@link FleetIndex}) from a {@code
 * LabQueryResult}.
 *
 * <p>This is a pure function: proto in, snapshot out, no RPC. That keeps it unit-testable and
 * scenario-agnostic. It indexes every dimension it sees, with no curated allowlist. It is the Java
 * port of the search prototype's {@code _extract} plus the per-key half of {@code
 * _finalize_indexes}.
 *
 * <p>Values are normalized to lowercase for the index terms so lookups are case-insensitive. The
 * first-seen original casing of each value is retained in {@link FleetIndex#valueDisplays} for
 * presentation. Counts are distinct-device counts: a device that lists the same value twice (for
 * example the same dimension as both supported and required) contributes one.
 */
public final class FleetIndexBuilder {

  /** Host property that carries the lab server version, when present. */
  private static final String HOST_VERSION_PROPERTY = "host_version";

  /**
   * Display names for the built-in device, host, and config keys. Discovered {@code dim::} and
   * {@code prop::} keys derive their display name from their raw name (see {@link #displayName}).
   */
  private static final ImmutableMap<String, String> BUILTIN_DISPLAY_NAMES =
      ImmutableMap.<String, String>builder()
          .put(FIELD_UUID, "UUID")
          .put(FIELD_TYPE, "Type")
          .put(FIELD_STATUS, "Status")
          .put(FIELD_OWNER, "Owners")
          .put(FIELD_DRIVER, "Supported Drivers")
          .put(FIELD_DECORATOR, "Supported Decorators")
          .put(FIELD_EXECUTOR, "Executors")
          .put("dim::os", "OS")
          .put("dim::model", "Model")
          .put("dim::sdk_version", "SDK Version")
          .put("dim::software_version", "Software Version")
          .put("dim::device_form", "Form")
          .put(DIM_QUARANTINED, "Quarantine")
          .put("dim::device_class_name", "Device Class")
          .put("dim::manufacturer", "Manufacturer")
          .put(CONFIG_WIFI_SSID, "Wi-Fi SSID")
          .put(HOST_NAME, "Host Name")
          .put(HOST_IP, "Host IP")
          .put("host::host_os", "Host OS")
          .put(HOST_LAB_TYPE, "Host Lab Type")
          .put("host::connectivity", "Host Lab Server Connectivity")
          .put("host::lab_server_activity", "Host Lab Server Activity")
          .put("host::daemon_status", "Host Daemon Server Status")
          .put("host::release_status", "Host Release Status")
          .put("host::lab_server_version", "Host Lab Server Version")
          .put("host::release_type", "Host Release Type")
          .put(HOST_ATS_CONTROLLER, "ATS Lab")
          .buildOrThrow();

  @Inject
  FleetIndexBuilder() {}

  /**
   * Builds a snapshot from lab data alone, with no host or device enrichment. Used by sources that
   * carry only a lab query result, such as the aggregated ATS fan-out, and by tests.
   */
  public FleetSnapshot build(LabQueryResult labData, Instant buildTime) {
    return build(FleetRawData.ofLabData(labData), buildTime);
  }

  /**
   * Builds a snapshot from the raw data, stamping the given build time.
   *
   * <p>{@link FleetRawData#labData()} provides the base device and host records. Per-device and
   * per-host enrichment, when present, adds the WiFi SSID, the host lab types, and the host
   * release, daemon, and version attributes. A device or host with no enrichment entry behaves
   * exactly as if only lab data were supplied.
   */
  public FleetSnapshot build(FleetRawData raw, Instant buildTime) {
    LabQueryResult labResult = raw.labData();
    List<DeviceRecord> devices = new ArrayList<>();
    List<HostRecord> hosts = new ArrayList<>();
    Accumulator accumulator = new Accumulator();
    // Read the controller-display registry once so indexDevice can map each controller id to its
    // friendly display without re-fetching per device.
    ImmutableMap<String, String> atsControllerDisplays = raw.atsControllerDisplays();

    // The full pull uses lab_view_request, so results arrive as a lab->device tree. The device_view
    // (grouped) shape is never requested for the index pull, so it is ignored here.
    ImmutableList<LabData> labDataList =
        labResult.hasLabView()
            ? ImmutableList.copyOf(labResult.getLabView().getLabDataList())
            : ImmutableList.of();

    for (LabData labData : labDataList) {
      LabInfo labInfo = labData.getLabInfo();
      LabLocator locator = labInfo.getLabLocator();
      String hostName = locator.getHostName();
      String hostIp = locator.getIp();
      Optional<String> masterDetectedIp =
          locator.hasMasterDetectedIp()
              ? Optional.of(locator.getMasterDetectedIp())
              : Optional.empty();
      String labStatus = labInfo.getLabStatus().name();
      ImmutableMap<String, String> hostProperties = extractHostProperties(labInfo);
      Optional<HostEnrichment> hostEnrichment =
          Optional.ofNullable(raw.hostEnrichments().get(hostName));
      ImmutableList<String> labTypes =
          hostEnrichment.map(HostEnrichment::labTypes).orElse(ImmutableList.of());

      DeviceList deviceList = labData.getDeviceList();
      hosts.add(
          buildHostRecord(hostName, hostIp, labStatus, hostProperties, deviceList, hostEnrichment));

      for (DeviceInfo deviceInfo : deviceList.getDeviceInfoList()) {
        int deviceIndex = devices.size();
        String deviceId = deviceInfo.getDeviceLocator().getId();
        Optional<DeviceEnrichment> deviceEnrichment =
            Optional.ofNullable(raw.deviceEnrichments().get(deviceId));
        DeviceRecord record =
            buildDeviceRecord(
                deviceInfo,
                hostName,
                hostIp,
                labStatus,
                masterDetectedIp,
                hostProperties,
                deviceEnrichment);
        devices.add(record);
        indexDevice(
            accumulator, deviceIndex, record, hostProperties, labTypes, atsControllerDisplays);
      }
    }

    return FleetSnapshot.builder()
        .setBuildTime(buildTime)
        .setDevices(ImmutableList.copyOf(devices))
        .setHosts(ImmutableList.copyOf(hosts))
        .setIndex(accumulator.toIndex())
        .build();
  }

  private static HostRecord buildHostRecord(
      String hostName,
      String hostIp,
      String labStatus,
      ImmutableMap<String, String> hostProperties,
      DeviceList deviceList,
      Optional<HostEnrichment> enrichment) {
    return HostRecord.builder()
        .setHostName(hostName)
        .setHostIp(hostIp)
        .setLabStatus(labStatus)
        .setHostProperties(hostProperties)
        .setDeviceCount(deviceList.getDeviceInfoCount())
        .setLabTypes(enrichment.map(HostEnrichment::labTypes).orElse(ImmutableList.of()))
        .setReleaseStatus(enrichment.flatMap(HostEnrichment::releaseStatus))
        .setReleaseType(enrichment.flatMap(HostEnrichment::releaseType))
        .setDaemonStatus(enrichment.flatMap(HostEnrichment::daemonStatus))
        // The enrichment source wins when it carries a version; otherwise fall back to the
        // host_version property that LabInfo reports.
        .setLabServerVersion(
            enrichment
                .flatMap(HostEnrichment::labServerVersion)
                .or(() -> hostVersion(hostProperties)))
        .build();
  }

  private static DeviceRecord buildDeviceRecord(
      DeviceInfo deviceInfo,
      String hostName,
      String hostIp,
      String labStatus,
      Optional<String> masterDetectedIp,
      ImmutableMap<String, String> hostProperties,
      Optional<DeviceEnrichment> enrichment) {
    DeviceFeature feature = deviceInfo.getDeviceFeature();
    DeviceCondition condition = deviceInfo.getDeviceCondition();

    Optional<Instant> lastHealthyTime =
        condition.hasLastHealthyTime()
            ? Optional.of(toInstant(condition.getLastHealthyTime()))
            : Optional.empty();

    return DeviceRecord.builder()
        .setDeviceId(deviceInfo.getDeviceLocator().getId())
        .setHostName(hostName)
        .setStatus(deviceInfo.getDeviceStatus().name())
        .setTypes(ImmutableList.copyOf(feature.getTypeList()))
        .setOwners(ImmutableList.copyOf(feature.getOwnerList()))
        .setDrivers(ImmutableList.copyOf(feature.getDriverList()))
        .setDecorators(ImmutableList.copyOf(feature.getDecoratorList()))
        .setExecutors(ImmutableList.copyOf(feature.getExecutorList()))
        .setDimensions(mergeDimensions(feature.getCompositeDimension()))
        .setQuarantined(isQuarantined(condition))
        .setLastHealthyTime(lastHealthyTime)
        .setHostIp(hostIp)
        .setLabStatus(labStatus)
        .setMasterDetectedIp(masterDetectedIp)
        .setHostProperties(hostProperties)
        .setWifiSsid(enrichment.flatMap(DeviceEnrichment::wifiSsid))
        .setAtsController(enrichment.flatMap(DeviceEnrichment::atsController))
        .build();
  }

  /**
   * Merges supported and required dimensions into one name to values map, deduping values per name
   * while preserving first-seen order. Supported and required share one namespace: both mean the
   * device offers the value, which is what a search over the dimension asks about.
   */
  private static ImmutableMap<String, ImmutableList<String>> mergeDimensions(
      DeviceCompositeDimension composite) {
    // LinkedHashMultimap dedups values and preserves first-seen order for both names and values,
    // matching what a search over the dimension asks about.
    LinkedHashMultimap<String, String> merged = LinkedHashMultimap.create();
    addDimensions(merged, composite.getSupportedDimensionList());
    addDimensions(merged, composite.getRequiredDimensionList());

    ImmutableMap.Builder<String, ImmutableList<String>> result = ImmutableMap.builder();
    for (Map.Entry<String, Collection<String>> entry : merged.asMap().entrySet()) {
      result.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
    }
    return result.buildOrThrow();
  }

  private static void addDimensions(
      LinkedHashMultimap<String, String> merged, List<DeviceDimension> dimensions) {
    for (DeviceDimension dimension : dimensions) {
      String name = dimension.getName();
      if (name.isEmpty()) {
        continue;
      }
      merged.put(name, dimension.getValue());
    }
  }

  /**
   * Quarantine matches the FE v6 detail page (DeviceHeaderInfoBuilder): a temp dimension named
   * exactly "quarantined" whose value equals "true" ignoring case.
   */
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
  private static void indexDevice(
      Accumulator accumulator,
      int deviceIndex,
      DeviceRecord record,
      ImmutableMap<String, String> hostProperties,
      ImmutableList<String> labTypes,
      ImmutableMap<String, String> atsControllerDisplays) {
    Set<String> seen = new HashSet<>();

    if (!record.deviceId().isEmpty()) {
      accumulator.add(deviceIndex, seen, FIELD_UUID, record.deviceId());
    }
    accumulator.add(deviceIndex, seen, FIELD_STATUS, record.status());
    for (String value : record.types()) {
      accumulator.add(deviceIndex, seen, FIELD_TYPE, value);
    }
    for (String value : record.owners()) {
      accumulator.add(deviceIndex, seen, FIELD_OWNER, value);
    }
    for (String value : record.drivers()) {
      accumulator.add(deviceIndex, seen, FIELD_DRIVER, value);
    }
    for (String value : record.decorators()) {
      accumulator.add(deviceIndex, seen, FIELD_DECORATOR, value);
    }
    for (String value : record.executors()) {
      accumulator.add(deviceIndex, seen, FIELD_EXECUTOR, value);
    }
    for (Map.Entry<String, ImmutableList<String>> entry : record.dimensions().entrySet()) {
      String keyId = DIM_PREFIX + entry.getKey();
      for (String value : entry.getValue()) {
        accumulator.add(deviceIndex, seen, keyId, value);
      }
    }
    accumulator.add(deviceIndex, seen, DIM_QUARANTINED, record.quarantined() ? "Yes" : "No");
    for (Map.Entry<String, String> entry : hostProperties.entrySet()) {
      accumulator.add(deviceIndex, seen, PROP_PREFIX + entry.getKey(), entry.getValue());
    }
    if (!record.hostName().isEmpty()) {
      accumulator.add(deviceIndex, seen, HOST_NAME, record.hostName());
    }
    if (!record.hostIp().isEmpty()) {
      accumulator.add(deviceIndex, seen, HOST_IP, record.hostIp());
    }
    // Stamp the host lab types onto each device so devices are filterable and facetable by lab
    // type.
    for (String labType : labTypes) {
      accumulator.add(deviceIndex, seen, HOST_LAB_TYPE, labType);
    }
    // TODO: index the remaining cross-entity host attributes (host::host_os,
    // host::connectivity, host::lab_server_activity, host::daemon_status, host::release_status,
    // host::lab_server_version, host::release_type). They are derived from HostInfoService, which
    // later CLs add.
    record
        .wifiSsid()
        .filter(ssid -> !ssid.isEmpty())
        .ifPresent(ssid -> accumulator.add(deviceIndex, seen, CONFIG_WIFI_SSID, ssid));
    // The controller id is the stored/filter term; the display is the friendly name from the
    // ats-all registry, falling back to the id itself when the registry has no entry.
    record
        .atsController()
        .filter(id -> !id.isEmpty())
        .ifPresent(
            id ->
                accumulator.add(
                    deviceIndex,
                    seen,
                    HOST_ATS_CONTROLLER,
                    id,
                    atsControllerDisplays.getOrDefault(id, id)));
  }

  private static Instant toInstant(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  private static String displayName(String keyId) {
    String builtin = BUILTIN_DISPLAY_NAMES.get(keyId);
    if (builtin != null) {
      return builtin;
    }
    int separator = keyId.indexOf("::");
    String namespace = separator >= 0 ? keyId.substring(0, separator) : "";
    String name = separator >= 0 ? keyId.substring(separator + 2) : keyId;
    return switch (namespace) {
      case "dim" -> "Dimension " + name;
      case "prop" -> "Host Property " + name;
      default -> name;
    };
  }

  /**
   * Mutable scratch structures for one build pass. Converted to an immutable {@link FleetIndex} by
   * {@link #toIndex}.
   */
  private static final class Accumulator {
    // Posting lists keyed by the composite (key id plus value), device indices in insertion order.
    private final ListMultimap<String, Integer> postings =
        MultimapBuilder.hashKeys().arrayListValues().build();
    // Distinct normalized values per key id, kept sorted for prefix matching.
    private final TreeMultimap<String, String> distinctValues = TreeMultimap.create();
    // First-seen original display per composite (key id plus value).
    private final Map<String, String> valueDisplays = new HashMap<>();
    private final Set<String> keyIds = new LinkedHashSet<>();

    /**
     * Records one (key, value) for a device, using the term itself as its display. Deduped per
     * device via {@code seen} so counts and posting lists reflect distinct devices.
     */
    void add(int deviceIndex, Set<String> seen, String keyId, String original) {
      add(deviceIndex, seen, keyId, original, original);
    }

    /**
     * Records one (key, term) for a device with a distinct {@code display}, so the stored/filter
     * term and the presented value can differ (as they do for {@code host::ats_controller}).
     * Deduped per device via {@code seen} so counts and posting lists reflect distinct devices.
     */
    void add(int deviceIndex, Set<String> seen, String keyId, String term, String display) {
      String value = Ascii.toLowerCase(term);
      String composite = composite(keyId, value);
      if (!seen.add(composite)) {
        return;
      }
      keyIds.add(keyId);
      postings.put(composite, deviceIndex);
      distinctValues.put(keyId, value);
      valueDisplays.putIfAbsent(composite, display);
    }

    FleetIndex toIndex() {
      ImmutableMap.Builder<String, ImmutableMap<String, Integer>> counts = ImmutableMap.builder();
      ImmutableMap.Builder<String, ImmutableMap<String, ImmutableList<Integer>>> post =
          ImmutableMap.builder();
      ImmutableMap.Builder<String, ImmutableList<String>> sorted = ImmutableMap.builder();
      ImmutableMap.Builder<String, ImmutableMap<String, String>> displays = ImmutableMap.builder();

      // Emit per-key groups in key-id insertion order. Each key's values arrive sorted from the
      // TreeMultimap, which is the order the value index relies on for prefix matching. The count
      // for a value is the size of its posting list, since both are populated once per distinct
      // device.
      for (String keyId : keyIds) {
        NavigableSet<String> values = distinctValues.get(keyId);

        ImmutableMap.Builder<String, Integer> keyCounts = ImmutableMap.builder();
        ImmutableMap.Builder<String, ImmutableList<Integer>> keyPostings = ImmutableMap.builder();
        ImmutableMap.Builder<String, String> keyDisplays = ImmutableMap.builder();
        for (String value : values) {
          String composite = composite(keyId, value);
          List<Integer> deviceIndices = postings.get(composite);
          keyCounts.put(value, deviceIndices.size());
          keyPostings.put(value, ImmutableList.copyOf(deviceIndices));
          keyDisplays.put(value, valueDisplays.get(composite));
        }
        counts.put(keyId, keyCounts.buildOrThrow());
        post.put(keyId, keyPostings.buildOrThrow());
        sorted.put(keyId, ImmutableList.copyOf(values));
        displays.put(keyId, keyDisplays.buildOrThrow());
      }

      ImmutableMap.Builder<String, String> names = ImmutableMap.builder();
      for (String keyId : keyIds) {
        names.put(keyId, displayName(keyId));
      }

      return FleetIndex.builder()
          .setValueCounts(counts.buildOrThrow())
          .setPostings(post.buildOrThrow())
          .setSortedValues(sorted.buildOrThrow())
          .setValueDisplays(displays.buildOrThrow())
          .setKeyIds(ImmutableSet.copyOf(keyIds))
          .setDisplayNames(names.buildOrThrow())
          .build();
    }

    /**
     * Builds the composite key used to store per-(key, value) postings and displays in a single
     * flat map. The NUL separator never appears in a key id or a normalized value.
     */
    private static String composite(String keyId, String value) {
      return keyId + '\u0000' + value;
    }
  }
}
