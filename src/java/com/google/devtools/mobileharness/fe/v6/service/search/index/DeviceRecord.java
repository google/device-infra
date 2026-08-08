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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.Optional;

/**
 * One device's indexed fields in the forward store.
 *
 * <p>Holds everything needed to (a) build inverted-index posting lists, (b) construct result-table
 * Cells, and (c) compute facet counts. The index builder extracts these from the raw {@code
 * DeviceInfo} proto during the periodic pull; downstream code never touches raw protos.
 *
 * <p>All 3 deployment cases use the same record type. Fields that come from case-specific data
 * sources (e.g. wifi_ssid from DeviceConfigService) are Optional and populated only when the data
 * source is available.
 */
@AutoValue
public abstract class DeviceRecord {

  /** Device UUID. Primary identifier. */
  public abstract String deviceId();

  /** Lab host running this device. */
  public abstract String hostName();

  /** Raw device status enum name (e.g. "IDLE", "BUSY", "MISSING"). */
  public abstract String status();

  /** Device types (multi-valued, e.g. ["AndroidRealDevice", "AndroidDevice"]). */
  public abstract ImmutableList<String> types();

  /** Device owners. */
  public abstract ImmutableList<String> owners();

  /** Supported drivers. */
  public abstract ImmutableList<String> drivers();

  /** Supported decorators. */
  public abstract ImmutableList<String> decorators();

  /** Executors. */
  public abstract ImmutableList<String> executors();

  /**
   * All composite dimensions (supported + required, merged). Key is the dimension name, value is
   * the list of values (a dimension can be multi-valued, e.g. pool=["shared", "default"]).
   */
  public abstract ImmutableMap<String, ImmutableList<String>> dimensions();

  /** Whether this device is quarantined (derived from temp_dimension "quarantined"). */
  public abstract boolean quarantined();

  /** Last time this device was healthy. Absent if never recorded. */
  public abstract Optional<Instant> lastHealthyTime();

  /** Host IP from LabLocator. */
  public abstract String hostIp();

  /** Host connectivity status (e.g. "LAB_RUNNING", "LAB_MISSING"). From LabInfo.lab_status. */
  public abstract String labStatus();

  /** Fallback IP detected by the master. */
  public abstract Optional<String> masterDetectedIp();

  /** Host properties from LabInfo.lab_server_feature.host_properties (key-value pairs). */
  public abstract ImmutableMap<String, String> hostProperties();

  /**
   * WiFi SSID from DeviceConfigService. Absent when the config service is unavailable (e.g.
   * ats-all) or when the device has no default WiFi configured. Populated for ats-one and 1p (1p
   * deferred, TODO).
   */
  public abstract Optional<String> wifiSsid();

  /**
   * The ATS controller this device belongs to. Present only in the ats-all deployment. Absent in
   * ats-one and 1p.
   */
  public abstract Optional<String> atsController();

  // --- Cross-entity host attributes, stamped from the device's host so a device can be filtered,
  // faceted, and grouped by a host attribute. Resolved once per host and shared across its devices.

  /**
   * User-facing host lab types (multi-valued, for example ["Core Lab", "Satellite Lab"]). Derived
   * from LabInfo host properties and the HostInfoService release enum by {@code
   * HostTypes.determineUiLabTypes}. Empty for hosts with no lab type, which is every ATS host, so
   * the lab type key stays internal-only and data driven.
   */
  public abstract ImmutableList<String> labTypes();

  /** Host OS from the host_os host property, defaulting to "Unknown" when absent. */
  public abstract String hostOs();

  /** Host lab server connectivity title ("Running", "Missing", or "Unknown") from LabInfo. */
  public abstract String hostConnectivity();

  /** Daemon server status (for example "RUNNING"). From HostInfoService, absent in ATS. */
  public abstract Optional<String> daemonStatus();

  /** Lab server release status (for example "RUNNING"). From HostInfoService, absent in ATS. */
  public abstract Optional<String> releaseStatus();

  /** Host release type (raw LabType enum name). From HostInfoService, absent in ATS. */
  public abstract Optional<String> releaseType();

  /** Lab server version. From the host_version property or HostInfoService. */
  public abstract Optional<String> labServerVersion();

  /** Creates a new builder. */
  public static Builder builder() {
    return new AutoValue_DeviceRecord.Builder()
        .setQuarantined(false)
        .setAtsController(Optional.empty())
        .setLabTypes(ImmutableList.of())
        .setHostOs("")
        .setHostConnectivity("");
  }

  /** Builder for {@link DeviceRecord}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDeviceId(String deviceId);

    public abstract Builder setHostName(String hostName);

    public abstract Builder setStatus(String status);

    public abstract Builder setTypes(ImmutableList<String> types);

    public abstract Builder setOwners(ImmutableList<String> owners);

    public abstract Builder setDrivers(ImmutableList<String> drivers);

    public abstract Builder setDecorators(ImmutableList<String> decorators);

    public abstract Builder setExecutors(ImmutableList<String> executors);

    public abstract Builder setDimensions(ImmutableMap<String, ImmutableList<String>> dimensions);

    public abstract Builder setQuarantined(boolean quarantined);

    public abstract Builder setLastHealthyTime(Optional<Instant> lastHealthyTime);

    public abstract Builder setHostIp(String hostIp);

    public abstract Builder setLabStatus(String labStatus);

    public abstract Builder setMasterDetectedIp(Optional<String> masterDetectedIp);

    public abstract Builder setHostProperties(ImmutableMap<String, String> hostProperties);

    public abstract Builder setWifiSsid(Optional<String> wifiSsid);

    public abstract Builder setAtsController(Optional<String> atsController);

    public abstract Builder setLabTypes(ImmutableList<String> labTypes);

    public abstract Builder setHostOs(String hostOs);

    public abstract Builder setHostConnectivity(String hostConnectivity);

    public abstract Builder setDaemonStatus(Optional<String> daemonStatus);

    public abstract Builder setReleaseStatus(Optional<String> releaseStatus);

    public abstract Builder setReleaseType(Optional<String> releaseType);

    public abstract Builder setLabServerVersion(Optional<String> labServerVersion);

    public abstract DeviceRecord build();
  }
}
