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
import java.util.Optional;

/**
 * One host's indexed fields in the forward store.
 *
 * <p>Used by the host-entity search index. Fields come from two data sources: LabInfoService
 * (always available) and HostInfoService (available in the multi-controller internal build only).
 * Fields from HostInfoService are Optional and empty when that source is unavailable.
 */
@AutoValue
public abstract class HostRecord {

  /** Host name. Primary identifier. */
  public abstract String hostName();

  /** Host IP from LabLocator. */
  public abstract String hostIp();

  /** Host connectivity status (e.g. "LAB_RUNNING", "LAB_MISSING"). From LabInfo.lab_status. */
  public abstract String labStatus();

  /**
   * Host operating system. Sourced from the {@code host_os} host property, defaulting to "Unknown"
   * when the property is absent, matching the host detail page.
   */
  public abstract String hostOs();

  /**
   * Host lab server connectivity, bucketed from the lab status the same way as the host detail page
   * (for example "Running", "Missing").
   */
  public abstract String hostConnectivity();

  /** Host properties from LabInfo (key-value pairs). */
  public abstract ImmutableMap<String, String> hostProperties();

  /** Number of devices on this host. Computed from LabInfo device list. */
  public abstract int deviceCount();

  /**
   * User-facing lab types (multi-valued, e.g. ["Core", "Satellite"]). Derived from host properties
   * and HostInfoService lab type enum, combined by the same logic as HostTypes.determineUiLabTypes.
   */
  public abstract ImmutableList<String> labTypes();

  // --- Fields from HostInfoService (absent in OSS / ats-all) ---

  /** Lab server release status (e.g. "RUNNING", "DRAINING"). From HostInfoService. */
  public abstract Optional<String> releaseStatus();

  /** Host release type (raw LabType enum name). From HostInfoService. */
  public abstract Optional<String> releaseType();

  /** Daemon server status (e.g. "RUNNING", "MISSING"). From HostInfoService. */
  public abstract Optional<String> daemonStatus();

  /** Lab server version string. From host_properties "host_version" or HostInfoService. */
  public abstract Optional<String> labServerVersion();

  /**
   * The ATS controller this host belongs to. Present only in the ats-all deployment. Absent in
   * ats-one and 1p.
   */
  public abstract Optional<String> atsController();

  /** Creates a new builder. */
  public static Builder builder() {
    return new AutoValue_HostRecord.Builder().setAtsController(Optional.empty());
  }

  /** Builder for {@link HostRecord}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setHostName(String hostName);

    public abstract Builder setHostIp(String hostIp);

    public abstract Builder setLabStatus(String labStatus);

    public abstract Builder setHostOs(String hostOs);

    public abstract Builder setHostConnectivity(String hostConnectivity);

    public abstract Builder setHostProperties(ImmutableMap<String, String> hostProperties);

    public abstract Builder setDeviceCount(int deviceCount);

    public abstract Builder setLabTypes(ImmutableList<String> labTypes);

    public abstract Builder setReleaseStatus(Optional<String> releaseStatus);

    public abstract Builder setReleaseType(Optional<String> releaseType);

    public abstract Builder setDaemonStatus(Optional<String> daemonStatus);

    public abstract Builder setLabServerVersion(Optional<String> labServerVersion);

    public abstract Builder setAtsController(Optional<String> atsController);

    public abstract HostRecord build();
  }
}
