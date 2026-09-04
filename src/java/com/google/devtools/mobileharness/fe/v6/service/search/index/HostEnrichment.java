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
import java.util.Optional;

/**
 * Per-host enrichment that a data source layers on top of the LabInfo-derived host record and its
 * devices.
 *
 * <p>This is a deployment-neutral data type: it carries no proto and no case-specific dependency,
 * so the shared {@link FleetIndexBuilder} can consume it in every deployment. Each data source maps
 * its own backend (for example a HostInfoService proto) into this shape and leaves absent whatever
 * it cannot supply.
 */
@AutoValue
public abstract class HostEnrichment {

  /** Lab server release status (for example "RUNNING", "DRAINING"). */
  public abstract Optional<String> releaseStatus();

  /** Host release type (raw LabType enum name). */
  public abstract Optional<String> releaseType();

  /** Daemon server status (for example "RUNNING", "MISSING"). */
  public abstract Optional<String> daemonStatus();

  /** Daemon server version string. */
  public abstract Optional<String> daemonServerVersion();

  /** Lab server version string. */
  public abstract Optional<String> labServerVersion();

  /** The human-readable ATS lab display name (e.g. "Pixel Lab MTV"). */
  public abstract Optional<String> atsLabDisplayName();

  /** The raw controller endpoint identifier (e.g. "ats-ctrl-us-central1-b-04"). */
  public abstract Optional<String> atsControllerId();

  /** Creates a new builder. */
  public static Builder builder() {
    return new AutoValue_HostEnrichment.Builder()
        .setReleaseStatus(Optional.empty())
        .setReleaseType(Optional.empty())
        .setDaemonStatus(Optional.empty())
        .setDaemonServerVersion(Optional.empty())
        .setLabServerVersion(Optional.empty())
        .setAtsLabDisplayName(Optional.empty())
        .setAtsControllerId(Optional.empty());
  }

  /** Builder for {@link HostEnrichment}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setReleaseStatus(Optional<String> releaseStatus);

    public abstract Builder setReleaseType(Optional<String> releaseType);

    public abstract Builder setDaemonStatus(Optional<String> daemonStatus);

    public abstract Builder setDaemonServerVersion(Optional<String> daemonServerVersion);

    public abstract Builder setLabServerVersion(Optional<String> labServerVersion);

    public abstract Builder setAtsLabDisplayName(Optional<String> atsLabDisplayName);

    public abstract Builder setAtsControllerId(Optional<String> atsControllerId);

    public abstract HostEnrichment build();
  }
}
