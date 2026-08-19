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
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.CONFIG_WIFI_SSID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_PREFIX;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_QUARANTINED;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.EXCLUDED_DIMENSIONS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DECORATOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DRIVER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_EXECUTOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_OWNER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_UUID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_ATS_CONTROLLER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_CONNECTIVITY;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DAEMON_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DEVICE_COUNT;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_IP;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_SERVER_VERSION;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_OS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_RELEASE_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_RELEASE_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PLAIN_VALUE_KEYS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PROP_PREFIX;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
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
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostTypes;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UiLabType;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
 *
 * <p>Posting lists are not built at index time. They are built lazily by {@link LazyPostings} on
 * first access, keeping the build under 2 seconds for 152K devices.
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
          .put(HOST_OS, "Host OS")
          .put(HOST_LAB_TYPE, "Host Lab Type")
          .put(HOST_CONNECTIVITY, "Host Lab Server Connectivity")
          .put("host::lab_server_activity", "Host Lab Server Activity")
          .put(HOST_DAEMON_STATUS, "Host Daemon Server Status")
          .put(HOST_RELEASE_STATUS, "Host Release Status")
          .put(HOST_LAB_SERVER_VERSION, "Host Lab Server Version")
          .put(HOST_RELEASE_TYPE, "Host Release Type")
          .put(HOST_ATS_CONTROLLER, "ATS Lab")
          .put(HOST_DEVICE_COUNT, "Device Count")
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
    ImmutableMap<String, String> atsControllerDisplays = raw.atsControllerDisplays();

    ImmutableList<LabData> labDataList =
        labResult.hasLabView()
            ? ImmutableList.copyOf(labResult.getLabView().getLabDataList())
            : ImmutableList.of();

    // Phase 1: parallel per-host processing with per-thread Accumulator.
    // Each ForkJoinPool thread reuses ONE Accumulator across all hosts it processes,
    // reducing merge from 43K per-host accumulators to T per-thread accumulators.
    record HostDevices(HostRecord host, List<DeviceRecord> devices) {}

    ConcurrentMap<Thread, Accumulator> accumulatorsByThread = new ConcurrentHashMap<>();

    // A parallel per-thread accumulator builds the host index over host records, independent of the
    // device accumulator above. The two are kept separate so the device index stays a pure index
    // over devices and the host index a pure index over hosts.
    ConcurrentMap<Thread, Accumulator> hostAccumulatorsByThread = new ConcurrentHashMap<>();

    List<HostDevices> hostDevices =
        labDataList.parallelStream()
            .map(
                labData -> {
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
                  // Lab type is the composite of the LabInfo host properties (lab_type, dm_type)
                  // and the HostInfoService release enum, matching the host detail page. It is
                  // empty
                  // for any host with no lab type (every ATS host), which keeps the key internal
                  // only and data driven. UNKNOWN is treated as no lab type so it never surfaces.
                  Optional<String> releaseTypeOpt =
                      hostEnrichment.flatMap(HostEnrichment::releaseType);
                  ImmutableList<String> labTypes =
                      HostTypes.determineUiLabTypes(Optional.of(labInfo), releaseTypeOpt).stream()
                          .filter(labType -> labType != UiLabType.UNKNOWN)
                          .map(HostTypes::labTypeDisplayName)
                          .collect(toImmutableList());
                  // Host OS mirrors the host detail page default of "Unknown" when the property is
                  // absent. Connectivity uses the same LabStatus bucketing as the detail page.
                  String hostOs = hostProperties.getOrDefault("host_os", "Unknown");
                  String hostConnectivity =
                      HostConnectivityStatuses.create(Optional.of(labInfo)).getTitle();

                  DeviceList deviceList = labData.getDeviceList();
                  HostRecord hostRecord =
                      buildHostRecord(
                          hostName,
                          hostIp,
                          labStatus,
                          hostProperties,
                          deviceList,
                          labTypes,
                          hostOs,
                          hostConnectivity,
                          hostEnrichment);
                  Accumulator hostAccum =
                      hostAccumulatorsByThread.computeIfAbsent(
                          Thread.currentThread(), t -> new Accumulator());
                  indexHost(hostAccum, hostRecord, atsControllerDisplays);

                  // TODO: Consider refactoring thread-identity partitioned
                  // accumulators to a standard Stream collect/reduce or manual list chunking
                  // pattern if parallel collection semantics need modernization.
                  Accumulator accum =
                      accumulatorsByThread.computeIfAbsent(
                          Thread.currentThread(), t -> new Accumulator());
                  List<DeviceRecord> devices = new ArrayList<>();
                  for (DeviceInfo deviceInfo : deviceList.getDeviceInfoList()) {
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
                            hostRecord,
                            hostOs,
                            hostConnectivity,
                            deviceEnrichment);
                    devices.add(record);
                    indexDevice(accum, record, atsControllerDisplays);
                  }
                  return new HostDevices(hostRecord, devices);
                })
            .toList();

    // Phase 2: flatten + merge T accumulators (not 43K).
    List<DeviceRecord> allDevices = new ArrayList<>();
    ImmutableList.Builder<HostRecord> allHosts = ImmutableList.builder();
    for (HostDevices hd : hostDevices) {
      allHosts.add(hd.host());
      allDevices.addAll(hd.devices());
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
        .build();
  }

  private static HostRecord buildHostRecord(
      String hostName,
      String hostIp,
      String labStatus,
      ImmutableMap<String, String> hostProperties,
      DeviceList deviceList,
      ImmutableList<String> labTypes,
      String hostOs,
      String hostConnectivity,
      Optional<HostEnrichment> enrichment) {
    return HostRecord.builder()
        .setHostName(hostName)
        .setHostIp(hostIp)
        .setLabStatus(labStatus)
        .setHostOs(hostOs)
        .setHostConnectivity(hostConnectivity)
        .setHostProperties(hostProperties)
        .setDeviceCount(deviceList.getDeviceInfoCount())
        .setLabTypes(labTypes)
        .setReleaseStatus(enrichment.flatMap(HostEnrichment::releaseStatus))
        .setReleaseType(enrichment.flatMap(HostEnrichment::releaseType))
        .setDaemonStatus(enrichment.flatMap(HostEnrichment::daemonStatus))
        // The enrichment source wins when it carries a version; otherwise fall back to the
        // host_version property that LabInfo reports.
        .setLabServerVersion(
            enrichment
                .flatMap(HostEnrichment::labServerVersion)
                .or(() -> hostVersion(hostProperties)))
        .setAtsController(enrichment.flatMap(HostEnrichment::atsController))
        .build();
  }

  private static DeviceRecord buildDeviceRecord(
      DeviceInfo deviceInfo,
      String hostName,
      String hostIp,
      String labStatus,
      Optional<String> masterDetectedIp,
      ImmutableMap<String, String> hostProperties,
      HostRecord host,
      String hostOs,
      String hostConnectivity,
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
        .setLabTypes(host.labTypes())
        .setHostOs(hostOs)
        .setHostConnectivity(hostConnectivity)
        .setDaemonStatus(host.daemonStatus())
        .setReleaseStatus(host.releaseStatus())
        .setReleaseType(host.releaseType())
        .setLabServerVersion(host.labServerVersion())
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
      DeviceRecord record,
      ImmutableMap<String, String> atsControllerDisplays) {
    Set<String> seen = new HashSet<>();

    if (!record.deviceId().isEmpty()) {
      accumulator.add(seen, FIELD_UUID, record.deviceId());
    }
    accumulator.add(seen, FIELD_STATUS, record.status());
    for (String value : record.types()) {
      accumulator.add(seen, FIELD_TYPE, value);
    }
    for (String value : record.owners()) {
      accumulator.add(seen, FIELD_OWNER, value);
    }
    for (String value : record.drivers()) {
      accumulator.add(seen, FIELD_DRIVER, value);
    }
    for (String value : record.decorators()) {
      accumulator.add(seen, FIELD_DECORATOR, value);
    }
    for (String value : record.executors()) {
      accumulator.add(seen, FIELD_EXECUTOR, value);
    }
    for (Map.Entry<String, ImmutableList<String>> entry : record.dimensions().entrySet()) {
      String dimName = entry.getKey();
      if (EXCLUDED_DIMENSIONS.contains(dimName)) {
        continue;
      }
      String keyId = DIM_PREFIX + dimName;
      for (String value : entry.getValue()) {
        accumulator.add(seen, keyId, value);
      }
    }
    accumulator.add(seen, DIM_QUARANTINED, record.quarantined() ? "Yes" : "No");
    for (Map.Entry<String, String> entry : record.hostProperties().entrySet()) {
      accumulator.add(seen, PROP_PREFIX + entry.getKey(), entry.getValue());
    }
    if (!record.hostName().isEmpty()) {
      accumulator.add(seen, HOST_NAME, record.hostName());
    }
    if (!record.hostIp().isEmpty()) {
      accumulator.add(seen, HOST_IP, record.hostIp());
    }
    // Stamp the cross-entity host attributes onto each device so devices are filterable, facetable,
    // and groupable by a host attribute. The empty-value skip in Accumulator.add gates these to the
    // data that exists: HostInfoService-sourced values are absent in ATS and simply do not appear,
    // and a host with no lab type contributes no lab type value.
    for (String labType : record.labTypes()) {
      accumulator.add(seen, HOST_LAB_TYPE, labType);
    }
    accumulator.add(seen, HOST_OS, record.hostOs());
    accumulator.add(seen, HOST_CONNECTIVITY, record.hostConnectivity());
    record.daemonStatus().ifPresent(value -> accumulator.add(seen, HOST_DAEMON_STATUS, value));
    record.releaseStatus().ifPresent(value -> accumulator.add(seen, HOST_RELEASE_STATUS, value));
    record.releaseType().ifPresent(value -> accumulator.add(seen, HOST_RELEASE_TYPE, value));
    record
        .labServerVersion()
        .ifPresent(value -> accumulator.add(seen, HOST_LAB_SERVER_VERSION, value));
    // TODO: index host::lab_server_activity once the lab activity source is wired into the fleet
    // pull. It is the only cross-entity host attribute still deferred.
    record
        .wifiSsid()
        .filter(ssid -> !ssid.isEmpty())
        .ifPresent(ssid -> accumulator.add(seen, CONFIG_WIFI_SSID, ssid));
    // The controller id is the stored/filter term; the display is the friendly name from the
    // ats-all registry, falling back to the id itself when the registry has no entry.
    record
        .atsController()
        .filter(id -> !id.isEmpty())
        .ifPresent(
            id ->
                accumulator.add(
                    seen, HOST_ATS_CONTROLLER, id, atsControllerDisplays.getOrDefault(id, id)));
  }

  /**
   * Adds all index terms for one host to the host accumulator.
   *
   * <p>Mirrors {@link #indexDevice} for the host entity: it stamps the host's own attributes rather
   * than the cross-entity join. The same empty-value skip in {@link Accumulator#add} gates these to
   * the data that exists, so a host with no HostInfoService attributes (every ATS host) simply does
   * not contribute those keys, and a host with no lab type contributes no lab type value. Host OS
   * and connectivity are always present because {@code buildHostRecord} defaults them. The device
   * count is stamped as its decimal string so it is filterable and groupable like any other value.
   */
  private static void indexHost(
      Accumulator accumulator,
      HostRecord host,
      ImmutableMap<String, String> atsControllerDisplays) {
    Set<String> seen = new HashSet<>();

    if (!host.hostName().isEmpty()) {
      accumulator.add(seen, HOST_NAME, host.hostName());
    }
    if (!host.hostIp().isEmpty()) {
      accumulator.add(seen, HOST_IP, host.hostIp());
    }
    accumulator.add(seen, HOST_OS, host.hostOs());
    for (String labType : host.labTypes()) {
      accumulator.add(seen, HOST_LAB_TYPE, labType);
    }
    accumulator.add(seen, HOST_CONNECTIVITY, host.hostConnectivity());
    host.daemonStatus().ifPresent(value -> accumulator.add(seen, HOST_DAEMON_STATUS, value));
    host.releaseStatus().ifPresent(value -> accumulator.add(seen, HOST_RELEASE_STATUS, value));
    host.releaseType().ifPresent(value -> accumulator.add(seen, HOST_RELEASE_TYPE, value));
    host.labServerVersion()
        .ifPresent(value -> accumulator.add(seen, HOST_LAB_SERVER_VERSION, value));
    for (Map.Entry<String, String> entry : host.hostProperties().entrySet()) {
      accumulator.add(seen, PROP_PREFIX + entry.getKey(), entry.getValue());
    }
    // The controller id is the stored/filter term; the display is the friendly name from the
    // ats-all registry, falling back to the id itself when the registry has no entry.
    host.atsController()
        .filter(id -> !id.isEmpty())
        .ifPresent(
            id ->
                accumulator.add(
                    seen, HOST_ATS_CONTROLLER, id, atsControllerDisplays.getOrDefault(id, id)));
    // lab_server_activity is deferred: no lab activity source is wired into the fleet pull yet.
    accumulator.add(seen, HOST_DEVICE_COUNT, String.valueOf(host.deviceCount()));
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
   *
   * <p>Uses HashMap of HashSets for distinct values (O(1) insert) and sorts once at the end,
   * replacing the previous TreeMultimap (O(log V) per insert). Posting lists are not built here;
   * they are constructed lazily by {@link LazyPostings} on first access.
   */
  private static final class Accumulator {
    // Distinct normalized values per key id, collected into HashSets for O(1) insertion.
    private final Map<String, Set<String>> distinctValues = new HashMap<>();
    // Nested display map: key -> (value -> first-seen display).
    private final Map<String, Map<String, String>> valueDisplays = new HashMap<>();
    // Nested count map: key -> (value -> distinct-device count).
    private final Map<String, Map<String, Integer>> valueCounts = new HashMap<>();
    private final Set<String> keyIds = new LinkedHashSet<>();

    /** Records one (key, value) for a device, using the term itself as its display. */
    void add(Set<String> seen, String keyId, String original) {
      add(seen, keyId, original, original);
    }

    /**
     * Records one (key, term) for a device with a distinct {@code display}, so the stored/filter
     * term and the presented value can differ (as they do for {@code host::ats_controller}).
     *
     * <p>Deduped per device via {@code seen} so {@code valueCounts} reflects distinct devices, not
     * raw occurrences. The {@code seen} set is per-device (created fresh in {@link #indexDevice},
     * ~175 entries), so it stays resident in L1 cache and the composite key is cheap here. This
     * guards against intra-device duplicates that collapse on lowercase (case variants such as
     * {@code Android}/{@code android}, or repeated entries in the {@code types}/{@code owners}/...
     * repeated fields) which would otherwise inflate the facet count for that value.
     */
    void add(Set<String> seen, String keyId, String term, String display) {
      String value = Ascii.toLowerCase(term);
      // An empty value is not a distinct facet value. A device whose only value for
      // a key is the empty string has no value for that key (it counts toward "(no
      // value)"), matching the single-value fields and DeviceValueExtractor. proto3
      // dimension lists can carry empty strings, which must not surface as a blank
      // facet value in the value list.
      if (value.isEmpty()) {
        return;
      }
      // TODO: Consider extracting a composite(keyId, value) helper for
      // null-separated composite key lookups.
      if (!seen.add(keyId + '\u0000' + value)) {
        return;
      }
      keyIds.add(keyId);
      valueCounts.computeIfAbsent(keyId, k -> new HashMap<>()).merge(value, 1, Integer::sum);
      distinctValues.computeIfAbsent(keyId, k -> new HashSet<>()).add(value);
      valueDisplays.computeIfAbsent(keyId, k -> new HashMap<>()).putIfAbsent(value, display);
    }

    /** Merges another accumulator into this one. Counts are summed, distinct values unioned. */
    void mergeFrom(Accumulator other) {
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
    }

    FleetIndex toIndex() {
      List<String> keyList = new ArrayList<>(keyIds);

      // Parallel per-key: sort values + freeze counts + freeze displays.
      // Uses ConcurrentHashMap to collect results from parallel stream.
      ConcurrentHashMap<String, ImmutableList<String>> sortedMap = new ConcurrentHashMap<>();
      ConcurrentHashMap<String, ImmutableMap<String, Integer>> countsMap =
          new ConcurrentHashMap<>();
      ConcurrentHashMap<String, ImmutableMap<String, String>> displaysMap =
          new ConcurrentHashMap<>();

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
              });

      // Build semanticGlobalSorted (parallel collect + sort).
      List<ValueKeyPair> semanticPairs =
          keyList.parallelStream()
              .filter(keyId -> !PLAIN_VALUE_KEYS.contains(keyId))
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

      // Build display names.
      ImmutableMap.Builder<String, String> names = ImmutableMap.builder();
      for (String keyId : keyList) {
        names.put(keyId, displayName(keyId));
      }

      return FleetIndex.builder()
          .setValueCounts(ImmutableMap.copyOf(countsMap))
          .setSortedValues(ImmutableMap.copyOf(sortedMap))
          .setValueDisplays(ImmutableMap.copyOf(displaysMap))
          .setKeyIds(ImmutableSet.copyOf(keyIds))
          .setDisplayNames(names.buildOrThrow())
          .setSemanticGlobalSorted(ImmutableList.copyOf(semanticPairs))
          .setGlobalExact(frozenGlobalExact.buildOrThrow())
          .build();
    }
  }
}
